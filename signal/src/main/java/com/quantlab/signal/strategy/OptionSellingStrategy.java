package com.quantlab.signal.strategy;

import com.quantlab.common.entity.Signal;
import com.quantlab.common.entity.Strategy;
import com.quantlab.common.repository.SignalRepository;
import com.quantlab.common.repository.StrategyRepository;
import com.quantlab.common.utils.staticstore.dropdownutils.*;
import com.quantlab.signal.dto.HedgeData;
import com.quantlab.signal.dto.SignalMapperDto;
import com.quantlab.signal.dto.redisDto.MarketData;
import com.quantlab.signal.dto.redisDto.MasterResponseFO;
import com.quantlab.signal.service.CugUsersService;
import com.quantlab.signal.service.GrpcService;
import com.quantlab.signal.service.StrategyService;
import com.quantlab.signal.service.redisService.SyntheticPriceRepository;
import com.quantlab.signal.service.redisService.TouchLineService;
import com.quantlab.signal.utils.BalanceCalculator;
import com.quantlab.signal.utils.CommonUtils;
import com.quantlab.signal.utils.StrategyUtils;
import com.quantlab.signal.web.dto.MarketLiveDto;
import com.quantlab.signal.web.service.MarketDataFetch;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

import static com.quantlab.common.utils.staticstore.AppConstants.TOGGLE_TRUE;

@Service("OptionSellingStrategy")
public class OptionSellingStrategy implements StrategiesImplementation<OptionSellingStrategy> {

    private static final Logger logger = LoggerFactory.getLogger(OptionSellingStrategy.class);

    private static final double PREMIUM_PCT = 0.002;                   // 0.2% -> 0.002
    private static final double HEDGE_PREMIUM_NIFTY = 2.0;                   // Rs.2 hedge
    private static final double HEDGE_PREMIUM_SENSEX = 5.0;
    private static final double STOPLOSS_PERCENT = 20.0;               // 20% SL on each leg
    private static final int STRIKE_STEP_FALLBACK = 50;                // fallback strike step

    @Autowired
    private MarketDataFetch marketDataFetch;
    @Autowired private SignalService signalService;
    @Autowired private StrategyRepository strategyRepository;
    @Autowired private CommonUtils commonUtils;
    @Autowired private GrpcService grpcService;
    @Autowired private CugUsersService cugUsersService;
    @Autowired private BalanceCalculator balanceCalculator;
    @Autowired private SignalRepository signalRepository;
    @Autowired private TouchLineService touchLineService;
    @Autowired private RedisTemplate<String, Object> redisTemplate;
    @Autowired private SyntheticPriceRepository syntheticPriceRepository;
    @Autowired private StrategyService strategyService;
    @Autowired private StrategyUtils strategyUtils;

    @Override
    public Signal runStrategy(Strategy strategy) {
        try {
            MarketLiveDto marketLive = marketDataFetch.getMarketData(strategy);
            String underlying = strategy.getUnderlying().getName().toUpperCase(Locale.ROOT);
            String expiryShot = commonUtils.getExpiryShotDateByIndex(strategy.getExpiry(), underlying, OptionType.OPTION.getKey());
            double spot = marketLive.getSpotPrice();

            // ATM strike (prefer synthetic if available)
            int atm = marketDataFetch.getATM(underlying, spot, expiryShot);

            // Short premium target: 0.2% of spot/ATM
            double shortTargetPremium = Math.round((spot * PREMIUM_PCT) * 100.0) / 100.0;

            // Find short legs (CE + PE) closest to 0.2% premium
            HedgeData shortCe = strategyUtils.getOptionStrikeByPremium(shortTargetPremium, atm, SegmentType.CE.getKey(), strategy);
            HedgeData shortPe = strategyUtils.getOptionStrikeByPremium(shortTargetPremium, atm, SegmentType.PE.getKey(), strategy);

            HedgeData hedgeCe = null;
            HedgeData hedgePe = null;
            if (strategy.getUnderlying().getId() == 1) {
                // Find hedge legs (CE + PE) closest to Rs.2 premium for NIFTY
                hedgeCe = strategyUtils.getOptionStrikeByPremium(HEDGE_PREMIUM_NIFTY, atm, SegmentType.CE.getKey(), strategy);
                hedgePe = strategyUtils.getOptionStrikeByPremium(HEDGE_PREMIUM_NIFTY, atm, SegmentType.PE.getKey(), strategy);
            } else if (strategy.getUnderlying().getId() == 4) {
                // Find hedge legs (CE + PE) closest to Rs.5 premium for SENSEX
                hedgeCe = strategyUtils.getOptionStrikeByPremium(HEDGE_PREMIUM_SENSEX, atm, SegmentType.CE.getKey(), strategy);
                hedgePe = strategyUtils.getOptionStrikeByPremium(HEDGE_PREMIUM_SENSEX, atm, SegmentType.PE.getKey(), strategy);
            }

            if (shortCe == null || shortPe == null || hedgeCe == null || hedgePe == null) {
                logger.warn("One or more legs not found for OptionSelling strategy id={} atm={} shortTarget={} hedgeTarget={}",
                        strategy.getId(), atm, shortTargetPremium, HEDGE_PREMIUM_NIFTY);
                signalService.errorCreatingSignal(strategy, new IllegalStateException("Required leg(s) not found"));
                return null;
            }

            // Get master responses
            MasterResponseFO shortCeMaster = shortCe.getMasterData();
            MasterResponseFO shortPeMaster = shortPe.getMasterData();
            MasterResponseFO hedgeCeMaster = hedgeCe.getMasterData();
            MasterResponseFO hedgePeMaster = hedgePe.getMasterData();

            // Build SignalMapperDto list: sell CE, sell PE, buy CE(hedge), buy PE(hedge)
            List<SignalMapperDto> mapperDtos = new ArrayList<>();

            mapperDtos.add(buildMapper(strategy, marketLive, shortCe.getFinalKey(), shortCeMaster,
                    LegSide.SELL.getKey(), SegmentType.CE.getKey(), LegType.CALL.getKey(), 1, STOPLOSS_PERCENT));

            mapperDtos.add(buildMapper(strategy, marketLive, shortPe.getFinalKey(), shortPeMaster,
                    LegSide.SELL.getKey(), SegmentType.PE.getKey(), LegType.PUT.getKey(), 1, STOPLOSS_PERCENT));

            mapperDtos.add(buildMapper(strategy, marketLive, hedgeCe.getFinalKey(), hedgeCeMaster,
                    LegSide.BUY.getKey(), SegmentType.CE.getKey(), LegType.CALL.getKey(), 1, STOPLOSS_PERCENT));

            mapperDtos.add(buildMapper(strategy, marketLive, hedgePe.getFinalKey(), hedgePeMaster,
                    LegSide.BUY.getKey(), SegmentType.PE.getKey(), LegType.PUT.getKey(), 1, STOPLOSS_PERCENT));

            Signal signal = signalService.createSignal(strategy, mapperDtos);
            strategyRepository.updateSignalCount(strategy.getId());

            if (!strategy.getExecutionType().equalsIgnoreCase(ExecutionTypeMenu.PAPER_TRADING.getKey())
                    && cugUsersService.isUserInCug(signal.getAppUser().getTenentId())) {
                grpcService.sendSignal(signal);
            }

            logger.info("OptionSelling signal created for strategy id={} signalId={}", strategy.getId(), signal.getId());
            return signal;

        } catch (Exception e) {
            logger.error("Error creating OptionSelling signal for strategy id={} msg={}", strategy.getId(), e.getMessage(), e);
            signalService.errorCreatingSignal(strategy, e);
            throw new RuntimeException(e);
        }
    }

