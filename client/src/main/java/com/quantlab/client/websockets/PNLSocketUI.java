package com.quantlab.client.websockets;

import com.quantlab.common.repository.SignalRepository;
import com.quantlab.common.repository.StrategyRepository;
import com.quantlab.common.utils.staticstore.dropdownutils.ExecutionTypeMenu;
import com.quantlab.common.utils.staticstore.dropdownutils.Status;
import com.quantlab.common.utils.staticstore.dropdownutils.StrategyType;
import com.quantlab.common.utils.staticstore.dropdownutils.SubscriptionStatus;
import com.quantlab.signal.dto.PNLHeaderDTO;
import com.quantlab.signal.dto.PNLHoldingDTO;
import com.quantlab.signal.dto.StrategyLegTableDTO;
import com.quantlab.signal.service.redisService.PNLRedisRepository;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import static com.quantlab.client.websockets.OpenPositionsSocketHandler.userSessions;
import static com.quantlab.client.websockets.SocketDataProcessorService.*;
import static com.quantlab.common.utils.staticstore.AppConstants.AMOUNT_MULTIPLIER;
import static com.quantlab.signal.service.ExitPNL.strategyNonLivePNL;
import static com.quantlab.signal.utils.staticdata.StaticStore.roundToTwoDecimalPlaces;

@Service
public class PNLSocketUI {
    private static final Logger logger = LoggerFactory.getLogger(PNLSocketUI.class);

    @Autowired
    StrategyRepository strategyRepository;

    @Autowired
    SignalRepository signalRepository;

    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(4);
    int strategyStatusThreads = 0;
    OpenPositionsSocketHandler openPositionsSocketHandler = new OpenPositionsSocketHandler();
    DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    public static final Map<String, PNLHoldingDTO> userUISocketDTO = Collections.synchronizedMap(new HashMap<>());
    // users whose PNL data needs a DB refresh (e.g. strategy added/removed off-market)
    public static final Set<String> dirtyUsers = ConcurrentHashMap.newKeySet();

    @Autowired
    private PNLRedisRepository pnlRedisRepository;

    @PostConstruct
    public void socketUIThread() {
        try {
            if (strategyStatusThreads < 4) {
                Runnable task = () -> {
                    try {
                        if (LocalTime.now().isAfter(LocalTime.of(9, 15)) &&
                                LocalTime.now().isBefore(LocalTime.of(15, 31))) {
                            sendProcessedPNLDTOsToUI(true);
                        } else {
                            sendProcessedPNLDTOsToUI(false);
                        }
                    } catch (Exception e) {
                        logger.error("Error processing sendStrategyStatus task: {}", e.getMessage());
                    }
                };

                // daytime schedule (every 250ms)
                scheduler.scheduleWithFixedDelay(() -> {
                    if (LocalTime.now().isAfter(LocalTime.of(9, 15)) &&
                            LocalTime.now().isBefore(LocalTime.of(15, 31))) {
                        task.run();
                    }
                }, 0, 250, TimeUnit.MILLISECONDS);

                // off-market schedule (every 1000ms)
                scheduler.scheduleWithFixedDelay(() -> {
                    if (!(LocalTime.now().isAfter(LocalTime.of(9, 15)) &&
                            LocalTime.now().isBefore(LocalTime.of(15, 31)))) {
                        task.run();
                    }
                }, 0, 2000, TimeUnit.MILLISECONDS);

                strategyStatusThreads++;
            }
        } catch (Exception e) {
            logger.error("Error in P&L socket startStrategyStatusProcessing: {}", e.getMessage());
        }
    }

