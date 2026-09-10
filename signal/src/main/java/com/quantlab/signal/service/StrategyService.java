package com.quantlab.signal.service;

import com.quantlab.common.dao.PhoenixSignalDto;
import com.quantlab.common.dao.StrategyStatusResignalDao;
import com.quantlab.common.entity.*;
import com.quantlab.common.repository.*;
import com.quantlab.common.utils.staticstore.DeltaNeutralExit;
import com.quantlab.common.utils.staticstore.dropdownutils.*;
import com.quantlab.common.utils.staticstore.dropdownutils.Status;
import com.quantlab.signal.dto.*;
import com.quantlab.signal.dto.redisDto.MarketData;
import com.quantlab.signal.dto.redisDto.MasterResponseFO;
import com.quantlab.signal.service.redisService.TouchLineService;
import com.quantlab.signal.strategy.SignalService;
import com.quantlab.signal.utils.CommonUtils;
import com.quantlab.signal.web.dto.MarketLiveDto;
import com.quantlab.signal.web.service.MarketDataFetch;
import org.apache.logging.log4j.LogManager;
import org.hibernate.Hibernate;
import org.modelmapper.ModelMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import static com.quantlab.common.utils.staticstore.AppConstants.*;
import static com.quantlab.signal.service.DiyStrategyService.liveStrategyPNL;

@Service
@Transactional
public class StrategyService {

    private static final org.apache.logging.log4j.Logger logger = LogManager.getLogger(StrategyService.class);

    // Strike interval for Jodi adjustment (100 for NIFTY/BANKNIFTY)
    private static final int JODI_STRIKE_INTERVAL = 100;

    // Confirmation time in seconds for Jodi adjustment
    private static final int JODI_CONFIRMATION_SECONDS = 30;

    private final ConcurrentMap<Long, Integer> jodiCandidateStrike = new ConcurrentHashMap<>();
    private final ConcurrentMap<Long, Instant> jodiCandidateDetectedAt = new ConcurrentHashMap<>();


    @Autowired
    StrategyRepository strategyRepository;

    @Autowired
    private ModelMapper modelMapper;

    @Autowired
    SignalRepository signalRepository;

    @Autowired
    AppUserRepository appUserRepository;

    @Autowired
    TouchLineService touchLineService;

    @Autowired
    GrpcService grpcService;

    @Autowired
    UnderlyingRespository underlyingRespository;

    @Autowired
    EntryDetailsRepository entryDetailsRepository;

    @Autowired
    EntryDaysRespository entryDaysRespository;

    @Autowired
    StrategyLegRepository strategyLegRepository;

    @Autowired
    SignalAdditionsRepository signalAdditionsRepository;

    @Autowired
    private MarketDataFetch marketDataFetch;

    @Autowired
    SignalService signalService;

    @Autowired
    CommonUtils commonUtils;

    @Autowired
    GrpcErrorService grpcErrorService;

    Signal collarExitSignal;
    Signal phoenixExitSignal;

