package com.quantlab.signal.strategy;

import com.quantlab.common.entity.Signal;
import com.quantlab.common.entity.SignalAdditions;
import com.quantlab.common.entity.Strategy;
import com.quantlab.common.repository.SignalAdditionsRepository;
import com.quantlab.common.repository.SignalRepository;
import com.quantlab.common.repository.StrategyRepository;
import com.quantlab.common.utils.staticstore.dropdownutils.*;
import com.quantlab.signal.dto.JodiAdjustmentResult;
import com.quantlab.signal.dto.SignalMapperDto;
import com.quantlab.signal.dto.redisDto.MarketData;
import com.quantlab.signal.dto.redisDto.MasterResponseFO;
import com.quantlab.signal.service.CugUsersService;
import com.quantlab.signal.service.GrpcService;
import com.quantlab.signal.service.StrategyService;
import com.quantlab.signal.service.redisService.TouchLineService;
import com.quantlab.signal.utils.CommonUtils;
import com.quantlab.signal.web.dto.MarketLiveDto;
import com.quantlab.signal.web.service.MarketDataFetch;
import org.hibernate.Hibernate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Service("JodiArbitrageV2Strategy")
public class JodiStrategyV2 implements StrategiesImplementation<JodiStrategyV2>{

    private static final Logger logger = LoggerFactory.getLogger(JodiStrategyV2.class);

    @Autowired
    MarketDataFetch marketDataFetch;

    @Autowired
    private CommonUtils commonUtils;

    @Autowired
    private TouchLineService touchLineService;

    @Autowired
    private SignalService signalService;

    @Autowired
    CugUsersService cugUsersService;

    @Autowired
    private GrpcService grpcService;

    @Autowired
    private StrategyService strategyService;

    @Autowired
    private StrategyRepository strategyRepository;

    @Autowired
    private SignalRepository signalRepository;

    @Autowired
    private SignalAdditionsRepository signalAdditionsRepository;


