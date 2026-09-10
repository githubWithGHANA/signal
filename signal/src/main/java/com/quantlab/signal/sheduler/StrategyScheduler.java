package com.quantlab.signal.sheduler;

import com.quantlab.common.entity.Strategy;
import com.quantlab.common.repository.StrategyRepository;
import com.quantlab.common.utils.staticstore.dropdownutils.ExecutionTypeMenu;
import com.quantlab.signal.strategy.driver.Parser;
import com.quantlab.signal.utils.CommonUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.DayOfWeek;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

import static com.quantlab.common.utils.staticstore.AppConstants.FETCH_STRATEGIES_BY_STATUS;
import static com.quantlab.common.utils.staticstore.dropdownutils.StrategyType.POSITIONAL;

@Service
public class StrategyScheduler {

    private static final Logger logger = LogManager.getLogger(StrategyScheduler.class);

    @Autowired
    private final StrategyRepository strategyRepository;

    @Autowired
    private final CommonUtils commonUtils;

    private final Parser parser;

    // Cached strategies snapshot
    private volatile List<Strategy> paperStrategyCache = List.of();

    // Running flag to avoid overlapping runs
    private final AtomicBoolean running = new AtomicBoolean(false);

    private final ExecutorService priorityExecutor = Executors.newSingleThreadExecutor();

    // Custom executor for strategy tasks - bounded to avoid unbounded queueing when there are many strategies
    private final ThreadPoolExecutor taskExecutor = new ThreadPoolExecutor(
            Math.max(2, Runtime.getRuntime().availableProcessors()),
            Math.max(2, Runtime.getRuntime().availableProcessors() * 2),
            60L, TimeUnit.SECONDS,
            new LinkedBlockingQueue<>(500),
            new ThreadPoolExecutor.CallerRunsPolicy() // provide backpressure by running task in caller thread when queue is full
    );

    private static final int DB_CONCURRENCY = 40; // adjust as needed
    private static final int DB_QUEUE_CAPACITY = 5000; // max queued tasks waiting for DB worker
    private final BlockingQueue<Runnable> dbQueue;
    private final ThreadPoolExecutor dbExecutor;

    private static final int DEFAULT_BATCH_SIZE = 200;

    public StrategyScheduler(StrategyRepository strategyRepository, Parser parser, CommonUtils commonUtils) {
        this.strategyRepository = strategyRepository;
        this.parser = parser;
        this.commonUtils = commonUtils;

        this.dbQueue = new LinkedBlockingQueue<>(DB_QUEUE_CAPACITY);
        this.dbExecutor = new ThreadPoolExecutor(DB_CONCURRENCY, DB_CONCURRENCY,
                60L, TimeUnit.SECONDS, this.dbQueue);
        this.dbExecutor.allowCoreThreadTimeOut(true);
        // When queue is full, block the submitting thread until space becomes available (so nothing is dropped)
        this.dbExecutor.setRejectedExecutionHandler((r, executor) -> {
            try {
                executor.getQueue().put(r);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RejectedExecutionException("Interrupted while enqueuing DB task", e);
            }
        });
    }

    @Scheduled(fixedRate = 5000)
    public void refreshStrategies() {
        if (isMarketOpen()) {
            List<Strategy> strategies = strategyRepository.findStrategies("N", "Y", FETCH_STRATEGIES_BY_STATUS, ExecutionTypeMenu.PAPER_TRADING.getKey());
            paperStrategyCache = List.copyOf(strategies); // immutable snapshot
        }
    }

    @Scheduled(fixedRate = 1000)
    public void generateSchedule() {
        if (isMarketOpen() && commonUtils.shouldRunScheduler()) {
            if (!running.get()) {
                CompletableFuture.runAsync(this::scheduleTask, taskExecutor);
            }
        }
    }

    public void scheduleTask() {
        if (!running.compareAndSet(false, true)) {
            return; // skips if already running
        }

        Instant startTime = Instant.now();
        try {
            List<Strategy> strategies = paperStrategyCache;

            if (strategies == null || strategies.isEmpty()) {
                return;
            }

            final int total = strategies.size();
            final boolean isWeekend = isWeekend();

            // Process strategies in batches
            int batchSize = Math.min(DEFAULT_BATCH_SIZE, Math.max(1, DB_CONCURRENCY * 4));
            int start = 0;
            while (start < total) {
                int end = Math.min(total, start + batchSize);
                List<CompletableFuture<Void>> futures = new ArrayList<>(end - start);
                for (int i = start; i < end; i++) {
                    Strategy s = strategies.get(i);
                    if (shouldSkipStrategyOnWeekend(s, isWeekend)) {
                        continue;
                    }
                    futures.add(checkStrategyAsync(s));
                }

                CompletableFuture<Void> batchAll = CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]));
                try {
                    batchAll.join();
                } catch (CompletionException ce) {
                    logger.warn("One or more strategy checks in batch completed exceptionally: {}", ce.getMessage(), ce);
                }

                start = end;
            }

        } catch (Exception e) {
            logger.error("Unexpected error in scheduleTask: {}", e.getMessage(), e);
        } finally {
            running.set(false);
        }

        long duration = Instant.now().toEpochMilli() - startTime.toEpochMilli();
//        logger.info("Schedule task completed in {} ms", duration);
    }

    // Route DB-heavy parser.check calls to the DB-limited executor. All strategies are queued and none are skipped.
    public CompletableFuture<Void> checkStrategyAsync(Strategy strategy) {
        return CompletableFuture.runAsync(() -> {
            try {
                parser.check(strategy);
            } catch (Exception e) {
                Object id = null;
                try {
                    id = strategy.getId();
                } catch (Throwable ignore) {
                }
                logger.error("Error checking strategy {}: {}", id, e.getMessage(), e);
            }
        }, dbExecutor);
    }


    private boolean isMarketOpen() {
        LocalTime now = LocalTime.now();
        return now.isAfter(LocalTime.of(9, 14)) && now.isBefore(LocalTime.of(15, 30));
    }

    private boolean shouldSkipStrategyOnWeekend(Strategy strategy, boolean isWeekend) {
        return isWeekend
                && strategy != null
                && strategy.getPositionType() != null
                && strategy.getPositionType().equalsIgnoreCase(POSITIONAL.getKey());
    }

    private boolean isWeekend() {
        DayOfWeek day = LocalDate.now().getDayOfWeek();
        return day == DayOfWeek.SATURDAY || day == DayOfWeek.SUNDAY;
    }

    public void submitStrategyToManuallyExit(Strategy strategy) {

        priorityExecutor.submit(() -> {
            try {
                parser.check(strategy);
            } catch (Exception e) {
                logger.error("Error executing immediate exit", e);
            }
        });
    }
}