    public boolean deltaNeutralExitCheck(String underling, Signal signal, Strategy strategy) {
        try {
            if (strategy.getManualExitType().equalsIgnoreCase(ManualExit.ENABLED.getKey())) {
                return true;
            }

            Double strategyProfitLoss = liveStrategyPNL.get(strategy.getId());
            if (strategyProfitLoss != null && strategyProfitLoss <= -MAX_STOP_LOSS_IN_HOUSE * strategy.getMultiplier()) {
                strategy.setManualExitType(ManualExit.ENABLED.getKey());
                strategyRepository.save(strategy);
                return true;
            }

            ZoneId zoneId = ZoneId.systemDefault();
            LocalDate today = LocalDate.now(zoneId);

            ExitDetails exitDetails = strategy.getExitDetails();
            Instant exit = LocalDateTime.of(today, LocalTime.of(exitDetails.getExitHourTime(), exitDetails.getExitMinsTime())).atZone(zoneId).toInstant();

            Instant nowTime = Instant.now();
            if (exit.equals(nowTime) || nowTime.isAfter(exit)) {
                return true;
            }
            List<DeltaNeutralCheckDto> check = Arrays.stream(DeltaNeutralExit.values()).map((dto) -> new DeltaNeutralCheckDto(dto.getKey(), dto.getPositive(), dto.getNegative())).toList();
            int callLots = 0;
            int putLots = 0;
            List<StrategyLeg> legs = signal.getSignalLegs();
            for (StrategyLeg leg : legs) {
                if (leg.getLegType().equalsIgnoreCase(LegType.OPEN.getKey())) {
                    if (leg.getOptionType().equalsIgnoreCase(SegmentType.CE.getKey())) {
                        callLots = Math.toIntExact(leg.getNoOfLots());
                    } else if (leg.getOptionType().equalsIgnoreCase(SegmentType.PE.getKey())) {
                        putLots = Math.toIntExact(leg.getNoOfLots());
                    }

                }
            }
            DeltaNeutralCheckDto checkDto = check.stream().filter(dto -> dto.getLabel().equalsIgnoreCase(underling)).findFirst().orElse(null);

            Hibernate.initialize(signal.getSignalAdditions());
            SignalAdditions signalAdditions = signal.getSignalAdditions();
            double syntheticPrice = marketDataFetch.getSyntheticPrice(strategy, strategy.getUnderlying().getName());

            double entryPrice = (double) signalAdditions.getEntryUnderlingPrice() / AMOUNT_MULTIPLIER;

            int different = Math.abs((int) entryPrice - (int) syntheticPrice);

            if (callLots > putLots) {
                if (entryPrice > syntheticPrice) {
                    if (different > checkDto.getPositive()) {
                        logger.info("EXIT TRIGGERED Check Label: " + checkDto.getLabel() + ", strategy id: " + strategy.getId() + ", Entry Underlying Price: " + signal.getSignalAdditions().getEntryUnderlingPrice() / AMOUNT_MULTIPLIER + ", Different: " + different + ", Entry Price: " + entryPrice + ", Synthetic Price: " + syntheticPrice + ", Call Lots: " + callLots + ", Put Lots: " + putLots + ", Check Label positive: " + checkDto.getPositive() + ", Check Label negative: " + checkDto.getNegative());
                        logger.info("EXIT TRIGGERED Delta neutral exit check passed for call leg" + different + " > " + checkDto.getPositive() + ", callLots: " + callLots + ", putLots: " + putLots);
                        return true;
                    } else return false;
                } else if (entryPrice < syntheticPrice) {
                    if (different > checkDto.getNegative()) {
                        logger.info("EXIT TRIGGERED Check Label: " + checkDto.getLabel() + ", strategy id: " + strategy.getId() + ", Entry Underlying Price: " + signal.getSignalAdditions().getEntryUnderlingPrice() / AMOUNT_MULTIPLIER + ", Different: " + different + ", Entry Price: " + entryPrice + ", Synthetic Price: " + syntheticPrice + ", Call Lots: " + callLots + ", Put Lots: " + putLots + ", Check Label positive: " + checkDto.getPositive() + ", Check Label negative: " + checkDto.getNegative());
                        logger.info("EXIT TRIGGERED Delta neutral exit check passed for call leg" + different + " > " + checkDto.getNegative() + ", callLots: " + callLots + ", putLots: " + putLots);
                        return true;
                    } else return false;
                }

            } else if (callLots < putLots) {
                if (entryPrice > syntheticPrice) {
                    if (different > checkDto.getNegative()) {
                        logger.info("EXIT TRIGGERED Check Label: " + checkDto.getLabel() + ", strategy id: " + strategy.getId() + ", Entry Underlying Price: " + signal.getSignalAdditions().getEntryUnderlingPrice() / AMOUNT_MULTIPLIER + ", Different: " + different + ", Entry Price: " + entryPrice + ", Synthetic Price: " + syntheticPrice + ", Call Lots: " + callLots + ", Put Lots: " + putLots + ", Check Label positive: " + checkDto.getPositive() + ", Check Label negative: " + checkDto.getNegative());
                        logger.info("EXIT TRIGGERED Delta neutral exit check passed for call leg" + different + " > " + checkDto.getNegative() + ", callLots: " + callLots + ", putLots: " + putLots);
                        return true;
                    } else return false;
                } else if (entryPrice < syntheticPrice) {
                    if (different > checkDto.getPositive()) {
                        logger.info("EXIT TRIGGERED Check Label: " + checkDto.getLabel() + ", strategy id: " + strategy.getId() + ", Entry Underlying Price: " + signal.getSignalAdditions().getEntryUnderlingPrice() / AMOUNT_MULTIPLIER + ", Different: " + different + ", Entry Price: " + entryPrice + ", Synthetic Price: " + syntheticPrice + ", Call Lots: " + callLots + ", Put Lots: " + putLots + ", Check Label positive: " + checkDto.getPositive() + ", Check Label negative: " + checkDto.getNegative());
                        logger.info("EXIT TRIGGERED Delta neutral exit check passed for call leg" + different + " > " + checkDto.getPositive() + ", callLots: " + callLots + ", putLots: " + putLots);
                        return true;
                    } else return false;
                }
            } else {
                if (different > checkDto.getNegative()) {
                    System.out.println("EXIT TRIGGERED Check Label: " + checkDto.getLabel() + ", strategy id: " + strategy.getId() + ", Entry Underlying Price: " + signal.getSignalAdditions().getEntryUnderlingPrice() / AMOUNT_MULTIPLIER + ", Different: " + different + ", Entry Price: " + entryPrice + ", Synthetic Price: " + syntheticPrice + ", Call Lots: " + callLots + ", Put Lots: " + putLots + ", Check Label positive: " + checkDto.getPositive() + ", Check Label negative: " + checkDto.getNegative());
                    logger.info("EXIT TRIGGERED Delta neutral exit check passed for call leg index is " + checkDto.getLabel() + "UNDERLING NAME : " + underling + "  ------ " + "difference " + different + " > " + checkDto.getNegative() + ", callLots: " + callLots + ", putLots: " + putLots);
                    return true;
                } else return false;
            }
            return false;
        } catch (Exception e) {
            logger.error("Error in delta neutral exit check: " + underling + " , " + signal.getId() + " , ");
            logger.error("Error in delta neutral exit check: " + e.getMessage());
            return false;
        }
    }

    public EntryExitTimes entryExitTimes(Strategy strategy) {

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
        Instant entryTime = LocalDateTime.of(today, LocalTime.of(entryHour, entryMinute)).atZone(zoneId).toInstant();
        Instant exit = LocalDateTime.of(today, LocalTime.of(exitDetails.getExitHourTime(), exitDetails.getExitMinsTime())).atZone(zoneId).toInstant();
        EntryExitTimes exitTimes = new EntryExitTimes();
        exitTimes.setNowInstantTime(now);
        exitTimes.setEntryMinute(entryMinute);
        exitTimes.setEntryHour(entryHour);
        exitTimes.setNowHour(nowHour);
        exitTimes.setNowMinute(nowMinute);
        exitTimes.setExitInstantTime(exit);
        exitTimes.setEntryInstantTime(entryTime);

        return exitTimes;
    }

