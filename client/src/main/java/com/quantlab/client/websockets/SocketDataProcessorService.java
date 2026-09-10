package com.quantlab.client.websockets;

import com.quantlab.client.dto.GreeksDTO;
import com.quantlab.common.dto.SignalPNLDTO;
import com.quantlab.common.dto.StrategyLegPNLDTO;
import com.quantlab.client.service.UserStrategyService;
import com.quantlab.common.dto.StrategyPNLDto;
import com.quantlab.common.dto.StrategyStatus;
import com.quantlab.common.repository.*;
import com.quantlab.common.utils.staticstore.IndexInstruments;
import com.quantlab.common.utils.staticstore.dropdownutils.*;
import com.quantlab.common.utils.staticstore.dropdownutils.Status;
import com.quantlab.common.utils.staticstore.dropdownutils.StrategyCategoryType;
import com.quantlab.signal.dto.*;
import com.quantlab.signal.dto.redisDto.MarketData;
import com.quantlab.signal.service.TradingDayRuntimeCleanupEvent;
import com.quantlab.signal.service.redisService.TouchLineService;
import com.quantlab.signal.utils.CommonUtils;
import com.quantlab.signal.web.service.MarketDataFetch;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

import static com.quantlab.common.utils.staticstore.AppConstants.*;
import static com.quantlab.signal.service.DiyStrategyService.liveStrategyPNL;
import static com.quantlab.signal.service.ExitPNL.strategyNonLivePNL;
import static com.quantlab.signal.utils.staticdata.StaticStore.roundToTwoDecimalPlaces;

@Service
@Transactional
public class SocketDataProcessorService {

    private static final Logger logger = LoggerFactory.getLogger(SocketDataProcessorService.class);

    @Autowired
    StrategyRepository strategyRepository;

    @Autowired
    SignalRepository signalRepository;

    @Autowired
    SignalAdditionsRepository signalAdditionsRepository;

    @Autowired
    AppUserRepository appUserRepository;

    @Autowired
    StrategyLegRepository strategyLegRepository;

    @Autowired
    MarketDataFetch marketDataFetch;

    @Autowired
    TouchLineService touchLineService;

    @Autowired
    UserStrategyService strategyService;

    @Autowired
    DeploymentErrorsRepository deploymentErrorsRepository;

    @Autowired
    PNLSocketUI pnlSocketUI;

    @Value("${threads.pool.Pnl}")
    int threads;

    @Autowired
    @Qualifier("finalPNL")
    private Executor finalPNL;

    DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(3);
    public static Set<String> subscribedAppUsers = ConcurrentHashMap.newKeySet();
    public static Set<String> appUsers = ConcurrentHashMap.newKeySet();
    public static ConcurrentHashMap<String, PNLHoldingDTO> userIdPositionHoldings = new ConcurrentHashMap<>();
    private volatile List<SignalPNLDTO> liveSignals = new ArrayList<>();
    Set<Long> changedSignalIds = Collections.synchronizedSet(new HashSet<>());
    Set<Long> modifiedSignalsUserIds = Collections.synchronizedSet(new HashSet<>());
    ConcurrentHashMap<Long, PNLHoldingDTO> nonLiveHeaders = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Long, Double> allIndexPrices = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Long, Double> allSyntheticIndexPrices = new ConcurrentHashMap<>();
    public static ConcurrentHashMap<Long, List<StrategyLegPNLDTO>> liveStrategyLegsMap = new ConcurrentHashMap<>();
    public static ConcurrentHashMap<Long, StrategyPNLDto> liveStrategyDetails = new ConcurrentHashMap<>();

    @PostConstruct
    public void startPNLSocket() {
        try {
            for (int i = 0; i < threads; i++) {
                scheduler.scheduleWithFixedDelay(() -> {
                    try {
                        LocalTime now = LocalTime.now();
                        if (now.isBefore(LocalTime.of(9, 13)) || now.isAfter(LocalTime.of(15, 30))) {
                            return;
                        }
                        fetchDTO();
                    } catch (Exception e) {
                        logger.error("Error in fetchDTO tick: {}", e.getMessage());
                    }
                }, 0, 200, TimeUnit.MILLISECONDS);
            }
        } catch (Exception e) {
            logger.error("Error starting processing: {}", e.getMessage());
        }
    }

    private void stopProcessing() {
        scheduler.shutdown();
    }

