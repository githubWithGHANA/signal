package com.quantlab.signal.strategy;
import com.quantlab.common.entity.*;
import com.quantlab.common.repository.SignalRepository;
import com.quantlab.common.repository.StrategyRepository;
import com.quantlab.common.utils.staticstore.dropdownutils.*;
import com.quantlab.common.utils.staticstore.dropdownutils.Status;
import com.quantlab.signal.dto.EntryExitTimes;
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
import java.time.*;
import java.util.*;
import static com.quantlab.common.utils.staticstore.AppConstants.*;

@Service("SyntheticAtmStrategy")
public class SyntheticAtmStrategy implements StrategiesImplementation<SyntheticAtmStrategy> {

    private static final Logger logger = LoggerFactory.getLogger(SyntheticAtmStrategy.class);

    private static final double DELTA_BUFFER = 0.10;
    private static final int LOT_SIZE = 75;
    private static final int MAX_LOTS = 6;
    private static final double FAV_THRESHOLD = 50.0;
    private static final double UNFAV_THRESHOLD = 25.0;
//    private static final LocalTime FORCE_EXIT_TIME = LocalTime.of(15, 10);
//    private static final ZoneId IST_ZONE = ZoneId.of("Asia/Kolkata");

    @Autowired
    MarketDataFetch marketDataFetch;

    @Autowired
    CommonUtils commonUtils;

    @Autowired
    TouchLineService touchLineService;

    @Autowired
    SignalService signalService;

    @Autowired
    GrpcService grpcService;

    @Autowired
    StrategyService strategyService;

    @Autowired
    StrategyRepository strategyRepository;

    @Autowired
    SignalRepository signalRepository;

    @Autowired
    CugUsersService cugUsersService;

    private double entryPrice;
    private int[] currentQuantities;
    private int syntheticATM;