    public boolean collarEntry(Strategy strategy) {

        if (strategy.getStatus().equalsIgnoreCase(Status.ACTIVE.getKey())) {
            ZoneId zoneId = ZoneId.systemDefault();
            Instant now = Instant.now();
            LocalDate today = LocalDate.now(zoneId);

            Instant entryTime = LocalDateTime.of(today, LocalTime.of(9, 20))
                    .atZone(zoneId)
                    .toInstant();

            ExitDetails exitDetails = strategy.getExitDetails();
            Instant exitTime = LocalDateTime.of(today,
                            LocalTime.of(exitDetails.getExitHourTime(), exitDetails.getExitMinsTime()))
                    .atZone(zoneId)
                    .toInstant();

            if (now.isAfter(entryTime) && now.isBefore(exitTime)) {
                logger.info("Strategy is triggered");
                return true;
            }
        }
        return false;
    }

    @Transactional
    public boolean collarExit(Strategy strategy, Long signalId) {
        try {
            String underlying = strategy.getUnderlying().getName();
            int strikeInterval = getStrikeInterval(underlying);

            if (strategy.getManualExitType().equalsIgnoreCase(ManualExit.ENABLED.getKey())) {
                return true;
            }

            Double strategyProfitLoss = liveStrategyPNL.get(strategy.getId());
            if (strategyProfitLoss != null && strategyProfitLoss <= -MAX_STOP_LOSS_DUALBAND_STRADDLE * strategy.getMultiplier()) {
                strategy.setManualExitType(ManualExit.ENABLED.getKey());
                strategyRepository.save(strategy);
                return true;
            }

            ZoneId zoneId = ZoneId.systemDefault();
            LocalDate today = LocalDate.now(zoneId);
            ExitDetails exitDetails = strategy.getExitDetails();
            Instant exit = LocalDateTime.of(today, LocalTime.of(exitDetails.getExitHourTime(), exitDetails.getExitMinsTime())).atZone(zoneId).toInstant();

            Instant nowTime = Instant.now();
            if (exit.equals(nowTime) || nowTime.isAfter(exit)) {
                return true;
            }

            Optional<PhoenixSignalDto> signalDtoOpt = signalRepository.findPhoenixSignalDtoByIdForUpdate(signalId);
            if (signalDtoOpt.isEmpty()) {
                return false;
            }

            PhoenixSignalDto signalDto = signalDtoOpt.get();
            if (signalDto.getCurrentAtm() == null || signalDto.getBaseIndexPrice() == null) {
                return false;
            }
            int previousATM = signalDto.getCurrentAtm();
            double previousIndexPrice = signalDto.getBaseIndexPrice() / (double) AMOUNT_MULTIPLIER;

            double price;
            if (strategy.getAtmType().equalsIgnoreCase(AtmType.SYNTHETIC_ATM.getKey()))
                price = marketDataFetch.getSyntheticPrice(strategy, strategy.getUnderlying().getName());
            else
                price = marketDataFetch.getMarketData(strategy).getSpotPrice();

            String expiryDate = commonUtils.getExpiryShotDateByIndex(strategy.getExpiry(), strategy.getUnderlying().getName(), OptionType.OPTION.getKey());
            double priceDifference = Math.abs(price - previousIndexPrice);

            // Only load full Signal entity if adjustments are needed
            if (priceDifference > strikeInterval) {
                Optional<Signal> signalOptional = signalRepository.findByIdForUpdate(signalId);
                if (signalOptional.isEmpty()) {
                    return false;
                }
                Signal signal = signalOptional.get();
                collarExitSignal = signal;
                SignalAdditions signalAdditions = signal.getSignalAdditions();

                // Ensure previousATM matches entity if available
                if (signalAdditions != null && signalAdditions.getCurrentAtm() != null) {
                    previousATM = signalAdditions.getCurrentAtm();
                }

                while (priceDifference > strikeInterval) {
                    List<StrategyLeg> strategyLegs = signal.getStrategyLeg();
                    int strikeDiff = price > previousATM ? -(strikeInterval * 2) : (strikeInterval * 2);
                    int offset = price > previousATM ? 1 : -1;
                    exitStrike(strategyLegs, strategy, previousATM, expiryDate, strikeDiff);

                    int entryATM = previousATM + ((3 * strikeInterval) * offset);
                    SignalMapperDto signalMapperDtoCE = createSingleSignalMapperDto(strategy, "CE", offset, entryATM);
                    SignalMapperDto signalMapperDtoPE = createSingleSignalMapperDto(strategy, "PE", offset, entryATM);
                    List<StrategyLeg> createdLegs = signalService.createStrategyLegs(List.of(signalMapperDtoCE, signalMapperDtoPE),
                            strategy, signal, SegmentType.PE.getKey());
                    previousATM = previousATM + (offset * strikeInterval);
                    if (signalAdditions != null) {
                        signalAdditions.setCurrentAtm(previousATM);
                    }
                    signal.setBaseIndexPrice((long) (price * AMOUNT_MULTIPLIER));

                    List<StrategyLeg> newLegs = strategyLegRepository.saveAll(createdLegs);
                    newLegs = assignLegIdentifiers(newLegs, signal);
                    if (signalAdditions != null) {
                        signalAdditionsRepository.saveAndFlush(signalAdditions);
                    }
                    priceDifference = priceDifference - strikeInterval;
                    signal.getSignalLegs().addAll(newLegs);
                }
                if (Math.abs(price - previousIndexPrice) > strikeInterval &&
                        signal.getExecutionType().equalsIgnoreCase(ExecutionTypeMenu.LIVE_TRADING.getKey()) &&
                        signal.getStrategy().getExecutionType().equalsIgnoreCase(ExecutionTypeMenu.LIVE_TRADING.getKey())) {
                    logger.info("Collar exit with dynamic adjustment for strategy ID: " + strategy.getId() + ", Signal ID: " + signal.getId());
                    sendGRPCModificationOrders(signal.getId(), strategy);
                }
            }
            return false;
        } catch (Exception e) {
            if(strategy.getExecutionType().equalsIgnoreCase(ExecutionTypeMenu.LIVE_TRADING.getKey()))
                grpcErrorService.placingOrderLogs(ERROR_PROCESSING_STRATEGY_DATA_NOT_FOUND, collarExitSignal, Status.ERROR.getKey());
            logger.error("Error in collar exit with dynamic adjustment: " + e.getMessage());
            return false;
        }
    }

