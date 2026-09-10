package com.quantlab.signal.service;

import com.quantlab.common.entity.*;
import com.quantlab.common.loggingService.DeploymentErrorService;
import com.quantlab.common.repository.ExitDetailsRepository;
import com.quantlab.common.repository.SignalRepository;
import com.quantlab.common.repository.StrategyLegRepository;
import com.quantlab.common.repository.StrategyRepository;
import com.quantlab.common.dto.DiyLegStopLossCheckDto;
import com.quantlab.common.utils.staticstore.dropdownutils.*;
import com.quantlab.common.utils.staticstore.dropdownutils.Status;
import com.quantlab.signal.dto.redisDto.MarketData;
import com.quantlab.signal.service.redisService.TouchLineService;
import com.quantlab.signal.strategy.SignalService;
import com.quantlab.signal.utils.CommonUtils;
import com.quantlab.signal.web.service.MarketDataFetch;
import jakarta.transaction.Transactional;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.hibernate.Hibernate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import static com.quantlab.common.utils.staticstore.AppConstants.*;
import static com.quantlab.common.utils.staticstore.dropdownutils.TgtMenu.PERCENT_OF_ENTRY_PRICE;

@Service
public class DiyStrategyService {

    private static final Logger logger = LogManager.getLogger(DiyStrategyService.class);

    public static ConcurrentHashMap<Long, Double> liveStrategyPNL = new ConcurrentHashMap<>();

    private final SignalRepository signalRepository;

    private final TouchLineService touchLineService;

    private final SignalService signalService;

    private final CommonUtils commonUtils;

    private final GrpcService grpcService;

    private final MarketDataFetch marketDataFetch;

    private final StrategyRepository strategyRepository;

    private final StrategyLegRepository strategyLegRepository;

    private final GrpcErrorService grpcErrorService;

    private final ExitDetailsRepository exitDetailsRepository;
    private final OrderCommonService orderCommonService;

    CugUsersService cugUsersService;

    DeploymentErrorService deploymentErrorService;


    @Autowired
    public DiyStrategyService(SignalRepository signalRepository, TouchLineService touchLineService, SignalService signalService, CommonUtils commonUtils , GrpcService grpcService , MarketDataFetch marketDataFetch , StrategyLegRepository strategyLegRepository, StrategyRepository strategyRepository , GrpcErrorService grpcErrorService, ExitDetailsRepository exitDetailsRepository, CugUsersService cugUsersService, DeploymentErrorService deploymentErrorService, OrderCommonService orderCommonService) {
        this.signalRepository = signalRepository;
        this.touchLineService = touchLineService;
        this.signalService = signalService;
        this.commonUtils = commonUtils;
        this.grpcService = grpcService;
        this.marketDataFetch = marketDataFetch;
        this.strategyLegRepository = strategyLegRepository;
        this.strategyRepository = strategyRepository;
        this.grpcErrorService = grpcErrorService;
        this.exitDetailsRepository = exitDetailsRepository;
        this.cugUsersService = cugUsersService;
        this.deploymentErrorService = deploymentErrorService;
        this.orderCommonService = orderCommonService;
    }