    @Override
    public Signal runStrategy(Strategy strategy) {
        Hibernate.initialize(strategy.getUnderlying());
        String underlying = strategy.getUnderlying().getName();
        try {

            MarketLiveDto marketLive = marketDataFetch.getMarketData(strategy);
            double spot = marketLive.getSpotPrice();

            String expiryOption = commonUtils.getExpiryShotDateByIndex(strategy.getExpiry(), strategy.getUnderlying().getName(), OptionType.OPTION.getKey());
            String expiryFuture = commonUtils.getExpiryShotDateByIndex(strategy.getExpiry(), strategy.getUnderlying().getName(), OptionType.FUTURE.getKey());

            // 1) Compute Spot ATM strike
            int spotAtmStrike = marketDataFetch.getATM(underlying, spot, expiryOption);

            String spotKeyCE = underlying.toUpperCase(Locale.ROOT) + expiryOption + "-" + spotAtmStrike + "CE";
            String spotKeyPE = underlying.toUpperCase(Locale.ROOT) + expiryOption + "-" + spotAtmStrike + "PE";
            MasterResponseFO spotMasterCE = marketDataFetch.getMasterResponse(spotKeyCE);
            MasterResponseFO spotMasterPE = marketDataFetch.getMasterResponse(spotKeyPE);

            String futureKey = underlying.toUpperCase(Locale.ROOT) + expiryFuture + OptionType.FUTURE.getKey();
            MasterResponseFO futureMaster = marketDataFetch.getMasterResponse(futureKey);
            MarketData futureData = touchLineService.getTouchLine(String.valueOf(futureMaster.getExchangeInstrumentID()));
            double futurePrice = futureData.getLTP();

            int futureAtmStrike = marketDataFetch.getATM(underlying, futurePrice , expiryOption);

            // 3) Jodi for Spot ATM
            double spotCallLtp = touchLineService.getTouchLine(String.valueOf(spotMasterCE.getExchangeInstrumentID())).getLTP();
            double spotPutLtp = touchLineService.getTouchLine(String.valueOf(spotMasterPE.getExchangeInstrumentID())).getLTP();
            double spotJodi = spotCallLtp + spotPutLtp;

            String futureKeyCE = underlying.toUpperCase(Locale.ROOT) + expiryOption + "-" + futureAtmStrike + "CE";
            String futureKeyPE = underlying.toUpperCase(Locale.ROOT) + expiryOption + "-" + futureAtmStrike + "PE";
            MasterResponseFO futureMasterCE = marketDataFetch.getMasterResponse(futureKeyCE);
            MasterResponseFO futureMasterPE = marketDataFetch.getMasterResponse(futureKeyPE);

            // 4) Jodi for Future ATM
            double futureCallLtp = touchLineService.getTouchLine(String.valueOf(futureMasterCE.getExchangeInstrumentID())).getLTP();
            double futurePutLtp = touchLineService.getTouchLine(String.valueOf(futureMasterPE.getExchangeInstrumentID())).getLTP();
            double futureJodi = futureCallLtp + futurePutLtp;

            // 5) Select the strike where jodi (premium) is lower
            boolean useSpot = spotJodi <= futureJodi;
            String selectedExpiry = expiryOption;
            int selectedStrike = useSpot ? spotAtmStrike : futureAtmStrike;
            double selectedJodi   = useSpot ? spotJodi : futureJodi;

            List<SignalMapperDto> legs = new ArrayList<>();

            {
                String key = underlying.toUpperCase(Locale.ROOT) + selectedExpiry + "-" + selectedStrike + "CE";
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


            {

                String key = underlying.toUpperCase(Locale.ROOT) + selectedExpiry + "-" + selectedStrike + "PE";
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

            Signal signal = signalService.createSignal(strategy, legs);
            strategyRepository.updateSignalCount(strategy.getId());

            signal = signalRepository.findById(signal.getId()).orElseThrow();

            SignalAdditions additions = signal.getSignalAdditions();
            if (additions == null) {
                additions = new SignalAdditions();
                signal.setSignalAdditions(additions);
            }
            additions.setJodiEntryStrike(selectedStrike);
            additions.setJodiEntryPremium(selectedJodi);
            additions.setJodiEntryExpiry(selectedExpiry);

            signalRepository.save(signal);

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

    /**
     * Adjust strategy to a new strike - square off old position and enter new one
     */
    public Signal adjustStrategy(Strategy strategy, int newStrike, String expiry, double newPremium) {
        String underlying = strategy.getUnderlying().getName();
        try {
            logger.info("Adjusting Jodi strategy {} from old position to new strike: {}",
                    strategy.getId(), newStrike);

            // Get market data
            MarketLiveDto marketLive = marketDataFetch.getMarketData(strategy);

            List<SignalMapperDto> legs = new ArrayList<>();

            // Create CE leg
            {
                String key = underlying.toUpperCase(Locale.ROOT) + expiry + "-" + newStrike + "CE";
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

            // Create PE leg
            {
                String key = underlying.toUpperCase(Locale.ROOT) + expiry + "-" + newStrike + "PE";
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

            // Create new signal for adjusted position
            Signal signal = signalService.createSignal(strategy, legs);
            strategyRepository.updateSignalCount(strategy.getId());
            signal = signalRepository.findById(signal.getId()).orElseThrow();

            // Update signal additions with new strike and premium
            SignalAdditions additions = signal.getSignalAdditions();
            if (additions == null) {
                additions = new SignalAdditions();
                signal.setSignalAdditions(additions);
            }
            additions.setJodiEntryStrike(newStrike);
            additions.setJodiEntryPremium(newPremium);
            additions.setJodiEntryExpiry(expiry);

            signalRepository.save(signal);

            if (!strategy.getExecutionType().equalsIgnoreCase(ExecutionTypeMenu.PAPER_TRADING.getKey())
                    && (cugUsersService.isUserInCug(signal.getAppUser().getTenentId()))) {
                grpcService.sendSignal(signal);
            }

            logger.info("Successfully adjusted Jodi strategy {} to strike {}", strategy.getId(), newStrike);
            return signal;

        } catch (Exception e) {
            logger.error("Error adjusting Jodi strategy {}: {}", strategy.getId(), e.getMessage(), e);
            throw new RuntimeException(e);
        }
    }

    @Override
    public void exitStrategy(Strategy strategy) {
        try {
            Signal exit = signalService.createExit(strategy);
            if (exit != null && !ExecutionTypeMenu.PAPER_TRADING.getKey()
                    .equalsIgnoreCase(strategy.getExecutionType())) {
                grpcService.sendExitSignal(exit);
            }
            logger.info("Jodi strategy exited: {}", strategy.getId());
        } catch (Exception ex) {
            logger.error("Error exiting Jodi strategy: {}", strategy.getId(), ex);
            throw new RuntimeException(ex);
        }
    }

    @Override
    public void check(Strategy strategy) {
        if (strategyService.inHouseEntryCheck(strategy)) {
            logger.info("InHouse Entry Check Passed for strategy: {}", strategy.getId());
            this.runStrategy(strategy);
        } else if (strategy.getStatus().equalsIgnoreCase(Status.LIVE.getKey())) {
            Long signalId = signalRepository.findLatestSignalId(strategy.getId(), Status.LIVE.getKey());
            if (signalId != null) {
                // Check if it's time for final exit (2:55 PM)
                if (strategyService.shouldExit(strategy)) {
                    logger.info("Final exit time reached for strategy {}", strategy.getId());
                    this.exitStrategy(strategy);
                    return;
                }

                // Check for adjustment opportunity
                JodiAdjustmentResult result = strategyService.checkJodiAdjustment(strategy);
                if (result.shouldAdjust()) {
                    logger.info("Adjustment triggered for strategy {}: moving to strike {}",
                            strategy.getId(), result.getNewStrike());

                    // Exit old position
                    this.exitStrategy(strategy);

                    // Enter new position at adjusted strike
                    this.adjustStrategy(strategy, result.getNewStrike(),
                            result.getExpiry(), result.getNewPremium());
                }
            }
        }
    }
}

