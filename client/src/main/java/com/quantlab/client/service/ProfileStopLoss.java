package com.quantlab.client.service;

import com.quantlab.client.websockets.OpenPositionsSocketHandler;
import com.quantlab.client.websockets.SocketDataProcessorService;
import com.quantlab.common.dto.ProfitLossStatsDao;
import com.quantlab.common.entity.AppUser;
import com.quantlab.common.entity.DeploymentErrors;
import com.quantlab.common.entity.Strategy;
import com.quantlab.common.repository.*;
import com.quantlab.common.utils.staticstore.dropdownutils.ExecutionTypeMenu;
import com.quantlab.common.utils.staticstore.dropdownutils.ManualExit;
import com.quantlab.common.utils.staticstore.dropdownutils.Status;
import com.quantlab.signal.dto.PNLHoldingDTO;
import com.quantlab.signal.service.redisService.TouchLineService;
import com.quantlab.signal.utils.CommonUtils;
import com.quantlab.signal.web.service.MarketDataFetch;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.core.parameters.P;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.*;
import java.util.concurrent.locks.ReentrantLock;

import static com.quantlab.client.websockets.PNLSocketUI.userUISocketDTO;
import static com.quantlab.common.utils.staticstore.AppConstants.AMOUNT_MULTIPLIER;
import static com.quantlab.common.utils.staticstore.AppConstants.DEFAULT_MIN_MAX_VALUE;

@Component
public class ProfileStopLoss {

    private static final Logger logger = LoggerFactory.getLogger(ProfileStopLoss.class);

    @Autowired
    StrategyRepository strategyRepository;

    @Autowired
    UserAuthConstantsRepository userAuthConstantsRepository;

    @Autowired
    AppUserRepository appUserRepository;

    @Autowired
    UserStrategyService strategyService;

    @Autowired
    DeploymentErrorsRepository deploymentErrorsRepository;

    @Autowired
    CommonUtils commonUtils;

    List<ProfitLossStatsDao> profileProfitLossStats = new CopyOnWriteArrayList<>();
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
    private final ConcurrentHashMap<Long, ReentrantLock> userLocks = new ConcurrentHashMap<>();

    @PostConstruct
    public void profileStopLossThread() {
        scheduler.scheduleWithFixedDelay(() -> {
            try {
                if (LocalTime.now().isAfter(LocalTime.of(9, 15)) &&
                        LocalTime.now().isBefore(LocalTime.of(15, 31))) {
                    exitBasedOnMinMaxValues();
                }
            } catch (Exception e) {
                logger.error("Error processing exitBasedOnMinMaxValues task: {}", e.getMessage());
            }
        }, 0, 1000, TimeUnit.MILLISECONDS);
    }

    @Transactional(readOnly = true)
    public void exitBasedOnMinMaxValues() {
        for (ProfitLossStatsDao userData : new ArrayList<>(profileProfitLossStats)) {
            try {
                Long userId = userData.getUserId();

                PNLHoldingDTO pnl = userUISocketDTO.get(userId.toString());
                if (pnl == null || pnl.getLiveHeaders() == null || pnl.getForwardHeaders() == null) {
                    SocketDataProcessorService.appUsers.add(userId.toString());
                    continue;
                }

                ReentrantLock lock = userLocks.computeIfAbsent(userId, k -> new ReentrantLock());

                CompletableFuture.runAsync(() -> {
                    try {
                        if (!lock.tryLock()) return;

                        long minProfit = Optional.ofNullable(userData.getMinProfit()).orElse(DEFAULT_MIN_MAX_VALUE);
                        long maxLoss = Optional.ofNullable(userData.getMaxLoss()).orElse(DEFAULT_MIN_MAX_VALUE);

                        double livePNL = pnl.getLiveHeaders().getTodaysPAndL();
                        double forwardPNL = pnl.getForwardHeaders().getTodaysPAndL();

                        boolean exitLive = false, exitForward = false;
                        String exitReason = null;

                        if (maxLoss != DEFAULT_MIN_MAX_VALUE) {
                            if (maxLoss <= -livePNL * AMOUNT_MULTIPLIER && hasLiveStrategies(userId, ExecutionTypeMenu.LIVE_TRADING.getKey()))
                                exitLive = true;
                            if (maxLoss <= -forwardPNL * AMOUNT_MULTIPLIER && hasLiveStrategies(userId, ExecutionTypeMenu.PAPER_TRADING.getKey()))
                                exitForward = true;
                            if (exitLive || exitForward)
                                exitReason = "Profile Max Loss: " + userData.getMaxLoss() / AMOUNT_MULTIPLIER + " reached";
                        }

                        if (minProfit != DEFAULT_MIN_MAX_VALUE && !exitLive && !exitForward) {
                            if (minProfit <= livePNL * AMOUNT_MULTIPLIER && hasLiveStrategies(userId, ExecutionTypeMenu.LIVE_TRADING.getKey()))
                                exitLive = true;
                            if (minProfit <= forwardPNL * AMOUNT_MULTIPLIER && hasLiveStrategies(userId, ExecutionTypeMenu.PAPER_TRADING.getKey()))
                                exitForward = true;
                            if (exitLive || exitForward)
                                exitReason = "Profile Min Profit: " + userData.getMinProfit() / AMOUNT_MULTIPLIER + " reached";
                        }

                        if (exitLive)
                            exitUserStrategies(userData, ExecutionTypeMenu.LIVE_TRADING.getKey(), exitReason);
                        if (exitForward)
                            exitUserStrategies(userData, ExecutionTypeMenu.PAPER_TRADING.getKey(), exitReason);

                    } catch (Exception e) {
                        logger.error("Error processing user {}: {}", userId, e.getMessage());
                    } finally {
                        lock.unlock();
                    }
                });
            } catch (Exception e) {
                logger.error("Error in exitBasedOnMinMaxValues: {}", e.getMessage(), e);
            }
        }
    }