    @Transactional
    public boolean phoenixExit(Strategy strategy, Long signalId) {
        try {
            // 1. Manual exit check
            if (strategy.getManualExitType().equalsIgnoreCase(ManualExit.ENABLED.getKey())) {
                return true; // force exit
            }

            Double strategyProfitLoss = liveStrategyPNL.get(strategy.getId());
            if (strategyProfitLoss != null && strategyProfitLoss <= -MAX_STOP_LOSS_IN_HOUSE * strategy.getMultiplier()) {
                strategy.setManualExitType(ManualExit.ENABLED.getKey());
                strategyRepository.save(strategy);
                return true;
            }


            ZoneId zoneId = ZoneId.systemDefault();
            LocalDate today = LocalDate.now(zoneId);
            ExitDetails exitDetails = strategy.getExitDetails();
            Instant exitTime = LocalDateTime.of(
                    today,
                    LocalTime.of(exitDetails.getExitHourTime(), exitDetails.getExitMinsTime())
            ).atZone(zoneId).toInstant();

            Instant now = Instant.now();
            if (now.isAfter(exitTime)) {
                return true; // exit after cutoff time
            }

            Optional<com.quantlab.common.dao.PhoenixSignalDto> signalDtoOpt = signalRepository.findPhoenixSignalDtoByIdForUpdate(signalId);
            if (signalDtoOpt.isEmpty()) {
                return false;
            }

            com.quantlab.common.dao.PhoenixSignalDto signalDto = signalDtoOpt.get();
            // we don't load the full Signal entity here to save memory; for error logging we'll keep phoenixExitSignal null
            phoenixExitSignal = null;

            Long baseIndexPrice = signalDto.getBaseIndexPrice();
            if (baseIndexPrice == null) {
                return false;
            }
            double previousIndexPrice = baseIndexPrice / (double) AMOUNT_MULTIPLIER;

            // 3. Get current spot/synthetic price
            double currentPrice;
            if (strategy.getAtmType().equalsIgnoreCase(AtmType.SYNTHETIC_ATM.getKey())) {
                currentPrice = marketDataFetch.getSyntheticPrice(strategy, strategy.getUnderlying().getName());
            } else {
                currentPrice = marketDataFetch.getMarketData(strategy).getSpotPrice();
            }

            // 4. Calculate difference between current price and base price
            double priceDifference = Math.abs(currentPrice - previousIndexPrice);
            return priceDifference > STRIKE_INTERVAL;
        } catch (Exception e) {
            grpcErrorService.placingOrderLogs(
                    ERROR_PROCESSING_STRATEGY_DATA_NOT_FOUND,
                    phoenixExitSignal,
                    Status.ERROR.getKey()
            );
            logger.error("Error in collar exit (ATM Straddle): {}", e.getMessage(), e);
            return false;
        }
    }


    @Transactional
    public List<StrategyLeg> assignLegIdentifiers(List<StrategyLeg> newLegs, Signal signal) {
        for (StrategyLeg leg : newLegs) {
            leg.setLegIdentifier("QO_" + signal.getId() + "_" + leg.getId());
        }
        return strategyLegRepository.saveAllAndFlush(newLegs);
    }

    @Transactional
    public void sendGRPCModificationOrders(Long signalId, Strategy strategy) {

        Optional<Signal> signalOptional = signalRepository.findById(signalId);
        if (signalOptional.isEmpty()) {
            logger.error("Signal not found for ID: " + signalId);
            grpcErrorService.placingOrderLogs(ERROR_PROCESSING_STRATEGY_DATA_NOT_FOUND, null, Status.ERROR.getKey());
            return;
        }

        Signal signal = signalOptional.get();
        sendCollarExit(signal, strategy);
        sendCollarEntry(signal, strategy);
    }

    private void sendCollarEntry(Signal signal, Strategy strategy) {
        try {
            grpcService.sendSignal(signal);
            logger.info("Collar Entry signal sent successfully for strategy ID: " + strategy.getId() + ", Signal ID: " + signal.getId());
        } catch (Exception e) {
            logger.error("Error in sending collar Entry signal: " + e.getMessage());
            grpcErrorService.placingOrderLogs(ERROR_PROCESSING_STRATEGY_DATA_NOT_FOUND, signal, Status.ERROR.getKey());
        }
    }

    private void sendCollarExit(Signal signal, Strategy strategy) {
        try {
            grpcService.sendExitSignal(signal);
            logger.info("Collar exit signal sent successfully for strategy ID: {}, Signal ID: {}", strategy.getId(), signal.getId());
        } catch (Exception e) {
            logger.error("Error in sending collar exit signal: " + e.getMessage());
            grpcErrorService.placingOrderLogs(ERROR_PROCESSING_STRATEGY_DATA_NOT_FOUND, signal, Status.ERROR.getKey());
        }
    }