    @EventListener
    public void resetRuntimeStateForNewTradingDay(TradingDayRuntimeCleanupEvent event) {
        logger.info("Resetting websocket runtime P&L state for new trading day. cleanedAt={}, liveSignals={}, liveStrategyDetails={}, liveStrategyLegsMap={}, nonLiveHeaders={}, userIdPositionHoldings={}",
                event.cleanedAt(), liveSignals.size(), liveStrategyDetails.size(), liveStrategyLegsMap.size(),
                nonLiveHeaders.size(), userIdPositionHoldings.size());

        liveSignals = new ArrayList<>();
        changedSignalIds.clear();
        modifiedSignalsUserIds.clear();
        nonLiveHeaders.clear();
        allIndexPrices.clear();
        allSyntheticIndexPrices.clear();
        liveStrategyDetails.clear();
        liveStrategyLegsMap.clear();
        userIdPositionHoldings.clear();

        liveStrategyPNL.clear();
        strategyNonLivePNL.clear();

        logger.info("Websocket runtime P&L state reset completed for new trading day. liveSignals={}, liveStrategyDetails={}, liveStrategyLegsMap={}, nonLiveHeaders={}, userIdPositionHoldings={}",
                liveSignals.size(), liveStrategyDetails.size(), liveStrategyLegsMap.size(), nonLiveHeaders.size(),
                userIdPositionHoldings.size());
    }

    @Scheduled(cron = "0 30 15 ? * *")
    public void updateFinalProfitLoss(){
            logger.info("Different signals detected, updating final P&L for changed signals: {}, started at: {}", changedSignalIds.size(), ZonedDateTime.now(ZoneId.of("Asia/Kolkata")).toLocalDateTime().format(formatter));
            try {
                CompletableFuture.runAsync(() -> {
//                    setFinalPNL();
                    findUsersNonLiveSignalsPnL();
                });
            } catch (Exception e) {
                logger.error("error setFinalPNL: {}", e.getMessage());
            logger.info("completed updating final P&L for changed signals: {}, finished at: {}", changedSignalIds.size(), ZonedDateTime.now(ZoneId.of("Asia/Kolkata")).toLocalDateTime().format(formatter));
        }
    }

