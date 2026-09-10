package com.quantlab.signal.strategy;

import com.quantlab.common.entity.Signal;
import com.quantlab.common.entity.Strategy;
import com.quantlab.common.repository.SignalRepository;
import com.quantlab.common.repository.StrategyRepository;
import com.quantlab.common.utils.staticstore.dropdownutils.*;
import com.quantlab.signal.dto.SignalMapperDto;
import com.quantlab.signal.dto.redisDto.MarketData;
import com.quantlab.signal.dto.redisDto.MasterResponseFO;
import com.quantlab.signal.service.CugUsersService;
import com.quantlab.signal.service.GrpcService;
import com.quantlab.signal.service.StrategyService;
import com.quantlab.signal.service.redisService.TouchLineService;
import com.quantlab.signal.utils.BalanceCalculator;
import com.quantlab.signal.utils.CommonUtils;
import com.quantlab.signal.web.dto.MarketLiveDto;
import com.quantlab.signal.web.service.MarketDataFetch;
import org.hibernate.Hibernate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

@Service("Phoenix")
public class PhoenixStrategy implements StrategiesImplementation<PhoenixStrategy> {

    private static final Logger logger = LoggerFactory.getLogger(PhoenixStrategy.class);

    @Autowired
    CommonUtils commonUtils;

    @Autowired
    MarketDataFetch marketDataFetch;

    @Autowired
    private GrpcService grpcService;

    @Autowired
    private TouchLineService touchLineService;

    @Autowired
    private SignalService signalService;

    @Autowired
    private StrategyService strategyService;

    @Autowired
    private StrategyRepository strategyRepository;

    @Autowired
    private SignalRepository signalRepository;

    @Autowired
    CugUsersService cugUsersService;

    @Override
    public Signal runStrategy(Strategy strategy) {
        LocalDate now = LocalDate.now();
        Hibernate.initialize(strategy.getUnderlying());
        String underlying = strategy.getUnderlying().getName();

        try {
            double price;
            // 1. Fetch live market data
            MarketLiveDto marketLive = marketDataFetch.getMarketData(strategy);

            // 2. Compute synthetic price if required, else use spot
            if (AtmType.SYNTHETIC_ATM.getKey().equalsIgnoreCase(strategy.getAtmType())) {
                price = marketDataFetch.getSyntheticPrice(strategy, underlying);
            } else {
                price = marketLive.getSpotPrice();
            }
            if (strategy.getName().contains("ATM")) {
                price = Math.round(price / 100.0) * 100.0;
            }
            // 3. Recalculate ATM strike and expiry
            int aTM = marketDataFetch.getATM(
                    underlying,
                    (int) price,
                    commonUtils.getExpiryShotDateByIndex(strategy.getExpiry(), underlying, OptionType.OPTION.getKey())
            );
            String expiryDate = commonUtils.getExpiryShotDateByIndex(strategy.getExpiry(), underlying, OptionType.OPTION.getKey());

            // 4. Build only 2 legs: ATM CE and ATM PE
            List<SignalMapperDto> legs = new ArrayList<>(2);

            // ATM CE
            {
                int strike = aTM;
                String key = underlying.toUpperCase(Locale.ROOT) + expiryDate + "-" + strike + "CE";
                MasterResponseFO master = marketDataFetch.getMasterResponse(key);
                MarketData data = touchLineService.getTouchLine(String.valueOf(master.getExchangeInstrumentID()));

                SignalMapperDto ce = new SignalMapperDto();
                ce.setMarketLiveDto(marketLive);
                ce.setTouchlineBinaryResponse(data);
                ce.setLegName(key);
                ce.setMasterData(master);
                ce.setBuySellFlag(LegSide.SELL.getKey());
                ce.setSegment("NSEFO");
                ce.setCategory(LegType.CALL.getKey());
                ce.setPositionType(strategy.getPositionType());
                ce.setLegType(LegType.OPEN.getKey());
                ce.setDerivativeType(OptionType.OPTION.getKey());
                ce.setLots(1L);
                ce.setQuantity((int) (master.getLotSize() * strategy.getMultiplier()));

                legs.add(ce);
            }

            // ATM PE
            {
                int strike = aTM;
                String key = underlying.toUpperCase(Locale.ROOT) + expiryDate + "-" + strike + "PE";
                MasterResponseFO master = marketDataFetch.getMasterResponse(key);
                MarketData data = touchLineService.getTouchLine(String.valueOf(master.getExchangeInstrumentID()));

                SignalMapperDto pe = new SignalMapperDto();
                pe.setMarketLiveDto(marketLive);
                pe.setTouchlineBinaryResponse(data);
                pe.setLegName(key);
                pe.setMasterData(master);
                pe.setBuySellFlag(LegSide.SELL.getKey());
                pe.setSegment("NSEFO");
                pe.setCategory(LegType.PUT.getKey());
                pe.setPositionType(strategy.getPositionType());
                pe.setLegType(LegType.OPEN.getKey());
                pe.setDerivativeType(OptionType.OPTION.getKey());
                pe.setLots(1L);
                pe.setQuantity((int) (master.getLotSize() * strategy.getMultiplier()));

                legs.add(pe);
            }

            // 5. Create signal and optionally send via gRPC
            Signal signal = signalService.createSignal(strategy, legs);
            if (!strategy.getExecutionType().equalsIgnoreCase(ExecutionTypeMenu.PAPER_TRADING.getKey())
                    && (cugUsersService.isUserInCug(signal.getAppUser().getTenentId()))) {
                grpcService.sendSignal(signal);
            }

            return signal;

        } catch (Exception e) {
            signalService.errorCreatingSignal(strategy, e);
            throw new RuntimeException(e);
        }
    }

    @Override
    public void exitStrategy(Strategy strategy) {
        try {
            logger.info("Exiting strategy: {}", strategy.getId());

            Signal newSignal = signalService.createExit(strategy);
            if (newSignal != null && !strategy.getExecutionType().equalsIgnoreCase(ExecutionTypeMenu.PAPER_TRADING.getKey())
                    && (cugUsersService.isUserInCug(newSignal.getAppUser().getTenentId()))){
                grpcService.sendExitSignal(newSignal);
                logger.info("Exit signal sent for strategy: {}", strategy.getId());
            } else {
                logger.info("Exit skipped for strategy: {} Paper Trading or No Signal Created", strategy.getId());

            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void check(Strategy strategy) {
        if (strategyService.inHouseEntryCheck(strategy)) {
            logger.info("InHouse Entry Check Passed for strategy: {}", strategy.getId());
            this.runStrategy(strategy);
        }
        else if (strategy.getStatus().equalsIgnoreCase(Status.LIVE.getKey())) {
            Long hasLiveId = signalRepository.findLatestSignalId(strategy.getId(), Status.LIVE.getKey());
            if (hasLiveId != null) {
                boolean check = strategyService.phoenixExit(strategy, hasLiveId);
                if (check) {
                    this.exitStrategy(strategy);
                }
            }
        }
    }
}