    public boolean checkDiyEntry(Strategy strategy) {
        // has to check the pause the strategy
        if (StrategyOption.ENABLE_HOLD.getKey().equalsIgnoreCase(strategy.getHoldType())){
            return false ;
        }
        // has to check for the entry conditions
        if (strategy.getStatus().equalsIgnoreCase(Status.ACTIVE.getKey())) {
            Hibernate.initialize(strategy.getEntryDetails());
            EntryDetails entryDetails = strategy.getEntryDetails();

            ZoneId zoneId = ZoneId.systemDefault();
            Instant now = Instant.now();
            LocalDate today = LocalDate.now(zoneId);
            // Convert both Instants to ZonedDateTime
            ZonedDateTime nowDateTime = now.atZone(zoneId);
            // Extract hours and minutes
            int entryHour = entryDetails.getEntryHourTime();
            int entryMinute = entryDetails.getEntryMinsTime();
            int nowHour = nowDateTime.getHour();
            int nowMinute = nowDateTime.getMinute();
            ExitDetails exitDetails = strategy.getExitDetails();
            //Instant entryTime = entryDetails.getEntryTime();
            Instant entryTime = LocalDateTime.of(today, LocalTime.of(entryDetails.getEntryHourTime(),entryDetails.getEntryMinsTime() )).atZone(zoneId).toInstant();
            Instant exit = LocalDateTime.of(today, LocalTime.of(exitDetails.getExitHourTime(),exitDetails.getExitMinsTime() )).atZone(zoneId).toInstant();
            // Convert Instant to LocalDateTime without applying any zone shift
            if ((now.isAfter(entryTime)  && now.isBefore(exit)) || (entryDetails.getEntryHourTime() == nowHour && entryDetails.getEntryMinsTime() == nowMinute)) {
                logger.info("Exit Time Is: "+exit+"Entry Time Is: "+entryTime+" Now Time Is: "+now+" strategyID = "+strategy.getId()+" ,"+"entryHour: " + entryHour + ", entryMinute: " + entryMinute + " :: nowHour: " + nowHour + ", nowMinute: " + nowMinute);
                logger.info("Strategy is triggered ");
                // now has to check the Multiple Signals are there or not
//                List<Signal> signals = signalRepository.findSignalsByPositionTypeAndStrategyLive(StrategyType.POSITIONAL.getKey(), StrategyType.INTRADAY.getKey(),strategy.getId());
                return true;
            } else {
                return false;
            }
        }
        return false;
    }


    @Transactional
    public boolean checkDiyExit(Strategy strategy) {

        if (strategy.getStatus().equalsIgnoreCase(Status.LIVE.getKey())) {
            if (strategy.getManualExitType().equalsIgnoreCase(ManualExit.ENABLED.getKey())) {
                return true;
            }

            Hibernate.initialize(strategy.getExitDetails());
            ExitDetails exitDetails = strategy.getExitDetails();

            if (exitDetails == null){
                return false;
            }

            if (checkExitTimeReached(strategy, exitDetails)) {
                return true;
            }
            if (checkStrategyStopLoss(strategy, exitDetails))
                return true;
        }
        //stopping the DIY leg level stop loss check for time being for better performance
        diyLegStopLossCheckAndExit(strategy);
        return false;
    }

    private Boolean checkStrategyStopLoss(Strategy strategy, ExitDetails exitDetails) {

        try {
            Double strategyProfitLoss = liveStrategyPNL.get(strategy.getId());
            Double profitMtmUnitValue = null;
            Double stoplossMtmValue = null;
            if (exitDetails.getTargetUnitToggle().equalsIgnoreCase(TOGGLE_TRUE) && exitDetails.getTargetUnitType() != null) {
                double res = exitDetails.getProfitMtmUnitValue();
                profitMtmUnitValue = (double) Math.round(res);
            }
            if (exitDetails.getStopLossUnitToggle().equalsIgnoreCase(TOGGLE_TRUE) && exitDetails.getStopLossUnitType() != null) {
                double res = exitDetails.getStoplossMtmUnitValue();
                stoplossMtmValue = (double) Math.round(res);
            }

            return profitLossCheck(strategyProfitLoss, profitMtmUnitValue, stoplossMtmValue, strategy);

        }catch (Exception e){
            logger.error("Error while calculating strategy level profit and loss for strategy id: {}, exception = {}", strategy.getId(), e);
        }
        return false;
    }

    private Boolean profitLossCheck(Double strategyProfitLoss, Double profitMtmUnitValue, Double stopLossMtmValue, Strategy strategy) {
        String prefix = "Strategy exit condition met: ";
        if (strategyProfitLoss == null) {
            return false;
        }
        if (profitMtmUnitValue != null && strategyProfitLoss >= profitMtmUnitValue) {
            String suffix = "profit reached = " +strategyProfitLoss;
            deploymentErrorService.saveStrategyUpdateLogs(strategy,prefix + suffix);
            return true;
        }
        if (stopLossMtmValue != null && strategyProfitLoss <= -stopLossMtmValue) {
            String suffix = "Loss reached = " +strategyProfitLoss;
            deploymentErrorService.saveStrategyUpdateLogs(strategy,prefix + suffix);
            return true;
        }
        return false;
    }