    @Override
    public Signal runStrategy(Strategy strategy) {
        LocalDate now = LocalDate.now();
        Hibernate.initialize(strategy.getUnderlying());
        Underlying underling = strategy.getUnderlying();
        String underlingName = underling.getName();

        try {
            MarketLiveDto marketLive = marketDataFetch.getMarketData(strategy);
            double syntheticPrice = marketDataFetch.getSyntheticPrice(strategy, strategy.getUnderlying().getName());
            //this.syntheticATM = marketDataFetch.getSpotATM(strategy.getUnderlying().getName(), (int) syntheticPrice );
            this.entryPrice = this.syntheticATM;
            String callKey = strategy.getUnderlying().getName().toUpperCase(Locale.ROOT) + commonUtils.getExpiryShotDateByIndex(strategy.getExpiry(), strategy.getUnderlying().getName(), OptionType.OPTION.getKey()) + "-" + syntheticATM + "CE";
            String putKey = strategy.getUnderlying().getName().toUpperCase(Locale.ROOT) + commonUtils.getExpiryShotDateByIndex(strategy.getExpiry(), strategy.getUnderlying().getName(), OptionType.OPTION.getKey()) + "-" + syntheticATM + "PE";
            MasterResponseFO callMaster = marketDataFetch.getMasterResponse(callKey);
            MasterResponseFO putMaster = marketDataFetch.getMasterResponse(putKey);
            MarketData callTouchlineResponse = touchLineService.getTouchLine(String.valueOf(callMaster.getExchangeInstrumentID()));
            MarketData putTouchlineResponse = touchLineService.getTouchLine(String.valueOf(putMaster.getExchangeInstrumentID()));

            // Calculate delta-neutral quantities
            this.currentQuantities = computeNeutralQuantities(
                    callTouchlineResponse.getDelta(),
                    putTouchlineResponse.getDelta(),
                    callMaster.getLotSize(),
                    strategy.getMultiplier()
            );

            List<SignalMapperDto> signalMapperDto = new ArrayList<>();

            // Call leg
            SignalMapperDto callLeg = new SignalMapperDto();
            callLeg.setMarketLiveDto(marketLive);
            callLeg.setTouchlineBinaryResponse(callTouchlineResponse);
            callLeg.setLegName(callKey);
            callLeg.setMasterData(callMaster);
            callLeg.setBuySellFlag(LegSide.SELL.getKey());
            callLeg.setSegment("NSEFO");
            callLeg.setCategory(LegType.CALL.getKey());
            callLeg.setPositionType(strategy.getPositionType());
            callLeg.setLots((long) currentQuantities[0]);
            callLeg.setLegType(LegType.OPEN.getKey());
            callLeg.setDerivativeType(OptionType.OPTION.getKey());
            callLeg.setQuantity((int) (currentQuantities[0] * callMaster.getLotSize() * strategy.getMultiplier()));


            // Put leg
            SignalMapperDto putLeg = new SignalMapperDto();
            putLeg.setMarketLiveDto(marketLive);
            putLeg.setTouchlineBinaryResponse(putTouchlineResponse);
            putLeg.setMasterData(putMaster);
            putLeg.setLegName(putKey);
            putLeg.setBuySellFlag(LegSide.SELL.getKey());
            putLeg.setSegment("NSEFO");
            putLeg.setCategory(LegType.PUT.getKey());
            putLeg.setPositionType(strategy.getPositionType());
            putLeg.setLots((long) currentQuantities[1]);
            putLeg.setLegType(LegType.OPEN.getKey());
            putLeg.setDerivativeType(OptionType.OPTION.getKey());
            putLeg.setQuantity((int) (currentQuantities[1] * putMaster.getLotSize() * strategy.getMultiplier()));

            signalMapperDto.add(callLeg);
            signalMapperDto.add(putLeg);

            Signal signals = signalService.createSignal(strategy, signalMapperDto);
            if (!strategy.getExecutionType().equalsIgnoreCase(ExecutionTypeMenu.PAPER_TRADING.getKey())
                    && (cugUsersService.isUserInCug(signals.getAppUser().getTenentId()))){
                grpcService.sendSignal(signals);
            }
            // 5. Start monitoring thread (original logic)
            new Thread(() -> monitorPositions(strategy)).start();

            return signals;
        } catch (Exception e) {
            signalService.errorCreatingSignal(strategy, e);
            throw new RuntimeException(e);
        }
    }

    private int[] computeNeutralQuantities(double callDelta, double putDelta, int lotSize, long multiplier) {
        long maxShares = (long) MAX_LOTS * lotSize;
        long callQty = lotSize;
        long putQty = lotSize;

        while ((callQty + putQty) <= maxShares) {
            double netDelta = callQty * callDelta - putQty * Math.abs(putDelta);
            if (Math.abs(netDelta) <= DELTA_BUFFER) {
                logger.debug("Delta neutrality achieved with netDelta = {}", netDelta);
                break;
            }
            if (netDelta > 0) {
                putQty += lotSize;
            } else {
                callQty += lotSize;
            }
        }
        callQty = Math.min(callQty * multiplier, maxShares);
        putQty = Math.min(putQty * multiplier, maxShares);

        return new int[]{(int) callQty, (int) putQty};
    }

    private void monitorPositions(Strategy strategy) {
        logger.info("Starting position monitoring for strategy {}", strategy.getId());

        while (!Thread.currentThread().isInterrupted()) {
            try {
                // 1. Check exit time
//                if (LocalTime.now(IST_ZONE).isAfter(FORCE_EXIT_TIME)) {
//                    logger.info("Forced exit at {}", FORCE_EXIT_TIME);
//                    exitStrategy(strategy);
//                    break;
//                }
                // 2. Check price movement
                MarketLiveDto marketLive = marketDataFetch.getMarketData(strategy);
                double currentPrice = syntheticATM;
                double move = currentPrice - entryPrice;

                if (Math.abs(move) >= getCurrentThreshold(strategy)) {
                    logger.info("Threshold hit ({} points), exiting", move);
                    exitStrategy(strategy);
                    runStrategy(strategy);  // Re-entry
                    continue;
                }

                Thread.sleep(50); // Original polling interval
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            } catch (Exception e) {
                logger.error("Monitoring error", e);
            }
        }
    }

