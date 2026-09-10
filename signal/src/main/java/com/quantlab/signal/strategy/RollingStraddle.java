package com.quantlab.signal.strategy;
import com.quantlab.common.dao.StrategyStatusResignalDao;
import com.quantlab.common.entity.Signal;
import com.quantlab.common.entity.Strategy;
import com.quantlab.common.repository.SignalRepository;
import com.quantlab.common.repository.StrategyRepository;
import com.quantlab.common.utils.staticstore.IndexDifference;
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
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

import static com.quantlab.common.utils.staticstore.AppConstants.*;


@Service("RollingStraddle")
public class RollingStraddle implements StrategiesImplementation<RollingStraddle> {

    private static final Logger logger = LoggerFactory.getLogger(RollingStraddle.class);

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
    private BalanceCalculator balanceCalculator;

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
        IndexDifference difference = IndexDifference.fromKey(underlying);

        int strikeInterval = getStrikeInterval(underlying);

        try {
            double price;
            // 1. Fetch live market data
            MarketLiveDto marketLive = marketDataFetch.getMarketData(strategy);
            // 2. Compute synthetic price
            if (AtmType.SYNTHETIC_ATM.getKey().equalsIgnoreCase(strategy.getAtmType()))
                price = marketDataFetch.getSyntheticPrice(strategy, strategy.getUnderlying().getName());
            else
                price = marketLive.getSpotPrice();
            // 3. Recalculate ATM strike using synthetic price
            int aTM = marketDataFetch.getATM(strategy.getUnderlying().getName(), (int) price , commonUtils.getExpiryShotDateByIndex(strategy.getExpiry(), strategy.getUnderlying().getName(), OptionType.OPTION.getKey()));
            String expiryDate = commonUtils.getExpiryShotDateByIndex(strategy.getExpiry(), strategy.getUnderlying().getName(), OptionType.OPTION.getKey());

            List<SignalMapperDto> legs = List.of(
                    Map.entry(0, "CE"),    // ATM CE
                    Map.entry(0, "PE"),    // ATM PE
                    Map.entry(-1, "CE"),   // ITM1 CE
                    Map.entry(-1, "PE"),   // OTM1 PE
                    Map.entry(-2, "CE"),   // ITM2 CE
                    Map.entry(-2, "PE"),   // OTM2 PE
                    Map.entry(1, "CE"),     // OTM1 CE
                    Map.entry(1, "PE"),     // ITM1 PE
                    Map.entry(2, "CE"),    // OTM2 CE
                    Map.entry(2, "PE")     // ITM2 PE
            ).stream().map(entry -> {
                int strikeOffset = entry.getKey();
                String optionType = entry.getValue();
                int strike = aTM + (strikeOffset * strikeInterval);
                String key = strategy.getUnderlying().getName().toUpperCase(Locale.ROOT) + expiryDate + "-" + strike + optionType;
                MasterResponseFO master = marketDataFetch.getMasterResponse(key);
                MarketData data = touchLineService.getTouchLine(String.valueOf(master.getExchangeInstrumentID()));
                SignalMapperDto leg = new SignalMapperDto();
                leg.setMarketLiveDto(marketLive);
                leg.setTouchlineBinaryResponse(data);
                leg.setLegName(key);
                leg.setMasterData(master);
                leg.setBuySellFlag(LegSide.SELL.getKey());
                leg.setSegment("NSEFO");
                leg.setCategory(entry.getValue().equals("CE") ? LegType.CALL.getKey() : LegType.PUT.getKey());
                leg.setPositionType(strategy.getPositionType());
                leg.setLegType(LegType.OPEN.getKey());
                leg.setDerivativeType(OptionType.OPTION.getKey());
                String lotsKey = entry.getValue().equals("CE") ? "call" : "put";
                leg.setLots(1L);
                leg.setQuantity((int) (master.getLotSize() * strategy.getMultiplier()));

                return leg;
            }).toList();

            Signal signal = signalService.createSignal(strategy, legs);
            if (!strategy.getExecutionType().equalsIgnoreCase(ExecutionTypeMenu.PAPER_TRADING.getKey())
                    && (cugUsersService.isUserInCug(signal.getAppUser().getTenentId()))){
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
        boolean entry = strategyService.inHouseEntryCheck(strategy);
        if (entry) {
            Optional<StrategyStatusResignalDao> newStrategy = strategyRepository.findStatusAndCountsById(strategy.getId());
            if (newStrategy.isPresent() && strategy.getStatus().equalsIgnoreCase(Status.ACTIVE.getKey())) {
                if ((newStrategy.get().getReSignalCount() > newStrategy.get().getSignalCount())) {
                    runStrategy(strategy);
                }
            }
        }
        else if (strategy.getStatus().equalsIgnoreCase(Status.LIVE.getKey())) {
            Long hasLiveId = signalRepository.findLatestSignalId(strategy.getId(), Status.LIVE.getKey());
            if (hasLiveId != null) {
                boolean check = strategyService.collarExit(strategy, hasLiveId);
                if (check) {
                    this.exitStrategy(strategy);
                }
            }
        }
    }
    public static int getStrikeInterval(String underlying) {
        if (underlying != null &&
                (underlying.toUpperCase().contains("SENSEX"))) {
            return 200;
        }
        return 100;
    }
}