    public boolean inHouseEntryCheck(Strategy strategy) {

        if (strategy.getManualExitType().equalsIgnoreCase(ManualExit.ENABLED.getKey()))
            return false;

        if (strategy.getStatus().equalsIgnoreCase(Status.ACTIVE.getKey()) || strategy.getStatus().equalsIgnoreCase(StrategyStatus.EXIT.getKey())) {
            EntryExitTimes entryExitTimes = entryExitTimes(strategy);
            if ((entryExitTimes.getNowInstantTime().isAfter(entryExitTimes.getEntryInstantTime()) && entryExitTimes.getNowInstantTime().isBefore(entryExitTimes.getExitInstantTime())) || (entryExitTimes.getEntryHour() == entryExitTimes.getNowHour() && entryExitTimes.getEntryMinute() == entryExitTimes.getNowMinute())) {
                Optional<StrategyStatusResignalDao> strategySnapshot = strategyRepository.findStatusAndCountsById(strategy.getId());
                if ((entryExitTimes.getExitInstantTime().equals(entryExitTimes.getNowInstantTime()) || (entryExitTimes.getExitInstantTime().isAfter(entryExitTimes.getNowInstantTime()))) && strategySnapshot.isPresent() && (strategySnapshot.get().getStatus().equalsIgnoreCase(Status.ACTIVE.getKey()) || strategySnapshot.get().getStatus().equalsIgnoreCase(StrategyStatus.EXIT.getKey()))) {
                    StrategyStatusResignalDao snapshot = strategySnapshot.get();
                    int reSignalCount = snapshot.getReSignalCount() != null ? snapshot.getReSignalCount() : 0;
                    int signalCount = snapshot.getSignalCount() != null ? snapshot.getSignalCount() : 0;
                    String manualExitType = snapshot.getManualExitType() != null ? snapshot.getManualExitType() : "";
//                    logger.info(
//                            "strategyId={}, reSignalCount={}, signalCount={}, manualExitType={}, entryTime={}:{}, nowTime={}:{}",
//                            strategy.getId(),
//                            reSignalCount,
//                            signalCount,
//                            manualExitType,
//                            entryExitTimes.getEntryHour(),
//                            entryExitTimes.getEntryMinute(),
//                            entryExitTimes.getNowHour(),
//                            entryExitTimes.getNowMinute()
//                    );

                    return (reSignalCount > signalCount) && manualExitType.equalsIgnoreCase(ManualExit.DISABLED.getKey());
                }
            }
        }
        return false;
    }

    private SignalMapperDto createSingleSignalMapperDto(Strategy strategy, String optionType, int offSet1, int strikeATM) {
        MarketLiveDto marketLive = marketDataFetch.getMarketData(strategy);
        // 2. Compute synthetic price
        String expiryDate = commonUtils.getExpiryShotDateByIndex(strategy.getExpiry(), strategy.getUnderlying().getName(), OptionType.OPTION.getKey());

        String key = strategy.getUnderlying().getName().toUpperCase(Locale.ROOT) + expiryDate + "-" + strikeATM + optionType;
        MasterResponseFO master = marketDataFetch.getMasterResponse(key);
        MarketData data = touchLineService.getTouchLine(String.valueOf(master.getExchangeInstrumentID()));
        SignalMapperDto leg = new SignalMapperDto();
        leg.setMarketLiveDto(marketLive);
        leg.setTouchlineBinaryResponse(data);
        leg.setLegName(key);
        leg.setMasterData(master);
        leg.setBuySellFlag(LegSide.SELL.getKey());
        leg.setSegment("NSEFO");
        leg.setCategory(optionType.equals("CE") ? LegType.CALL.getKey() : LegType.PUT.getKey());
        leg.setPositionType(strategy.getPositionType());
        leg.setLegType(LegType.OPEN.getKey());
        leg.setDerivativeType(OptionType.OPTION.getKey());
        String lotsKey = optionType.equals("CE") ? "call" : "put";
        leg.setLots(1L);
        leg.setQuantity((int) (master.getLotSize() * strategy.getMultiplier()));

        return leg;
    }

    @Transactional
    public List<Signal> getActiveSignals(Long strategyId, String status) {
        return signalRepository.findByStrategyIdAndStatus(strategyId, status);
    }

    void exitStrike(List<StrategyLeg> strategyLegs, Strategy strategy, int previousATM, String expiryDate, int strikeJump){
        String keyPE = strategy.getUnderlying().getName().toUpperCase(Locale.ROOT) + expiryDate + "-" + (previousATM + strikeJump) + "PE";
        String keyCE = strategy.getUnderlying().getName().toUpperCase(Locale.ROOT) + expiryDate + "-" + (previousATM + strikeJump) + "CE";

        StrategyLeg ceLeg = strategyLegs.stream()
                .filter(leg -> keyCE.equalsIgnoreCase(leg.getName()))
                .findFirst()
                .orElseThrow(() -> new RuntimeException("CE leg not found"));

        StrategyLeg peLeg = strategyLegs.stream()
                .filter(leg -> keyPE.equalsIgnoreCase(leg.getName()))
                .findFirst()
                .orElseThrow(() -> new RuntimeException("PE leg not found"));

        signalService.createSingleExit(strategy, peLeg);
        signalService.createSingleExit(strategy, ceLeg);
    }

    public boolean deltaNeutralExitCheckBySignalId(String underling, Long signalId, Strategy strategy) {
        try {
            if (signalId == null) return false;
            Optional<com.quantlab.common.dao.DeltaNeutralSignalDao> signalDtoOpt = signalRepository.findDeltaNeutralSignalById(signalId);
            if (signalDtoOpt.isEmpty()) return false;
            com.quantlab.common.dao.DeltaNeutralSignalDao signalDto = signalDtoOpt.get();
            List<com.quantlab.common.dao.DeltaNeutralLegDao> legs = strategyLegRepository.findLegsSummaryBySignalId(signalId);
            return deltaNeutralExitCheckUsingDtos(underling, legs, signalDto, strategy);
        } catch (Exception e) {
            logger.error("Error in optimized delta neutral exit check by signalId: {} -> {}", signalId, e.getMessage(), e);
            return false;
        }
    }

