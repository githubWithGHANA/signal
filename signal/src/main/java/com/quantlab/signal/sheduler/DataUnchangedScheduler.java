package com.quantlab.signal.sheduler;
import com.quantlab.common.emailService.EmailService;
import com.quantlab.common.entity.Order;
import com.quantlab.common.entity.Signal;
import com.quantlab.common.entity.Strategy;
import com.quantlab.common.entity.StrategyLeg;
import com.quantlab.common.repository.OrderRepository;
import com.quantlab.common.repository.SignalRepository;
import com.quantlab.common.repository.StrategyLegRepository;
import com.quantlab.common.repository.StrategyRepository;
import com.quantlab.common.utils.staticstore.dropdownutils.Status;
import com.quantlab.signal.dto.redisDto.MarketData;
import com.quantlab.signal.service.OrderCommonService;
import com.quantlab.signal.service.redisService.TouchLineRepository;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

import static com.quantlab.common.utils.staticstore.AppConstants.*;

@Component
public class DataUnchangedScheduler {

    private static final Logger logger = LogManager.getLogger(DataUnchangedScheduler.class);

    @Autowired
    BodSchedule bodSchedule;

    @Autowired
    StrategyRepository strategyRepository;

    @Autowired
    StrategyLegRepository strategyLegRepository;

    @Autowired
    SignalRepository signalRepository;

    @Autowired
    OrderRepository orderRepository;

    @Autowired
    OrderCommonService orderCommonService;

    private final TouchLineRepository touchLineRepository;
    private final EmailService emailService;

    private Double lastSeenLTP = null;
    private Long lastSeenLut = null;
    private List<Long> cachedPendingStrategyIds = new ArrayList<>();
    LocalTime startTime = LocalTime.of(9, 16);
    LocalTime endTime = LocalTime.of(15, 30);
    private volatile ExecutorService executor = Executors.newFixedThreadPool(5);

    private ScheduledExecutorService scheduledTrigger;
    private final AtomicBoolean pendingCheckRunning = new AtomicBoolean(false);


    @Autowired
    public DataUnchangedScheduler(TouchLineRepository touchLineRepository, EmailService emailService) {
        this.touchLineRepository = touchLineRepository;
        this.emailService = emailService;
    }