    private void diyLegStopLossCheckAndExit(Strategy strategy) {
        if (strategy.getStrategyCategory().getId() !=4){
            List<DiyLegStopLossCheckDto> legs = strategyLegRepository.findLatestSignalLegsForStopLossCheck(
                    strategy.getId(),
                    SignalStatus.LIVE.getKey(),
                    Status.LIVE.getKey()
            );
            if (legs.isEmpty()) {
                return;
            }

            List<LegExitHit> exitHits = new ArrayList<>();

            for (DiyLegStopLossCheckDto leg : legs) {
                LegExitResult exitResult;
//                if (TOGGLE_TRUE.equalsIgnoreCase(leg.getTrailingStopLossToggle())) { check and update the TSL logic
                if (false) {
                    exitResult = changeTrailingStopLoss(leg);
                } else {
                    exitResult = checkLegStopProfitLoss(leg);
                }
                if (exitResult.isExit()) {
                    exitHits.add(new LegExitHit(
                            leg.getId(),
                            leg.getName(),
                            exitResult.reason(),
                            leg.getTargetFinalValue(),
                            leg.getStopLossFinalValue(),
                            leg.getTrailingStopLossPoints()
                    ));
                }
            }

            if (exitHits.isEmpty()) {
                return;
            }

            Optional<Signal> optionalSignal = signalRepository.findFirstByStrategyIdAndStatusOrderByCreatedAtDesc(
                    strategy.getId(), SignalStatus.LIVE.getKey()
            );
            if (optionalSignal.isEmpty()) {
                return;
            }

            Signal signal = optionalSignal.get();
            List<Long> hitLegIds = exitHits.stream().map(LegExitHit::legId).toList();
            List<StrategyLeg> legsToExit = strategyLegRepository.findBySignalIdAndIdInAndStatus(
                    signal.getId(),
                    hitLegIds,
                    Status.LIVE.getKey()
            );
            if (legsToExit.isEmpty()) {
                return;
            }

            String reasons = exitHits.stream()
                    .map(this::formatLegExitLog)
                    .collect(Collectors.joining(", "));
            deploymentErrorService.saveStrategyUpdateLogs(strategy, "DIY leg exit hits: " + reasons);

            try {
                triggerExitOrder(strategy, legsToExit, signal);
            }catch (Exception e) {
                logger.error("Error while triggering exit order for strategy leg: " + strategy.getId(), e);
            }
//            else {
//                strategyLegRepository.saveAll(legs);
//            }
        }
    }

    private boolean checkExitTimeReached(Strategy strategy, ExitDetails exitDetails) {

        Instant now = Instant.now();
        ZoneId zoneId = ZoneId.systemDefault();
        ZonedDateTime nowDateTime = now.atZone(zoneId);
        int nowHour = nowDateTime.getHour();
        int nowMinute = nowDateTime.getMinute();

        // Extract hours and minutes from strategy
        int exitHour = exitDetails.getExitHourTime();
        int exitMinute = exitDetails.getExitMinsTime();


        if (strategy.getPositionType().equalsIgnoreCase(StrategyType.POSITIONAL.getKey())){
            if (!checkPositionalExit(strategy,exitDetails))
                return false;
        }
            return exitHour == nowHour && exitMinute == nowMinute;
    }