    private boolean deltaNeutralExitCheckUsingDtos(String underling, List<com.quantlab.common.dao.DeltaNeutralLegDao> legs, com.quantlab.common.dao.DeltaNeutralSignalDao signalDto, Strategy strategy) {
        try {
            if (strategy.getManualExitType().equalsIgnoreCase(ManualExit.ENABLED.getKey())) {
                return true;
            }

            Double strategyProfitLoss = liveStrategyPNL.get(strategy.getId());
            if (strategyProfitLoss != null && strategyProfitLoss <= -MAX_STOP_LOSS_IN_HOUSE * strategy.getMultiplier()) {
                strategy.setManualExitType(ManualExit.ENABLED.getKey());
                strategyRepository.save(strategy);
                return true;
            }


            ZoneId zoneId = ZoneId.systemDefault();
            LocalDate today = LocalDate.now(zoneId);

            ExitDetails exitDetails = strategy.getExitDetails();
            Instant exit = LocalDateTime.of(today, LocalTime.of(exitDetails.getExitHourTime(), exitDetails.getExitMinsTime())).atZone(zoneId).toInstant();

            Instant nowTime = Instant.now();
            if (exit.equals(nowTime) || nowTime.isAfter(exit)) {
                return true;
            }

            List<DeltaNeutralCheckDto> check = Arrays.stream(DeltaNeutralExit.values()).map((dto) -> new DeltaNeutralCheckDto(dto.getKey(), dto.getPositive(), dto.getNegative())).toList();
            int callLots = 0;
            int putLots = 0;

            for (com.quantlab.common.dao.DeltaNeutralLegDao leg : legs) {
                if (leg.getLegType() != null && leg.getLegType().equalsIgnoreCase(LegType.OPEN.getKey())) {
                    if (leg.getOptionType() != null && leg.getOptionType().equalsIgnoreCase(SegmentType.CE.getKey())) {
                        callLots = Math.toIntExact(leg.getNoOfLots());
                    } else if (leg.getOptionType() != null && leg.getOptionType().equalsIgnoreCase(SegmentType.PE.getKey())) {
                        putLots = Math.toIntExact(leg.getNoOfLots());
                    }
                }
            }

            DeltaNeutralCheckDto checkDto = check.stream().filter(dto -> dto.getLabel().equalsIgnoreCase(underling)).findFirst().orElse(null);
            if (checkDto == null) return false;

            Integer entryUnderlingPriceInt = signalDto.getEntryUnderlingPrice();
            if (entryUnderlingPriceInt == null) return false;
            double entryPrice = (double) entryUnderlingPriceInt / AMOUNT_MULTIPLIER;

            double syntheticPrice = marketDataFetch.getSyntheticPrice(strategy, strategy.getUnderlying().getName());

            int different = Math.abs((int) entryPrice - (int) syntheticPrice);

            if (callLots > putLots) {
                if (entryPrice > syntheticPrice) {
                    return different > checkDto.getPositive();
                } else if (entryPrice < syntheticPrice) {
                    return different > checkDto.getNegative();
                }
            } else if (callLots < putLots) {
                if (entryPrice > syntheticPrice) {
                    return different > checkDto.getNegative();
                } else if (entryPrice < syntheticPrice) {
                    return different > checkDto.getPositive();
                }
            } else {
                return different > checkDto.getNegative();
            }

            return false;
        } catch (Exception e) {
            logger.error("Error in delta neutral exit check using DTOs: {}", e.getMessage(), e);
            return false;
        }
    }
    @Transactional
    public JodiAdjustmentResult checkJodiAdjustment(Strategy strategy) {
        try {
            Long signalAdditionsId = signalRepository.findLatestSignalId_SignalAdditions(strategy.getId(), SignalStatus.LIVE.getKey());
            if (signalAdditionsId == null) {
                return new JodiAdjustmentResult(false);
            }
            Instant nowTime = Instant.now();
            return handleJodiAdjustment(strategy, signalAdditionsId, nowTime);
        } catch (Exception e) {
            logger.error("Error checking Jodi adjustment for strategy {}: {}",
                    strategy.getId(), e.getMessage(), e);
            return new JodiAdjustmentResult(false);
        }
    }

    public boolean shouldExit(Strategy strategy) {
        try {
            if (strategy.getManualExitType().equalsIgnoreCase(ManualExit.ENABLED.getKey())) {
                return true;
            }

            // Check for stop loss
            Double strategyProfitLoss = liveStrategyPNL.get(strategy.getId());
            if (strategyProfitLoss != null && strategyProfitLoss <= -MAX_STOP_LOSS_IN_HOUSE * strategy.getMultiplier()) {
                strategy.setManualExitType(ManualExit.ENABLED.getKey());
                strategyRepository.save(strategy);
                return true;
            }

            ZoneId zoneId = ZoneId.systemDefault();
            LocalDate today = LocalDate.now(zoneId);
            ExitDetails exitDetails = strategy.getExitDetails();
            Instant exit = LocalDateTime.of(today, LocalTime.of(exitDetails.getExitHourTime(),
                    exitDetails.getExitMinsTime())).atZone(zoneId).toInstant();

            Instant nowTime = Instant.now();
            return exit.equals(nowTime) || nowTime.isAfter(exit);
        } catch (Exception e) {
            logger.error("Error checking exit time for strategy {}: {}", strategy.getId(), e.getMessage(), e);
            return false;
        }
    }