    public void sendProcessedPNLDTOsToUI(Boolean liveMarket) {

        subscribedAppUsers.parallelStream().forEach(userId -> {
            try {
                PNLHoldingDTO holdingsDTO = userUISocketDTO.get(userId);

                if (holdingsDTO != null && liveMarket) {
                    //for testing purpose we are adding unique key to the holdingsDTO
                    String uniqueKey = holdingsDTO.getUniqueKey();
                    holdingsDTO.setUniqueKey(uniqueKey + "-" + ZonedDateTime.now(ZoneId.of("Asia/Kolkata")).toLocalDateTime().format(formatter));
                    uniqueKey = holdingsDTO.getUniqueKey();
                    holdingsDTO.setUniqueKey(uniqueKey + " -## " + userSessions.size() + "-");
                    //remove till here
                } else if (holdingsDTO != null && !liveMarket && !dirtyUsers.contains(userId)) {
                    // off-market: reuse cached data, no DB call
                } else if (holdingsDTO != null && !liveMarket && dirtyUsers.remove(userId)) {
                    // off-market but user data changed: refresh from DB once
                    holdingsDTO = fetchHeadersData(Long.valueOf(userId));
                    userUISocketDTO.put(userId, holdingsDTO);
                } else {
                    holdingsDTO = fetchHeadersData(Long.valueOf(userId));
                    userUISocketDTO.put(userId, holdingsDTO);
                }
                Map<String, Object> wrapper = new HashMap<>();
                wrapper.put("type", "OPEN_POSITIONS");
                wrapper.put("data", holdingsDTO);

                openPositionsSocketHandler.sendOpenPositionsToUser(holdingsDTO.getUserID(), wrapper);
            } catch (Exception e) {
                logger.error("error in P&L socket thrown in sendProcessedDTO(): " + e.getMessage());
//            e.printStackTrace();
            }
        });
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

    public PNLHeaderDTO createHeaders(PNLHoldingDTO forwardHeaders) {
        PNLHeaderDTO pnlHeaderDTO = new PNLHeaderDTO();
        pnlHeaderDTO.setTodaysPAndL(forwardHeaders.getTodaysPAndL());
        pnlHeaderDTO.setOverAllUserPAndL(forwardHeaders.getOverAllUserPAndL());
        pnlHeaderDTO.setIntradayPAndL(forwardHeaders.getIntradayPAndL());
        pnlHeaderDTO.setPositionalPAndL(forwardHeaders.getPostionalPAndL());
        pnlHeaderDTO.setDeployedCapital(forwardHeaders.getDeployedCapital());
        return pnlHeaderDTO;
    }

    public synchronized void createUIDTOForEachUser(HashMap<String, HashMap<Long, StrategyLegTableDTO>> mappingLegsToUsers, String uniqueKey) {

        synchronized (userUISocketDTO) {
            userUISocketDTO.keySet().removeIf(k -> !mappingLegsToUsers.containsKey(k));
        }

        for (Map.Entry<String, HashMap<Long, StrategyLegTableDTO>> userEntry : mappingLegsToUsers.entrySet()) {
            try {
                String userId = userEntry.getKey();

                if (userIdPositionHoldings.get(userId) == null) {
                    appUsers.add(userId);
                    continue;
                }
                HashMap<Long, StrategyLegTableDTO> strategies = userEntry.getValue();
                PNLHoldingDTO pnlHoldingDTO = new PNLHoldingDTO(userIdPositionHoldings.get(userId));

                synchronized (this) {
                    PNLHeaderDTO liveHeaders = new PNLHeaderDTO(pnlHoldingDTO.getLiveHeaders());
                    PNLHeaderDTO forwardHeaders = new PNLHeaderDTO(pnlHoldingDTO.getForwardHeaders());

                    double liveTodaysPNL = assignValueOrZero(liveHeaders.getTodaysPAndL());
                    double forwardTodaysPNL = assignValueOrZero(forwardHeaders.getTodaysPAndL());
                    double liveOverallPNL = assignValueOrZero(liveHeaders.getOverAllUserPAndL());
                    double liveIntradayPNL = assignValueOrZero(liveHeaders.getIntradayPAndL());
                    double livePositionalPNL = assignValueOrZero(liveHeaders.getPositionalPAndL());
                    double forwardOverallPNL = assignValueOrZero(forwardHeaders.getOverAllUserPAndL());
                    double forwardIntradayPNL = assignValueOrZero(forwardHeaders.getIntradayPAndL());
                    double forwardPositionalPNL = assignValueOrZero(forwardHeaders.getPositionalPAndL());

                    if (strategies != null) {
                        for (StrategyLegTableDTO strategy : strategies.values()) {
                            double pnl = strategyNonLivePNL.getOrDefault(strategy.getStrategyId(), 0.0);
                            pnl = strategy.getStrategyMTM() - pnl;

                            if (strategy.getExecutionType() != null) {
                                if (strategy.getExecutionType().equalsIgnoreCase(ExecutionTypeMenu.LIVE_TRADING.getKey())) {
                                    liveTodaysPNL += pnl;
                                    liveOverallPNL += strategy.getStrategyMTM();
                                    if (strategy.getPositionType() != null) {
                                        if (strategy.getPositionType().equalsIgnoreCase(StrategyType.INTRADAY.getKey())) {
                                            liveIntradayPNL += pnl;
                                        } else if (strategy.getPositionType().equalsIgnoreCase(StrategyType.POSITIONAL.getKey())) {
                                            livePositionalPNL += pnl;
                                        }
                                    }
                                } else if (strategy.getExecutionType().equalsIgnoreCase(ExecutionTypeMenu.PAPER_TRADING.getKey())) {
                                    forwardTodaysPNL += pnl;
                                    forwardOverallPNL += strategy.getStrategyMTM();
                                    if (strategy.getPositionType() != null) {
                                        if (strategy.getPositionType().equalsIgnoreCase(StrategyType.INTRADAY.getKey())) {
                                            forwardIntradayPNL += pnl;
                                        } else if (strategy.getPositionType().equalsIgnoreCase(StrategyType.POSITIONAL.getKey())) {
                                            forwardPositionalPNL += pnl;
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // Update the headers atomically with final calculated values
                    synchronized (liveHeaders) {
                        liveHeaders.setTodaysPAndL(liveTodaysPNL);
                        liveHeaders.setOverAllUserPAndL(liveOverallPNL);
                        liveHeaders.setIntradayPAndL(liveIntradayPNL);
                        liveHeaders.setPositionalPAndL(livePositionalPNL);
                    }

                    synchronized (forwardHeaders) {
                        forwardHeaders.setTodaysPAndL(forwardTodaysPNL);
                        forwardHeaders.setOverAllUserPAndL(forwardOverallPNL);
                        forwardHeaders.setIntradayPAndL(forwardIntradayPNL);
                        forwardHeaders.setPositionalPAndL(forwardPositionalPNL);
                    }

                    // Set strategies to headers
                    if (pnlHoldingDTO != null) {
                        synchronized (pnlHoldingDTO) {
                            if (pnlHoldingDTO.getLiveHeaders() != null) {
                                liveHeaders.setDeployedCapital(pnlHoldingDTO.getLiveHeaders().getDeployedCapital());
                                forwardHeaders.setDeployedCapital(pnlHoldingDTO.getForwardHeaders().getDeployedCapital());
                                pnlHoldingDTO.setLiveHeaders(liveHeaders);
                                pnlHoldingDTO.setForwardHeaders(forwardHeaders);
                                pnlHoldingDTO.setTodaysPAndL(liveHeaders.getTodaysPAndL() + forwardHeaders.getTodaysPAndL());
                            }
                            pnlHoldingDTO.setUniqueKey(uniqueKey);
                            pnlHoldingDTO.setUserID(userId);

                            userUISocketDTO.put(userId, pnlHoldingDTO);
//                            try {
//                                if (pnlHoldingDTO != null && pnlRedisRepository != null) {
//                                    pnlRedisRepository.savePNL(userId, pnlHoldingDTO);
//                                }
//                            } catch (Exception ex) {
//                                logger.error("Error saving PNL to Redis for user {}: {}", userId, ex.getMessage());
//                            }
                        }
                    }
                }
            } catch (Exception e) {
                logger.error("error in P&L socket createUIDTOForEachUser(): {}", e.getMessage());
            }
        }
    }

    private double assignValueOrZero(Double overAllUserPAndL) {
        double value = overAllUserPAndL != null ? overAllUserPAndL : 0.0;
        return Math.round(value * 100.0) / 100.0;
    }


}