    @Override
    public void exitStrategy(Strategy strategy) {
        try {
            logger.info("Exiting OptionSelling strategy: {}", strategy.getId());
            Signal exitSignal = signalService.createExit(strategy);
            if (exitSignal != null
                    && !strategy.getExecutionType().equalsIgnoreCase(ExecutionTypeMenu.PAPER_TRADING.getKey())
                    && cugUsersService.isUserInCug(exitSignal.getAppUser().getTenentId())) {
                grpcService.sendExitSignal(exitSignal);
                logger.info("Exit signal sent for strategy: {}", strategy.getId());
            } else {
                logger.info("Exit skipped for strategy: {} (paper trading or no exit signal)", strategy.getId());
            }
        } catch (Exception e) {
            logger.error("Error while exiting OptionSelling strategy id={}", strategy.getId(), e);
            throw new RuntimeException(e);
        }
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW, isolation = Isolation.READ_COMMITTED)
    public void check(Strategy strategy) {
        if (strategyService.inHouseEntryCheck(strategy)) {
            logger.info("InHouse Entry Check Passed for OptionSelling strategy: {}", strategy.getId());
            this.runStrategy(strategy);
        }
        else if (strategy.getStatus().equalsIgnoreCase(Status.LIVE.getKey())) {
            Long topSignalId = signalRepository.findTopIdByStrategyIdAndStatusOrderByCreatedAtDesc(strategy.getId(), SignalStatus.LIVE.getKey());
            if (topSignalId != null) {
                boolean check = strategyService.optionSellingExit(strategy, topSignalId);
                if (check) {
                    this.exitStrategy(strategy);
                }
            }
        }
    }


    // Helper methods

    private SignalMapperDto buildMapper(Strategy strategy,
                                        MarketLiveDto marketLive,
                                        String legName,
                                        MasterResponseFO master,
                                        String buySellFlag,
                                        String optionType,
                                        String legCategory,
                                        long lots,
                                        double stoplossPercent) {
        SignalMapperDto dto = new SignalMapperDto();
        dto.setMarketLiveDto(marketLive);
        MarketData touchLine = touchLineService.getTouchLine(String.valueOf(master.getExchangeInstrumentID()));
        dto.setTouchlineBinaryResponse(touchLine);
        dto.setMasterData(master);
        dto.setLegName(legName);
        dto.setBuySellFlag(buySellFlag);
        dto.setSegment("NSEFO");
        dto.setCategory(legCategory);
        dto.setOptionType(optionType);
        dto.setPositionType(strategy.getPositionType());
        dto.setLots(lots);
        dto.setLegType(LegType.OPEN.getKey());
        dto.setDerivativeType(OptionType.OPTION.getKey());
        dto.setQuantity((int) (lots * master.getLotSize() * strategy.getMultiplier()));

        dto.setStopLossUnitToggle(TOGGLE_TRUE);
        dto.setStopLossUnitType("PERCENT");
        dto.setStopLossUnitValue((long) Math.round(stoplossPercent));

        return dto;
    }

}