    private JodiAdjustmentResult handleJodiAdjustment(Strategy strategy, Long signalAdditionsId, Instant nowTime) {

        SignalAdditions additions = signalAdditionsRepository.findById(signalAdditionsId).orElse(null);

        if (additions == null ||
                additions.getJodiEntryStrike() == null ||
                additions.getJodiEntryPremium() == null ||
                additions.getJodiEntryExpiry() == null) {

            logger.warn("No Jodi entry data in SignalAdditions for strategy={}, signalAdditions={}, cannot adjust.",
                    strategy.getId(), signalAdditionsId);
            return new JodiAdjustmentResult(false);
        }

        int entryStrike = additions.getJodiEntryStrike();
        double originalEntryPremium = additions.getJodiEntryPremium();
        String expiry = additions.getJodiEntryExpiry();

        if (entryStrike <= 0 || originalEntryPremium <= 0) {
            logger.warn("Invalid Jodi entry data: entryStrike={}, entryPremium={} for strategy={}",
                    entryStrike, originalEntryPremium, strategy.getId());
            return new JodiAdjustmentResult(false);
        }

        String underlying = strategy.getUnderlying().getName();

        // Define adjacent strikes using fixed 100-point interval
        int aboveStrike = entryStrike + JODI_STRIKE_INTERVAL;
        int belowStrike = entryStrike - JODI_STRIKE_INTERVAL;

        if (belowStrike <= 0) {
            logger.debug("Below strike <= 0 ({}). Skipping below leg for strategy {}.",
                    belowStrike, strategy.getId());
        }

        double abovePremium = getCurrentJodiPremium(underlying, expiry, aboveStrike);
        double belowPremium = belowStrike > 0
                ? getCurrentJodiPremium(underlying, expiry, belowStrike)
                : 0.0;

        boolean aboveQualifies = abovePremium > 0 && abovePremium < originalEntryPremium;
        boolean belowQualifies = belowPremium > 0 && belowPremium < originalEntryPremium;

        if (!aboveQualifies && !belowQualifies) {
            // No candidate → reset state atomically
            clearJodiCandidateState(strategy.getId());
            return new JodiAdjustmentResult(false);
        }

        int candidateStrike;
        double candidatePremium;
        if (aboveQualifies && belowQualifies) {
            if (abovePremium < belowPremium) {
                candidateStrike = aboveStrike;
                candidatePremium = abovePremium;
            } else {
                candidateStrike = belowStrike;
                candidatePremium = belowPremium;
            }
        } else if (aboveQualifies) {
            candidateStrike = aboveStrike;
            candidatePremium = abovePremium;
        } else {
            candidateStrike = belowStrike;
            candidatePremium = belowPremium;
        }

        logger.debug("JodiAdjust candidateStrike={} for strategy={} (entryStrike={}, entryPremium={}, " +
                        "abovePremium={}, belowPremium={})",
                candidateStrike, strategy.getId(), entryStrike, originalEntryPremium, abovePremium, belowPremium);

        // Atomic check-and-update for 30-second confirmation state
        final int finalCandidateStrike = candidateStrike;
        Instant detectedAt = jodiCandidateDetectedAt.compute(strategy.getId(), (key, existingTime) -> {
            Integer existingCandidate = jodiCandidateStrike.get(key);
            if (existingCandidate == null || !existingCandidate.equals(finalCandidateStrike) || existingTime == null) {
                // First detection or candidate changed → start fresh confirmation window
                jodiCandidateStrike.put(key, finalCandidateStrike);
                return nowTime;
            }
            return existingTime;
        });

        // Check if this is a new candidate detection
        if (detectedAt.equals(nowTime)) {
            logger.info("JodiAdjust candidate={} detected for strategy {}. Starting {}s confirmation.",
                    candidateStrike, strategy.getId(), JODI_CONFIRMATION_SECONDS);
            return new JodiAdjustmentResult(false);
        }

        Duration elapsed = Duration.between(detectedAt, nowTime);
        if (elapsed.getSeconds() < JODI_CONFIRMATION_SECONDS) {
            logger.debug("Waiting for {}s confirmation for strategy {} at strike {}: {}/{}s elapsed",
                    JODI_CONFIRMATION_SECONDS, strategy.getId(), candidateStrike, elapsed.getSeconds(), JODI_CONFIRMATION_SECONDS);
            return new JodiAdjustmentResult(false);
        }

        // Re-confirm condition with fresh premium after confirmation period
        double reconfirmPremium = getCurrentJodiPremium(underlying, expiry, candidateStrike);
        if (reconfirmPremium <= 0 || reconfirmPremium >= originalEntryPremium) {
            logger.info("JodiAdjust invalid after {}s for strategy {}. candidateStrike={}, reconfirmPremium={}, " +
                            "originalEntryPremium={}. Clearing candidate.",
                    JODI_CONFIRMATION_SECONDS, strategy.getId(), candidateStrike, reconfirmPremium, originalEntryPremium);

            clearJodiCandidateState(strategy.getId());
            return new JodiAdjustmentResult(false);
        }

        logger.info("JodiAdjust CONFIRMED for strategy {}. Moving from strike {} to {} " +
                        "(entryPremium={}, newPremium={})",
                strategy.getId(), entryStrike, candidateStrike, originalEntryPremium, reconfirmPremium);

        // Clear the candidate state after successful adjustment
        clearJodiCandidateState(strategy.getId());

        return new JodiAdjustmentResult(true, candidateStrike, expiry, reconfirmPremium);
    }

    /**
     * Atomically clears the Jodi candidate state for a strategy.
     * Call this when a strategy is stopped/deleted to prevent memory leaks.
     */
    public void clearJodiCandidateState(Long strategyId) {
        jodiCandidateStrike.remove(strategyId);
        jodiCandidateDetectedAt.remove(strategyId);
    }