    @Transactional(readOnly = true)
    public boolean hasLiveStrategies(long userId, String executionType) {
        Long count = strategyRepository.countStrategiesByUserIdStatusOrManualExitTypeAndExecutionType(userId, Status.LIVE.getKey(), ManualExit.ENABLED.getKey() , executionType);
        return count != null && count > 0;
    }

    private void exitUserStrategies(ProfitLossStatsDao userData, String executionMode, String exitReason) {
        logger.info("##### TRIGGERING EXIT_ALL FOR USER {} ExecutionMode:{}, DUE TO {}, ",userData.getUserId(), executionMode, exitReason);
            strategyService.exitAllByExecutionType(userData.getClientId(), executionMode);
            logExitRequest(exitReason, userData.getUserId(), executionMode);
            strategyService.changeActiveStrategiesToStandBy(userData.getUserId(), executionMode);
    }

    @Transactional
    public void logExitRequest(String exitReason, Long userId, String executionMode) {

        Optional<AppUser> appUsers = appUserRepository.findById(userId);
        if (appUsers.isPresent()) {
            List<Strategy> allLiveStrategies= strategyRepository.findByStatusInAndAppUserAndExecutionType(Collections.singletonList(Status.LIVE.getKey()), appUsers.get(), executionMode);

            List<DeploymentErrors> deploymentErrorsList = new ArrayList<>();
            for (Strategy strategy: allLiveStrategies) {
                DeploymentErrors deploymentErrors = new DeploymentErrors();
                deploymentErrors.setStatus(strategy.getStatus());
                deploymentErrors.setStrategy(strategy);
                deploymentErrors.setAppUser(strategy.getAppUser());
                deploymentErrors.setDeployedOn(Instant.now());
                deploymentErrors.setDescription(Collections.singletonList(exitReason));
                deploymentErrorsList.add(deploymentErrors);
            }
            deploymentErrorsRepository.saveAllAndFlush(deploymentErrorsList);
        }
    }


    @PostConstruct
    public void fetchUserProfileStopLoss() {
        scheduler.scheduleWithFixedDelay(() -> {
            try {
                if (commonUtils.shouldRunScheduler()) {
                    if (LocalTime.now().isAfter(LocalTime.of(9, 13)) && LocalTime.now().isBefore(LocalTime.of(15, 30))) {
                        profileProfitLossStats = new CopyOnWriteArrayList<>(userAuthConstantsRepository.getProfitLossStats());
                        return;
                    }
                }
                profileProfitLossStats = new CopyOnWriteArrayList<>();
            } catch (Exception e) {
                logger.error("Error in fetchUserProfileStopLoss task: {}", e.getMessage());
            }
        }, 0, 6000, TimeUnit.MILLISECONDS);
    }


}