    @PostConstruct
    public void processPendingStrategyScheduler() {

        scheduledTrigger = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "pending-strategy-scheduler-thread");
            t.setDaemon(true);
            return t;
        });

        scheduledTrigger.scheduleAtFixedRate(() -> {
            try {
                // Skip this run if the previous is still executing.
                if (!pendingCheckRunning.compareAndSet(false, true)) {
                    logger.info("Previous pending-strategies check still running; skipping this scheduled trigger.");
                    return;
                }

                // Submit actual work to the worker executor so the scheduler thread is never blocked.
                CompletableFuture.runAsync(() -> {
                    try {
                        checkPendingStrategiesStatus();
                    } catch (Throwable th) {
                        logger.error("Unhandled error inside checkPendingStrategiesStatus()", th);
                    } finally {
                        pendingCheckRunning.set(false);
                    }
                }, getExecutor());
            } catch (Throwable t) {
                logger.error("Error while triggering checkPendingStrategiesStatus()", t);
            }
        }, 0L, 100L, TimeUnit.SECONDS);
    }

    @Scheduled(fixedRate = 60000)
    public void monitorTouchline() {

        LocalTime now = LocalTime.now();
        LocalTime startTime = LocalTime.of(9, 16);
        LocalTime endTime = LocalTime.of(15, 30);

        if (now.isBefore(startTime) || now.isAfter(endTime)) {
            return;
        }

        if (!bodSchedule.shouldRunScheduler()) {
            return;
        }

        MarketData currentData = getNiftyMarketData();
        if (currentData == null) {
            emailService.sendEmailAlertForNullData("MD_26000");
            return;
        }

        logger.info("NIFTY Touchline LTP: {}, LastTradedTime: {}", currentData.getLTP(), currentData.getLut());

        if (lastSeenLTP == null || lastSeenLut == null) {
            lastSeenLTP = currentData.getLTP();
            lastSeenLut = currentData.getLut();
            logger.info("Initialized last seen LTP and traded time.");
            return;
        }

        if (currentData.getLTP() == lastSeenLTP && currentData.getLut() == lastSeenLut) {
            emailService.sendEmailAlert(currentData.getLTP(), currentData.getLut());
        } else {
            lastSeenLTP = currentData.getLTP();
            lastSeenLut = currentData.getLut();
        }
    }

    private MarketData getNiftyMarketData() {
        try {
            MarketData data = touchLineRepository.find("MD_26000");
            if (data == null) throw new Exception("MarketData not found");
            return data;
        } catch (Exception e) {
            logger.error("Error fetching NIFTY MarketData", e);
            return null;
        }
    }

    @Scheduled(fixedRate = 300000)
    @Transactional
    public void monitorPendingStrategies() {
        LocalTime now = LocalTime.now();
        LocalTime startTime = LocalTime.of(9, 16);
        LocalTime endTime = LocalTime.of(15, 30);

        if (now.isBefore(startTime) || now.isAfter(endTime)) {return;}
        if (!bodSchedule.shouldRunScheduler()) { return; }

        List<Long> pendingStrategyIds = strategyRepository.getStrategyIdsByStatuses(FETCH_PENDING_STRATEGIES_BY_STATUS);
        if (pendingStrategyIds.isEmpty()) {
            return;
        }

        if(cachedPendingStrategyIds.isEmpty()) {
            cachedPendingStrategyIds = pendingStrategyIds;
            return;
        }
        
        List<Long> previousPendingStrategies = pendingStrategyIds.stream()
                .filter(cachedPendingStrategyIds::contains)
                .toList();

        for (Long strategyId : previousPendingStrategies) {
            try {
                logger.info("Strategy ID {} has been pending for multiple checks.", strategyId);
                Strategy strategy = strategyRepository.findById(strategyId).orElseThrow(() -> new Exception("Strategy not found"));
                emailService.sendStrategyErrorEmail(strategy, PENDING_ERROR_MAIL_SUBJECT);
            } catch (Exception e) {
                logger.error("Error sending mail for pending strategy ID: {}, error: {}", strategyId, e);
            }
        }

        cachedPendingStrategyIds = pendingStrategyIds;
    }

    public void checkPendingStrategiesStatus() {
        LocalTime now = LocalTime.now();
//        if (!bodSchedule.shouldRunScheduler()) { return;}
        if (now.isBefore(startTime) || now.isAfter(endTime)) {return;}

        try {
            List<Long> pendingStrategyIds = strategyRepository.getStrategyIdsByStatuses(FETCH_PENDING_STRATEGIES_BY_STATUS);
            if (pendingStrategyIds.isEmpty()) {
                return;
            }
            logger.info("Checking status for pending strategies {}.", pendingStrategyIds);

            List<CompletableFuture<Void>> futures = pendingStrategyIds.stream()
                    .map(strategyId -> CompletableFuture.runAsync(() -> {
                        try {
                            Strategy strategy = strategyRepository.findById(strategyId)
                                    .orElseThrow(() -> new Exception("Strategy not found"));
                            checkOrderStatusAndNotify(strategy);
                        } catch (Exception e) {
                            logger.error("Error processing pending strategy ID: {}, error: {}", strategyId, e.getMessage());
                        }
                    }, getExecutor()))
                    .toList();

            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();


        } catch (Exception e) {
            logger.error("Error checking pending strategies status: {}", e.getMessage());
        }
    }

    @Transactional
    public void checkOrderStatusAndNotify(Strategy strategy) {
        try {
            Long signalId = signalRepository.findTopIdByStrategyIdOrderByCreatedAtDesc(strategy.getId());
            if (signalId == null) {
                logger.warn("No signals found for strategy ID: {}", strategy.getId());
                return;
            }
            Long strategyLegsCount = strategyLegRepository.countBySignalId(signalId);

            if (strategyLegsCount == 0) {
                logger.warn("No strategy legs found for signal ID: {}", signalId);
                return;
            }
            Long orderCount = orderRepository.countBySignalId(signalId);
            if (orderCount < strategyLegsCount) {
                logger.warn("Order count {} is less than strategy legs count {} for strategyId : {}, signal ID: {}", orderCount, strategyLegsCount,strategy.getId(), signalId);
                return;
            }

            Optional<Signal> signalOpt = signalRepository.findByID(signalId);
            if (signalOpt.isEmpty()) {
                logger.warn("No signals found for strategy ID: {}", strategy.getId());
                return;
            }
            processPendingStatusStrategy(signalOpt.get(), strategy);
        } catch (Exception e) {
            logger.error("Error processing pending strategy ID: {}, error: {}", strategy.getId(), e);
        }
    }

    @Transactional
    public void processPendingStatusStrategy(Signal orderSignal, Strategy strategy) {
        logger.info("Processing pending status for strategyId: {},  signalID: {}",orderSignal.getStrategy().getId(), orderSignal.getId());
        boolean finishedAllOrders = true;
        List<StrategyLeg> strategyLegs = strategyLegRepository.findBySignalId(orderSignal.getId());
        for (StrategyLeg leg : strategyLegs) {
            if ((leg.getStatus().equalsIgnoreCase(Status.LIVE.getKey()) ||
                    leg.getStatus().equalsIgnoreCase(Status.EXIT.getKey()))) {
                logger.info("Skipping leg leg identifier: {} with status: {} ", leg.getLegIdentifier(), leg.getStatus());
                continue;
            }
            Optional<Order> order = orderRepository.findByOrderUniqueIdentifier(leg.getLegIdentifier());
            if (order.isEmpty()) {
                logger.info("No order found for strategyId: {},  signalID: {} leg identifier: {}, leg status:{}"
                        ,orderSignal.getStrategy().getId(), orderSignal.getId(), leg.getLegIdentifier(), leg.getStatus());
                return;
            }
            finishedAllOrders = false;
            logger.info("Processing leg identifier: {} with status: {} ", leg.getLegIdentifier(), leg.getStatus());
            orderCommonService.processStrategyBasedOnOrder(order.get(), leg, orderSignal);
        }
        if (finishedAllOrders) {
            logger.info("All legs are either Live/Exit for strategyId: {},  signalID: {}. Processing strategy status.",strategy.getId(), orderSignal.getId());
            processPendingStrategyStatusAfterNoPendingLegs(strategy, orderSignal);
        }
    }

    private ExecutorService getExecutor() {
        if (executor == null || executor.isShutdown() || executor.isTerminated()) {
            synchronized (this) {
                if (executor == null || executor.isShutdown() || executor.isTerminated()) {
                    executor = Executors.newFixedThreadPool(5);
                }
            }
        }
        return executor;
    }

    @PreDestroy
    public void shutdownScheduledTrigger() {
        try {
            if (scheduledTrigger != null && !scheduledTrigger.isShutdown()) {
                scheduledTrigger.shutdownNow();
                logger.info("DataUnchangedScheduler scheduledTrigger shutdown at bean destroy.");
            }
        } catch (Exception e) {
            logger.warn("Error shutting down scheduledTrigger: {}", e.getMessage());
        }
    }

    @PreDestroy
    public void shutdownExecutor() {
        try {
            if (executor != null && !executor.isShutdown()) {
                executor.shutdownNow();
                logger.info("DataUnchangedScheduler executor shutdown at bean destroy.");
            }
        } catch (Exception e) {
            logger.warn("Error shutting down executor: {}", e.getMessage());
        }
    }


    @Transactional
    public void processPendingStrategyStatusAfterNoPendingLegs(Strategy strategy, Signal orderSignal) {

        String status = orderCommonService.checkStrategyStatus(orderSignal);

        if (status.equalsIgnoreCase(Status.LIVE.getKey())) {
            orderSignal.setStatus(Status.LIVE.getKey());
            signalRepository.updateSignalStatus(orderSignal.getId(), Status.LIVE.getKey());
            logger.info("updated signal to Live of strategyID {}, to status: {} ",strategy.getId(), status);
        }
        else if (Status.EXIT.getKey().equalsIgnoreCase(status)) {
            orderSignal.setStatus(Status.EXIT.getKey());
            logger.info("updated signal to Exit of strategyID {}, to status: {} ",strategy.getId(), status);
            signalRepository.updateSignalStatus(orderSignal.getId(), Status.EXIT.getKey());
        }
        logger.info("processStrategyBasedOnOrder: successfullyFilled  strategyLeg id :{}, strategy status: {}. ",strategy.getId(), status);
        strategy.setStatus(status);
        strategyRepository.updateStrategyStatus(strategy.getId(), strategy.getStatus());
    }
 }