    private double getCurrentThreshold(Strategy strategy) {
        if (currentQuantities == null) return FAV_THRESHOLD;
        return isFavourable(strategy) ? FAV_THRESHOLD : UNFAV_THRESHOLD;
    }

    private boolean isFavourable(Strategy strategy) {
        if (currentQuantities == null) return false;
        int callQty = currentQuantities[0];
        int putQty = currentQuantities[1];

        MarketLiveDto marketLive = marketDataFetch.getMarketData(strategy);
        double move = this.syntheticATM - entryPrice;

        if (callQty > putQty) return move < 0;
        if (putQty > callQty) return move > 0;
        return false;
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
        if (strategy.getStatus().equalsIgnoreCase(Status.ACTIVE.getKey())) {
            EntryExitTimes entryExitTimes = strategyService.entryExitTimes(strategy);
            Hibernate.initialize(strategy.getEntryDetails());
            EntryDetails entryDetails = strategy.getEntryDetails();

            ZoneId zoneId = ZoneId.systemDefault();
            Instant now = Instant.now();
            LocalDate today = LocalDate.now(zoneId);

            ZonedDateTime nowDateTime = now.atZone(zoneId);
            int entryHour = entryDetails.getEntryHourTime();
            int entryMinute = entryDetails.getEntryMinsTime();
            int nowHour = nowDateTime.getHour();
            int nowMinute = nowDateTime.getMinute();

            ExitDetails exitDetails = strategy.getExitDetails();
            Instant entryTime = LocalDateTime.of(today, LocalTime.of(entryHour, entryMinute)).atZone(zoneId).toInstant();
            Instant exitTime = LocalDateTime.of(today, LocalTime.of(exitDetails.getExitHourTime(), exitDetails.getExitMinsTime())).atZone(zoneId).toInstant();

            if ((now.isAfter(entryTime) && now.isBefore(exitTime)) ||
                    (entryHour == nowHour && entryMinute == nowMinute)) {

                logger.info("Synthetic ATM Delta Neutral strategy triggered for {}", strategy.getId());
                Optional<Strategy> strategyOpt = strategyRepository.findById(strategy.getId());

                if (strategyOpt.isPresent() && strategyOpt.get().getStatus().equalsIgnoreCase(Status.ACTIVE.getKey())) {
                    if (strategyOpt.get().getReSignalCount() > strategyOpt.get().getSignalCount()) {
                        this.runStrategy(strategyOpt.get());
                    }
                }
            }
        } else if (strategy.getStatus().equalsIgnoreCase(Status.LIVE.getKey())) {
            List<Signal> signals = signalRepository.findByStrategyIdAndStatus(strategy.getId(), SignalStatus.LIVE.getKey());
            if (!signals.isEmpty()) {
                boolean shouldExit = shouldExitStrategy(strategy, signals.get(0));
                if (shouldExit) {
                    Optional<Strategy> strategyOpt = strategyRepository.findById(strategy.getId());
                    if (strategyOpt.isPresent() && strategyOpt.get().getStatus().equalsIgnoreCase(Status.LIVE.getKey())) {
                        this.exitStrategy(strategyOpt.get());
                    }
                }
            }
        }
    }

    private boolean shouldExitStrategy(Strategy strategy, Signal signal) {
        // Check if market is about to close (3:10 PM IST)
        LocalTime now = LocalTime.now(ZoneId.of("Asia/Kolkata"));
        if (now.isAfter(LocalTime.of(15, 10))) {
            logger.info("Market closing time reached, exiting positions");
            return true;
        }
        return false;
    }

}