        private boolean checkPositionalExit(Strategy strategy, ExitDetails exitDetails) {
            ZoneId zoneId = ZoneId.systemDefault();
            LocalDate today = LocalDate.now(zoneId);
            Instant exitStrategyDate = exitDetails.getExitStrategyDate();
            if (exitStrategyDate != null) {
                LocalDate exitDate = exitStrategyDate.atZone(zoneId).toLocalDate();
                return !today.isBefore(exitDate);
            }

            // Backward compatibility for existing rows that do not yet have exit_strategy_date.
            Instant lastDeployedSignalDate = signalRepository.findLastCreatedAtByStrategyId(strategy.getId());

            if (lastDeployedSignalDate == null) {
                return false;
            }
            long daysSinceLastSignal = ChronoUnit.DAYS.between(lastDeployedSignalDate, Instant.now());
            Integer exitAfterDays = exitDetails.getExitAfterEntryDays();

            if (exitAfterDays == null || daysSinceLastSignal >= exitAfterDays) {
                return true;
            }
            return false;
        }

    public void checkTrailingStopLoss(Strategy strategy) {
        if (strategy.getStatus().equalsIgnoreCase(Status.LIVE.getKey())) {
            Hibernate.initialize(strategy.getStrategyLeg());
            List<StrategyLeg> legs = strategy.getStrategyLeg();
            for (StrategyLeg leg : legs) {
                if (leg.getStatus().equalsIgnoreCase(Status.LIVE.getKey())) {
                    // has to calculate the leg level profit and loss

                }
            }
        }
    }

    public boolean checkLegStopProfitLoss(StrategyLeg strategyLeg) {

        if (strategyLeg == null || strategyLeg.getExecutedPrice() == null) {
            logger.debug("Warning: StrategyLeg or executed price is null. ");
            return false;
        }

        MarketData marketData = touchLineService.getTouchLine(String.valueOf(strategyLeg.getExchangeInstrumentId()));

        if (marketData == null) {
            logger.debug("Warning: Market data or LTP is not available for stopLoss calculation.");
            return false;
        }
        double executedPrice = strategyLeg.getExecutedPrice()/(double) AMOUNT_MULTIPLIER;

        double sign = strategyLeg.getBuySellFlag().equalsIgnoreCase(LegSide.SELL.getKey())? -1: 1;
        double profitLoss = (long) (marketData.getLTP() - executedPrice) * strategyLeg.getQuantity() * sign;
        double stopLossValue = requiredValue(strategyLeg.getTrailingStopLossToggle(),strategyLeg.getStopLossUnitType(), strategyLeg.getStopLossUnitValue(), executedPrice);
        double targetValue = requiredValue(strategyLeg.getTargetUnitToggle(), strategyLeg.getTargetUnitType(), strategyLeg.getTargetUnitValue(), executedPrice);

        if (profitLoss >= 0 && targetValue >= 0
                && TOGGLE_TRUE.equalsIgnoreCase(strategyLeg.getTargetUnitToggle()) && profitLoss >= targetValue) {
            strategyLeg.setStatus(LegStatus.EXCHANGE.getKey());
            return true;
        }

        if (profitLoss <= 0 && stopLossValue >= 0
                && TOGGLE_TRUE.equalsIgnoreCase(strategyLeg.getStopLossUnitToggle()) && (- profitLoss) >= stopLossValue) {
            strategyLeg.setStatus(LegStatus.EXCHANGE.getKey());
            return true;
        }
        // If no exit condition is met, return false
        return false;
    }