    @Scheduled(fixedRate = 1000)
    public void fetchLiveSignals(){
        if (LocalTime.now().isAfter(LocalTime.of(9, 15)) && LocalTime.now().isBefore(LocalTime.of(15, 30))) {
            try {
                Set<Long> allLiveSignals = signalRepository.findLiveSignalId(StrategyType.POSITIONAL.getKey(),
                        StrategyType.INTRADAY.getKey(), Status.LIVE.getKey());
                checkDifferentSignals(allLiveSignals);

                if (!allLiveSignals.isEmpty()) {
                    // Fetch all strategy details in one query
                    List<Object[]> strategyDetails = strategyRepository.findStrategyPNLDtoBySignalIds(allLiveSignals);
                    Map<Long, StrategyPNLDto> strategyDetailsMap = strategyDetails.stream()
                            .map(StrategyPNLDto::new)
                            .collect(Collectors.toMap(
                                    StrategyPNLDto::getSignalId,
                                    strategy -> strategy
                            ));

                    // Fetch all legs for all signals in one query
                    List<Object[]> allSignalLegs = strategyLegRepository.findLegsBySignalIdsAndStatus(allLiveSignals, LegType.OPEN.getKey());
                    Map<Long, List<StrategyLegPNLDTO>> signalLegsMap = mapToStrategyLegPNLDTOList(allSignalLegs).stream()
                            .collect(Collectors.groupingBy(
                                    StrategyLegPNLDTO::getSignalId,
                                    Collectors.toList()
                            ));

                    liveStrategyDetails.clear();
                    liveStrategyDetails.putAll(strategyDetailsMap);

                    liveStrategyLegsMap.clear();
                    liveStrategyLegsMap.putAll(signalLegsMap);
                } else{
                    liveStrategyDetails.clear();
                    liveStrategyLegsMap.clear();
                }
            } catch (Exception e) {
                logger.error("error fetchLiveSignals: {}", e.getMessage());
            }
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
    public void fetchDTO() {
        try {
            fetchAllIndexesPrice();
            String uniqueKey = ZonedDateTime.now(ZoneId.of("Asia/Kolkata")).toLocalDateTime().format(formatter);
            ConcurrentHashMap<Long, GreeksDTO> instrumentGreeksDTO = new ConcurrentHashMap<>();
            HashMap<String,HashMap<Long,StrategyLegTableDTO>> mappingLegsToUsers = new HashMap<>();
            List<SignalPNLDTO> allLiveSignals =  liveSignals;

            //remove the below line if you want to keep the unique key same for all the users and formatter on global level
            uniqueKey = uniqueKey + "-"+allLiveSignals.size();
            try { 
                for (SignalPNLDTO signalDTO : allLiveSignals) {
                    StrategyLegTableDTO strategyIdAndLegs = new StrategyLegTableDTO();
                    List<StrategyLegPNLDTO> signalLegs = liveStrategyLegsMap.getOrDefault(signalDTO.getId(), new ArrayList<>());
                    ArrayList<LegHoldingDTO> eachStrategyRows = new ArrayList<>();
                    double signalPAndL = 0.0;
                    StrategyPNLDto strategyDto = liveStrategyDetails.get(signalDTO.getId());
                    if (strategyDto == null)
                        continue;
                    strategyIdAndLegs.setStrategyId(signalDTO.getStrategyId());
                    Long indexPrice = fetchIndexPrice(signalDTO, strategyDto, strategyIdAndLegs);
                    strategyIdAndLegs.setExecutionType(signalDTO.getExecutionType());
                    strategyIdAndLegs.setPositionType(signalDTO.getPositionType());

                    for (StrategyLegPNLDTO strategyLeg : signalLegs) {
                        try {
                            if (LegType.OPEN.getKey().equalsIgnoreCase(strategyLeg.getLegType())) {
                                double legPAndL = 0.0;
                                if (VALID_LEG_FOR_PNL.contains(strategyLeg.getStatus())) {
                                    GreeksDTO ltpAndGreeks = instrumentGreeksDTO.get(strategyLeg.getExchangeInstrumentId());
                                    if (ltpAndGreeks == null) {
                                        ltpAndGreeks = fetchTouchlineBinaryResponseLTP(strategyLeg.getExchangeInstrumentId());
                                        if (ltpAndGreeks == null) {
//                                            logger.error("GreeksDTO is null for instrument ID: " + strategyLeg.getExchangeInstrumentId());
                                            continue;
                                        }
                                        instrumentGreeksDTO.put(strategyLeg.getExchangeInstrumentId(), ltpAndGreeks);
                                    }
                                    legPAndL = processPNL(strategyLeg, ltpAndGreeks.getCurrentLegLTP(), strategyLeg.getExecutedPrice());
                                    strategyLeg.setLtp((long) (ltpAndGreeks.getCurrentLegLTP() * AMOUNT_MULTIPLIER));
                                    strategyLeg.setProfitLoss((long) (legPAndL * AMOUNT_MULTIPLIER));
                                    strategyLeg.setCurrentIV((long) (ltpAndGreeks.getCurrentIV() * GREEK_MULTIPLIER));
                                    strategyLeg.setCurrentDelta((long) (ltpAndGreeks.getCurrentDelta() * GREEK_MULTIPLIER));
                                    strategyLeg.setLatestIndexPrice(indexPrice);
                                    LegHoldingDTO tableRow = new LegHoldingDTO(strategyLeg, ltpAndGreeks.getCurrentLegLTP(), signalDTO);
                                    eachStrategyRows.add(tableRow);
                                } else {
                                    legPAndL = strategyLeg.getProfitLoss() != null ? strategyLeg.getProfitLoss() / (double) AMOUNT_MULTIPLIER : 0.0;
                                }
                                signalPAndL = signalPAndL + (legPAndL);
                            }
                        } catch (Exception e) {
                            logger.error("P&L error in processing each leg: " + e.toString());
                        }
                    }
                    if (signalPAndL != 0.0) {
                        signalDTO.setProfitLoss((long) (signalPAndL * AMOUNT_MULTIPLIER));
                    }
                    strategyIdAndLegs.setStrategyStatus(strategyDto.getStatus());
                    double strategyPNLSum = sumStrategyNotLiveSignalsPNL(strategyDto.getId(), signalPAndL);
                    strategyIdAndLegs.setStrategyMTM(roundToTwoDecimalPlaces(strategyPNLSum));
                    strategyIdAndLegs.setData(eachStrategyRows);
                    updatedDataToEachUser(signalDTO, strategyDto, strategyIdAndLegs, mappingLegsToUsers);
                    liveStrategyPNL.put(strategyIdAndLegs.getStrategyId(), strategyIdAndLegs.getStrategyMTM());
                }
            } catch (Exception e) {
                logger.error("P&L error in processing each signal: " + e.toString());
            }

            mapToPNLHoldingDTO(mappingLegsToUsers, uniqueKey);
            String finalUniqueKey = uniqueKey;

            CompletableFuture.runAsync(() ->
                    pnlSocketUI.createUIDTOForEachUser(mappingLegsToUsers, finalUniqueKey));

        } catch (Exception e) {
            logger.error("error in P&L socket fetchdto "+e.toString());
        }
    }

    private void checkDifferentSignals(Set<Long> allLiveSignalIds) {

        if(!signalsRemainSame(allLiveSignalIds)){
            CompletableFuture.runAsync(this::updateFinalProfitLoss, finalPNL);
            liveSignals = fetchSignalDTOs();
        }
    }

    private void fetchAllIndexesPrice() {
        try {
            for (IndexInstruments indexInstruments : IndexInstruments.values()) {
                GreeksDTO greeksDTO = fetchTouchlineBinaryResponseLTP(indexInstruments.getLabel().longValue());
                if (greeksDTO != null)
                    allIndexPrices.put(indexInstruments.getLabel().longValue(), greeksDTO.getCurrentLegLTP());
            }

            for (IndexInstruments indexInstruments : IndexInstruments.values()) {
                Double syntheticPrice = marketDataFetch.getSyntheticPrice(null, indexInstruments.getKey());
                if (syntheticPrice != null)
                    allSyntheticIndexPrices.put(indexInstruments.getLabel().longValue(), syntheticPrice);
            }
        } catch (Exception e) {
            logger.error("P&L error in fetchAllIndexesPrice(): " + e.getMessage());
        }
    }

    public Long fetchIndexPrice(SignalPNLDTO signalDto, StrategyPNLDto strategyDTO, StrategyLegTableDTO strategyIdAndLegs) {
        Double ltp;
        String underlying = strategyDTO.getUnderlyingName();
        IndexInstruments instrument = IndexInstruments.fromKey(underlying);

        if (AtmType.SYNTHETIC_ATM.getKey().equalsIgnoreCase(strategyDTO.getAtmType()))
            ltp = allSyntheticIndexPrices.get(instrument.getLabel().longValue());
        else
            ltp = allIndexPrices.get(instrument.getLabel().longValue());

        if (ltp == null)
            return 0L;

        strategyIdAndLegs.setIndexCurrentPrice(ltp);
        signalDto.setLatestIndexPrice((long) (ltp*AMOUNT_MULTIPLIER));
        return (long) (ltp*AMOUNT_MULTIPLIER);
    }

    private double sumStrategyNotLiveSignalsPNL(Long strategyId, double signalPAndL) {
        Double strategyPNLSumLong = strategyNonLivePNL.get(strategyId);
        if (strategyPNLSumLong == null) {
            strategyPNLSumLong = 0.0;
        }
        return strategyPNLSumLong + signalPAndL;
    }

    public void mapToPNLHoldingDTO(HashMap<String, HashMap<Long, StrategyLegTableDTO>> mappingLegsToUsers, String uniqueKey) {
        for (String userId : appUsers) {
            HashMap<Long, StrategyLegTableDTO> eachUserStrategies = mappingLegsToUsers.get(userId);
            PNLHoldingDTO NonlivePNLHoldingDTO = nonLiveHeaders.get(Long.parseLong(userId));
            PNLHoldingDTO pnlHoldingDTO = new PNLHoldingDTO();
            if (NonlivePNLHoldingDTO == null){
                NonlivePNLHoldingDTO = fetchHeadersData(Long.valueOf(userId));
                nonLiveHeaders.put(Long.valueOf(userId),NonlivePNLHoldingDTO);
            }
            pnlHoldingDTO.setLiveHeaders(NonlivePNLHoldingDTO.getLiveHeaders());
            pnlHoldingDTO.setForwardHeaders(NonlivePNLHoldingDTO.getForwardHeaders());
            pnlHoldingDTO.setUserID(userId);
            pnlHoldingDTO.setDeployedCapital(NonlivePNLHoldingDTO.getDeployedCapital());
            pnlHoldingDTO.setPostionalPAndL(NonlivePNLHoldingDTO.getPostionalPAndL());
            pnlHoldingDTO.setIntradayPAndL(NonlivePNLHoldingDTO.getIntradayPAndL());
            pnlHoldingDTO.setTodaysPAndL(NonlivePNLHoldingDTO.getTodaysPAndL());
            pnlHoldingDTO.setOverAllUserPAndL(NonlivePNLHoldingDTO.getOverAllUserPAndL());

            if (eachUserStrategies != null) {
                HoldingsDTO holdingsDTO = new HoldingsDTO();
                ArrayList<StrategyLegTableDTO> valuesList = new ArrayList<>(eachUserStrategies.values());
                holdingsDTO.setStrategyLegs(valuesList);
                pnlHoldingDTO.setStrategyLegs(holdingsDTO.getStrategyLegs());
                //remove the below line if you want to keep the unique key same for all the users
                pnlHoldingDTO.setUniqueKey(uniqueKey);
            }
            userIdPositionHoldings.put(userId, pnlHoldingDTO);
        }
    }

    public void updatedDataToEachUser(SignalPNLDTO signalDto, StrategyPNLDto strategyDto,
                                      StrategyLegTableDTO strategyIdAndLegs,
                                      HashMap<String,HashMap<Long, StrategyLegTableDTO>> mappingLegsToUsers) {
        HashMap<Long,StrategyLegTableDTO> userStrategyLegTableDTOHashMap = new HashMap<>();

        if (!mappingLegsToUsers.containsKey(signalDto.getUserId().toString())){
            userStrategyLegTableDTOHashMap.put(strategyDto.getId(),strategyIdAndLegs);
            mappingLegsToUsers.put(signalDto.getUserId().toString(),userStrategyLegTableDTOHashMap);
        }else {
            userStrategyLegTableDTOHashMap = mappingLegsToUsers.get(signalDto.getUserId().toString());
            if (userStrategyLegTableDTOHashMap.containsKey(strategyDto.getId())){

                userStrategyLegTableDTOHashMap.get(strategyDto.getId()).getData()
                        .addAll(strategyIdAndLegs.getData());
            }
            else {
                userStrategyLegTableDTOHashMap.put(strategyDto.getId(),strategyIdAndLegs);
            }
        }
        mappingLegsToUsers.put(signalDto.getUserId().toString()
                ,userStrategyLegTableDTOHashMap);

    }


    private double processPNL(StrategyLegPNLDTO strategyLeg, double ltp, Long actualPrice) {
        if (actualPrice != null) {
            if (strategyLeg.getBuySellFlag().equalsIgnoreCase(LegSide.BUY.getKey()))
                return (ltp - (actualPrice / (double) AMOUNT_MULTIPLIER)) * strategyLeg.getFilledQuantity();
            else
                return ((actualPrice / (double) AMOUNT_MULTIPLIER) - ltp) * strategyLeg.getFilledQuantity();
        }
        return 0;
    }

    public GreeksDTO fetchTouchlineBinaryResponseLTP(Long instrumentId){
            GreeksDTO greeksDTO = new GreeksDTO();
        try {
             MarketData marketData = marketDataFetch.getInstrumentData(instrumentId);
            if (marketData != null) {
                greeksDTO.setCurrentLegLTP(marketData.getLTP());
                greeksDTO.setCurrentIV(marketData.getIV());
                greeksDTO.setCurrentDelta(marketData.getDelta());
            }else
                return null;
        }catch (Exception e){
            logger.error("error in P&L socket fetching the redisData for the instrumentId = "+instrumentId+e.getMessage());
        }
        return greeksDTO;
    }

    public PNLHoldingDTO fetchHeadersData(Long userId){
        PNLHoldingDTO liveHeaders =  fetchDataWhenUserHasNoSignalsLive(userId, ExecutionTypeMenu.LIVE_TRADING.getKey());
        PNLHoldingDTO forwardHeaders = fetchDataWhenUserHasNoSignalsLive(userId, ExecutionTypeMenu.PAPER_TRADING.getKey());
        PNLHoldingDTO pnlHoldingDTO = new PNLHoldingDTO();
        PNLHeaderDTO livePnlHeaderDTO =  createHeaders(liveHeaders);
        PNLHeaderDTO forwardPnlHeaderDTO = createHeaders(forwardHeaders);
        pnlHoldingDTO.setLiveHeaders(livePnlHeaderDTO);
        pnlHoldingDTO.setForwardHeaders(forwardPnlHeaderDTO);
        pnlHoldingDTO.setUserID(String.valueOf(userId));
        pnlHoldingDTO.setDeployedCapital(liveHeaders.getDeployedCapital() + forwardHeaders.getDeployedCapital());
        pnlHoldingDTO.setPostionalPAndL(liveHeaders.getPostionalPAndL() + forwardHeaders.getPostionalPAndL());
        pnlHoldingDTO.setIntradayPAndL(liveHeaders.getIntradayPAndL() + forwardHeaders.getIntradayPAndL());
        pnlHoldingDTO.setTodaysPAndL(liveHeaders.getTodaysPAndL() + forwardHeaders.getTodaysPAndL());
        pnlHoldingDTO.setOverAllUserPAndL(liveHeaders.getOverAllUserPAndL() + forwardHeaders.getOverAllUserPAndL());

        return pnlHoldingDTO;
    }

    public PNLHeaderDTO createHeaders(PNLHoldingDTO forwardHeaders) {
        PNLHeaderDTO pnlHeaderDTO = new PNLHeaderDTO();
        pnlHeaderDTO.setTodaysPAndL(forwardHeaders.getTodaysPAndL());
        pnlHeaderDTO.setOverAllUserPAndL(forwardHeaders.getOverAllUserPAndL());
        pnlHeaderDTO.setIntradayPAndL(forwardHeaders.getIntradayPAndL());
        pnlHeaderDTO.setPositionalPAndL(forwardHeaders.getPostionalPAndL());
        pnlHeaderDTO.setDeployedCapital(forwardHeaders.getDeployedCapital());
        return pnlHeaderDTO;
    }


    @Transactional(readOnly = true)
    public PNLHoldingDTO fetchDataWhenUserHasNoSignalsLive(Long userId, String executionType) {

        try {
            Map<String, Object> result = strategyRepository.fetchUserPnLAndCapital(userId, executionType, SubscriptionStatus.START.getKey());
            Long storeTodaysTotal = ((Number) result.get("todaysPnl")).longValue();
            Long overAll = ((Number) result.get("totalPnl")).longValue();
            long deployedCapital = ((Number) result.get("deployedCapital")).longValue();

            double todayPAndL = storeTodaysTotal / (double) AMOUNT_MULTIPLIER;
            double overUserPAndL = overAll / (double) AMOUNT_MULTIPLIER;
            deployedCapital = deployedCapital / AMOUNT_MULTIPLIER;
            double positionalPAndL = 0.0;
            double intraDayPAndL = 0.0;

            Long value = signalRepository.findByExecutionIntraDayPAndL(userId, StrategyType.INTRADAY.getKey(), SubscriptionStatus.START.getKey(), executionType);
            intraDayPAndL = value != null ? (value / (double) AMOUNT_MULTIPLIER) : 0.0;
            value = signalRepository.findByExecutionPositionalPAndL(userId, StrategyType.POSITIONAL.getKey(), Status.LIVE.getKey(), SubscriptionStatus.START.getKey(), executionType);
            positionalPAndL = value != null ? (value / (double) AMOUNT_MULTIPLIER) : 0.0;

            // converting the holdingsDTO to PNLHoldingDTO because we need to decrease size
            PNLHoldingDTO holdingsDTO = new PNLHoldingDTO();
            holdingsDTO.setTodaysPAndL(roundToTwoDecimalPlaces(todayPAndL)); //Today generated signal P&L
            holdingsDTO.setOverAllUserPAndL(roundToTwoDecimalPlaces(overUserPAndL)); //users all strategy signals generated P&L
            holdingsDTO.setIntradayPAndL(roundToTwoDecimalPlaces(intraDayPAndL));//strategy intraday P&L
            holdingsDTO.setPostionalPAndL(roundToTwoDecimalPlaces(positionalPAndL));//strategy positional P&L
            holdingsDTO.setDeployedCapital(roundToTwoDecimalPlaces(deployedCapital));
            return holdingsDTO;
        }catch (Exception e){
            logger.error("Error in fetchDataWhenUserHasNoSignalsLive when processing for user: "+userId+" ,"+e.getMessage());
        }
        return null;
    }

    public boolean signalsRemainSame(Set<Long> latestLiveSignals) {

        Set<Long> removed = liveSignals.stream()
                .map(SignalPNLDTO::getId).collect(Collectors.toSet());
        boolean same =  removed.equals(latestLiveSignals);

        removed.removeAll(latestLiveSignals);

        if (!removed.isEmpty()) {
            changedSignalIds.addAll(removed);
            modifiedSignalsUserIds.addAll(
                    liveSignals.stream()
                            .filter(signal -> removed.contains(signal.getId()))
                            .map(SignalPNLDTO::getUserId)
                            .collect(Collectors.toSet())
            );
        }
        return same;
    }


    @Transactional(readOnly = true)
    public void findUsersNonLiveSignalsPnL() {
        for (SignalPNLDTO signalPNLDTO : liveSignals) {
            try {
//                Long nonLivePNL = signalRepository.findTotalPNLByNonLiveSignalsToday(Status.LIVE.getKey(), signalPNLDTO.getStrategyId());
                Long nonLivePNL = strategyRepository.findTodayPNLById(signalPNLDTO.getStrategyId());
                strategyNonLivePNL.put(signalPNLDTO.getStrategyId(), nonLivePNL != null ? nonLivePNL / (double) AMOUNT_MULTIPLIER : 0);
            } catch (Exception e) {
                logger.error("error in P&L socket thrown in findUsersNonLiveSignalsPnL(): " + e.getMessage());
            }
        }
        for (Long userId : modifiedSignalsUserIds) {
            try {
                nonLiveHeaders.put(userId, fetchHeadersData(userId));
                modifiedSignalsUserIds.remove(String.valueOf(userId));
            } catch (Exception e) {
                logger.error("error in P&L socket thrown in findUsersNonLiveSignalsPnL() while fetching headers: " + e.getMessage());
            }
        }
    }

    public List<SignalPNLDTO> fetchSignalDTOs() {
        List<Object[]> rows = signalRepository.findSignalDetails(StrategyType.POSITIONAL.getKey(), StrategyType.INTRADAY.getKey(), Status.LIVE.getKey());
        return rows.stream()
                .map(r -> new SignalPNLDTO(
                        ((Number) r[0]).longValue(),
                        ((Number) r[1]).longValue(),
                        r[2] != null? ((Number) r[2]).longValue() :0,
                        ((Number) r[3]).longValue(),
                        r[4] != null? ((Number) r[4]).longValue() :0,
                        r[5] != null? ((Number) r[5]).longValue() :0,
                        r[6] != null? ((Number) r[6]).longValue() :0,
                        r[7] != null? r[7].toString() : "",
                        r[8] != null? ((String) r[8]) :StrategyType.INTRADAY.getKey()))
                .collect(Collectors.toList());
    }

    public List<StrategyLegPNLDTO> mapToStrategyLegPNLDTOList(List<Object[]> results) {
        return results.stream()
                .map(row -> new StrategyLegPNLDTO(
                        getLong(row[0]),  // id
                        getLong(row[1]),  // ltp
                        getLong(row[2]),  // profitLoss
                        getLong(row[3]),  // currentIV
                        getLong(row[4]),  // currentDelta
                        (String) row[5],  // buySellFlag
                        getLong(row[6]),  // filledQuantity
                        getLong(row[7]),  // price
                        (String) row[8],  // name
                        (String) row[9],  // status
                        (String) row[10], // legType
                        getLong(row[11]), // lotSize
                        getLong(row[12]), // noOfLots
                        getLong(row[13]), // signalId
                        getLong(row[14]), // exchangeInstrumentId
                        getLong(row[15]), // executedPrice
                        getLong(row[16]), // constantIV
                        getLong(row[17]), // constantDelta
                        getLong(row[18]), // latestIndexPrice
                        getLong(row[19])  // baseIndexPrice
                ))
                .collect(Collectors.toList());
    }

    private Long getLong(Object obj) {
        return obj != null ? ((Number) obj).longValue() : null;
    }

    private Map<Long, Long> mapToInstrumentIdAndPNL(List<Object[]> results ){
        return results.stream()
                .collect(Collectors.toMap(
                        row -> (Long) row[0],
                        row -> (Long) row[1],
                        (existing, replacement) -> replacement
                ));
    }
}