    private double getCurrentJodiPremium(String underlying, String expiry, int strike) {
        try {
            if (strike <= 0) {
                return 0.0;
            }

            String ceKey = underlying.toUpperCase(Locale.ROOT) + expiry + "-" + strike + "CE";
            String peKey = underlying.toUpperCase(Locale.ROOT) + expiry + "-" + strike + "PE";

            MasterResponseFO ceMaster = marketDataFetch.getMasterResponse(ceKey);
            MasterResponseFO peMaster = marketDataFetch.getMasterResponse(peKey);

            if (ceMaster == null || peMaster == null) {
                return 0.0;
            }

            MarketData ceData = touchLineService.getTouchLine(String.valueOf(ceMaster.getExchangeInstrumentID()));
            MarketData peData = touchLineService.getTouchLine(String.valueOf(peMaster.getExchangeInstrumentID()));

            return ceData.getLTP() + peData.getLTP();

        } catch (Exception ex) {
            logger.warn("Failed to fetch current jodi premium for {} {} {}: {}",
                    underlying, expiry, strike, ex.getMessage());
            return 0.0;
        }
    }

    @Transactional
    public boolean optionSellingExit(Strategy strategy, Long signalId) {

        try {
            // 1. Manual Exit
            if (strategy.getManualExitType().equalsIgnoreCase(ManualExit.ENABLED.getKey())) {
                return true;
            }

            // 2. Global Strategy SL (if applicable)
            Double strategyProfitLoss = liveStrategyPNL.get(strategy.getId());
            if (strategyProfitLoss != null && strategyProfitLoss <= -MAX_STOP_LOSS_IN_HOUSE * strategy.getMultiplier()) {
                strategy.setManualExitType(ManualExit.ENABLED.getKey());
                strategyRepository.save(strategy);
                return true;
            }


            Hibernate.initialize(strategy.getExitDetails());
            ExitDetails exitDetails = strategy.getExitDetails();

            if (exitDetails == null){
                return false;
            }

            ZoneId zoneId = ZoneId.of("Asia/Kolkata");
            LocalDate today = LocalDate.now(zoneId);
            Instant exitTime = LocalDateTime.of(
                    today,
                    LocalTime.of(exitDetails.getExitHourTime(), exitDetails.getExitMinsTime())
            ).atZone(zoneId).toInstant();

            Instant now = Instant.now();
            if (now.isAfter(exitTime)) {
                return true;
            }

            return false;
        }
        catch (Exception e) {
            grpcErrorService.placingOrderLogs(
                    ERROR_PROCESSING_STRATEGY_DATA_NOT_FOUND,
                    null,
                    Status.ERROR.getKey()
            );
            logger.error("Error during OptionSelling exit logic: {}", e.getMessage(), e);
            return false;
        }
    }

    @Transactional
    public boolean optionBuyingExit(Strategy strategy, Long signalId) {

        try {
            // Manual Exit
            if (ManualExit.ENABLED.getKey()
                    .equalsIgnoreCase(strategy.getManualExitType())) {
                return true;
            }

            // Global Strategy SL
            Double strategyProfitLoss = liveStrategyPNL.get(strategy.getId());
            if (strategyProfitLoss != null && strategyProfitLoss <= -MAX_STOP_LOSS_IN_HOUSE * strategy.getMultiplier()) {
                strategy.setManualExitType(ManualExit.ENABLED.getKey());
                strategyRepository.save(strategy);
                return true;
            }


            // LEG STOPLOSS EXIT (BUY OPTION)
            Signal signal = signalRepository.findById(signalId).orElse(null);
            if (signal != null) {

                Hibernate.initialize(signal.getSignalLegs());

                for (StrategyLeg leg : signal.getSignalLegs()) {

                    // Only OPEN BUY legs
//                    if (!LegType.OPEN.getKey().equalsIgnoreCase(leg.getLegType())) continue;
//                    if (!LegSide.BUY.getKey().equalsIgnoreCase(leg.getBuySellFlag())) continue;

                    if (leg.getExecutedPrice() == null || leg.getExecutedPrice() <= 0) continue;
                    double executedPrice = leg.getExecutedPrice() / (double) AMOUNT_MULTIPLIER;

                    MarketData tick = touchLineService.getTouchLine(
                            String.valueOf(leg.getExchangeInstrumentId())
                    );

                    if (tick == null || tick.getLTP() <= 0) {
                        logger.warn("[{}] Invalid LTP for legId={} instrumentId={}",
                                strategy.getId(), leg.getId(), leg.getExchangeInstrumentId());
                        continue;
                    }

                    double ltp = tick.getLTP();
                    double slPercent = leg.getStopLossUnitValue();
                    double stoplossPrice = executedPrice * (1 - slPercent / 100.0);

                    if (ltp <= stoplossPrice) {
                        logger.info(
                                "[OptionBuying SL HIT] strategyId={} legId={} execPrice={} ltp={} slPrice={}",
                                strategy.getId(), leg.getId(), executedPrice, ltp, stoplossPrice
                        );
                        return true;
                    }
                }
            }

            // Time-based Exit
            Hibernate.initialize(strategy.getExitDetails());
            ExitDetails exitDetails = strategy.getExitDetails();
            if (exitDetails == null) return false;

            ZoneId zoneId = ZoneId.of("Asia/Kolkata");
            Instant exitTime = LocalDateTime.of(
                    LocalDate.now(zoneId),
                    LocalTime.of(exitDetails.getExitHourTime(), exitDetails.getExitMinsTime())
            ).atZone(zoneId).toInstant();

            return Instant.now().isAfter(exitTime);

        } catch (Exception e) {
            logger.error("Error during OptionBuying exit logic", e);
            return false;
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