    private LegExitResult checkLegStopProfitLoss(DiyLegStopLossCheckDto strategyLeg) {

        if (strategyLeg.getExecutedPrice() == null) {
            logger.debug("Warning: StrategyLeg or executed price is null.");
            return LegExitResult.noExit();
        }

        MarketData marketData = touchLineService.getTouchLine(String.valueOf(strategyLeg.getExchangeInstrumentId()));

        if (marketData == null) {
            logger.debug("Warning: Market data or LTP is not available for stopLoss calculation.");
            return LegExitResult.noExit();
        }
        double executedPrice = strategyLeg.getExecutedPrice()/(double) AMOUNT_MULTIPLIER;

        double sign = strategyLeg.getBuySellFlag().equalsIgnoreCase(LegSide.SELL.getKey())? -1: 1;
        double profitLoss = (long) (marketData.getLTP() - executedPrice) * strategyLeg.getQuantity() * sign;
        double stopLossValue = strategyLeg.getStopLossFinalValue() != null ? strategyLeg.getStopLossFinalValue() : 0.0;
        double targetValue = strategyLeg.getTargetFinalValue() != null ? strategyLeg.getTargetFinalValue() : 0.0;

        if (profitLoss >= 0 && targetValue >= 0
                && TOGGLE_TRUE.equalsIgnoreCase(strategyLeg.getTargetUnitToggle()) && profitLoss >= targetValue) {
            return LegExitResult.exit("TARGET HIT");
        }

        if (profitLoss <= 0 && stopLossValue >= 0
                && TOGGLE_TRUE.equalsIgnoreCase(strategyLeg.getStopLossUnitToggle()) && (- profitLoss) >= stopLossValue) {
            return LegExitResult.exit("STOPLOSS HIT");
        }
        return LegExitResult.noExit();
    }


    public boolean changeTrailingStopLoss(StrategyLeg strategyLeg) {
        if (strategyLeg.getStatus().equalsIgnoreCase(Status.LIVE.getKey())) {
            // Fetch the latest touchline data
            MarketData touchlineBinaryResponse = touchLineService.getTouchLine(String.valueOf(strategyLeg.getExchangeInstrumentId()));
            if (touchlineBinaryResponse != null) {
                // Current market price
                double currentPrice = touchlineBinaryResponse.getLTP();
                // Trailing distance
                double trailingDistance = strategyLeg.getTrailingDistance();

                // for Long Positions
                if (strategyLeg.getBuySellFlag().equalsIgnoreCase(LegSide.BUY.getKey())) {
                    double stopLossValueDistance = currentPrice - trailingDistance;

                    // trailing stop loss if conditions are met
                    if (currentPrice > (strategyLeg.getExecutedPrice()/(double) AMOUNT_MULTIPLIER) && stopLossValueDistance > strategyLeg.getTrailingStopLossPoints()) {
                        long newStopLoss = (long) stopLossValueDistance;
                        strategyLeg.setTrailingStopLossPoints(newStopLoss);
                        return false;
                    }

                    // Trigger exit order if ltp is less than or equal to stop loss
                    if (currentPrice <= strategyLeg.getTrailingStopLossPoints()) {
                        strategyLeg.setStatus(LegStatus.EXCHANGE.getKey());
                        // triggerExitOrder(strategyLeg);
                        return true;
                    }
                }
                // for Short Positions
                else if (strategyLeg.getBuySellFlag().equalsIgnoreCase(LegSide.SELL.getKey())) {
                    double stopLossValueDistance = currentPrice + trailingDistance;

                    // trailing stop loss if conditions are met
                    if (currentPrice < (strategyLeg.getExecutedPrice()/(double) AMOUNT_MULTIPLIER) && stopLossValueDistance < strategyLeg.getTrailingStopLossPoints()) {
                        long newStopLoss = (long) stopLossValueDistance;
                        strategyLeg.setTrailingStopLossPoints(newStopLoss);

                        return false;
                    }
                    // Triggering the  exit order if ltp is greater than or equal to Stop loss
                    if (currentPrice >= strategyLeg.getTrailingStopLossPoints()) {
                        strategyLeg.setStatus(LegStatus.EXCHANGE.getKey());
                        // triggerExitOrder(strategyLeg);
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private LegExitResult changeTrailingStopLoss(DiyLegStopLossCheckDto strategyLeg) {
        MarketData touchlineBinaryResponse = touchLineService.getTouchLine(String.valueOf(strategyLeg.getExchangeInstrumentId()));
        if (touchlineBinaryResponse == null) {
            return LegExitResult.noExit();
        }

        double currentPrice = touchlineBinaryResponse.getLTP();
        double trailingDistance = strategyLeg.getTrailingDistance();
        Long trailingStopLossPoints = strategyLeg.getTrailingStopLossPoints();
        if (trailingStopLossPoints == null) {
            return LegExitResult.noExit();
        }

        if (strategyLeg.getBuySellFlag().equalsIgnoreCase(LegSide.BUY.getKey())) {
            double stopLossValueDistance = currentPrice - trailingDistance;

            if (currentPrice > (strategyLeg.getExecutedPrice() / (double) AMOUNT_MULTIPLIER)
                    && stopLossValueDistance > trailingStopLossPoints) {
                return LegExitResult.noExit();
            }

            return currentPrice <= trailingStopLossPoints ? LegExitResult.exit("TRAILING_STOPLOSS_HIT") : LegExitResult.noExit();
        }

        if (strategyLeg.getBuySellFlag().equalsIgnoreCase(LegSide.SELL.getKey())) {
            double stopLossValueDistance = currentPrice + trailingDistance;

            if (currentPrice < (strategyLeg.getExecutedPrice() / (double) AMOUNT_MULTIPLIER)
                    && stopLossValueDistance < trailingStopLossPoints) {
                return LegExitResult.noExit();
            }

            return currentPrice >= trailingStopLossPoints ? LegExitResult.exit("TRAILING_STOPLOSS_HIT") : LegExitResult.noExit();
        }

        return LegExitResult.noExit();
    }



    private void triggerExitOrder(Strategy strategy, List<StrategyLeg> strategyLegs, Signal signal) {

        logger.info("Triggering exit order for strategy leg: " + strategy.getId());
        Signal newSignal = signalService.createSingleExitWithList(strategy, strategyLegs,signal);
        if (newSignal == null) {
            logger.error("Failed to create exit signal for strategy leg: " + strategy.getId());
            return;
        }
        if (!strategy.getExecutionType().equalsIgnoreCase(ExecutionTypeMenu.PAPER_TRADING.getKey())
                && (cugUsersService.isUserInCug(signal.getAppUser().getTenentId()))){
            grpcService.sendExitSignal(newSignal);
            logger.info("Exit signal sent for strategy leg: " + strategy.getId());
        } else {
            logger.info("Exit signal created for strategy leg: " + strategy.getId() + " but not sent due to paper trading mode.");
        }

        // Ensure status convergence for DIY leg-stoploss exits when there are no live OPEN legs left.
        orderCommonService.reconcilePostExitState(strategy, newSignal);

    }

    private Double requiredValue(String toggle, String type, Long selectedValue, Double executedPrice) {
        if (TOGGLE_TRUE.equalsIgnoreCase(toggle)) {
            if (TgtMenu.PERCENT_OF_ENTRY_PRICE.getKey().equalsIgnoreCase(type)) {
                return executedPrice * selectedValue / 100;
            } else {
                return Double.valueOf(selectedValue);
            }
        }
        return 0.0;
    }

    private String formatLegExitLog(LegExitHit hit) {
        String legName = (hit.legName() == null || hit.legName().isBlank()) ? String.valueOf(hit.legId()) : hit.legName();
        return switch (hit.reason()) {
            case "TARGET HIT" -> legName + " hit the target " + safeAmount(hit.targetFinalValue());
            case "STOPLOSS HIT" -> legName + " hit the stopLoss " + safeAmount(hit.stopLossFinalValue());
            case "TRAILING_STOPLOSS_HIT" -> legName + " hit the trailing stopLoss " + safeAmount(hit.trailingStopLossPoints());
            default -> legName + " exit triggered: " + hit.reason();
        };
    }

    private long safeAmount(Long value) {
        return value == null ? 0L : value;
    }

    private record LegExitHit(Long legId,
                              String legName,
                              String reason,
                              Long targetFinalValue,
                              Long stopLossFinalValue,
                              Long trailingStopLossPoints) {}

    private record LegExitResult(boolean isExit, String reason) {
        private static LegExitResult noExit() {
            return new LegExitResult(false, "");
        }

        private static LegExitResult exit(String reason) {
            return new LegExitResult(true, reason);
        }
    }
}
