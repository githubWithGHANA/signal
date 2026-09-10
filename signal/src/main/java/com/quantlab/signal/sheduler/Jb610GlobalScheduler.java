package com.quantlab.signal.sheduler;

import com.quantlab.common.entity.Strategy;
import com.quantlab.common.repository.StrategyRepository;
import com.quantlab.signal.strategy.engine.Jb610EngineService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalTime;
import java.time.ZoneId;
import java.util.Optional;

/**
 * Global scheduler for JB610 Dual Supertrend Crossover Strategy.
 *
 * Runs at 30-minute candle boundaries with 15-second offset.
 * Computes dual Supertrend (6,1) and (10,3) on BANKNIFTY INDEX 30-min candles.
 *
 * Schedule: 09:30:15, 10:00:15, 10:30:15, 11:00:15, ... 15:00:15
 * (Every 30 minutes aligned to market candles)
 */
@Component
public class Jb610GlobalScheduler {

    private static final Logger logger = LoggerFactory.getLogger(Jb610GlobalScheduler.class);

    private static final Long JB610_TEMPLATE_ID = 67L;

    private static final String UNDERLYING = "BANKNIFTY";

    private static final LocalTime MARKET_OPEN = LocalTime.of(9, 15);
    private static final LocalTime MARKET_CLOSE = LocalTime.of(15, 20);

    @Autowired
    private Jb610EngineService engineService;

    @Autowired
    private StrategyRepository strategyRepository;

    /**
     * Runs at 15 seconds past every 30-minute mark during trading hours.
     * 30-min candle boundaries: 09:30, 10:00, 10:30, 11:00, ...
     *
     * Cron: second=15, minute=0,30, hour=9-15, weekdays
     */
    @Scheduled(cron = "15 0,30 9-15 * * MON-FRI", zone = "Asia/Kolkata")
    public void computeAtCandleBoundary() {
        LocalTime now = LocalTime.now(ZoneId.of("Asia/Kolkata"));

        if (now.isBefore(MARKET_OPEN) || now.isAfter(MARKET_CLOSE)) {
            return;
        }

        logger.info("[JB610-SCHEDULER] ===== 30-Min Candle Boundary Triggered at {} =====", now);

        Optional<Strategy> templateOpt = strategyRepository.findById(JB610_TEMPLATE_ID);
        if (templateOpt.isEmpty()) {
            logger.error("[JB610-SCHEDULER] Template strategy {} not found", JB610_TEMPLATE_ID);
            return;
        }

        Strategy template = templateOpt.get();

        try {
            logger.info("[JB610-SCHEDULER] Computing JB610 detection for {}", UNDERLYING);
            engineService.computeAndStore(template, UNDERLYING);
            logger.info("[JB610-SCHEDULER] Computation complete for {}", UNDERLYING);
        } catch (Exception e) {
            logger.error("[JB610-SCHEDULER] Error computing for {} | Error={}",
                    UNDERLYING, e.getMessage(), e);
        }
    }

    /**
     * Fallback at 30 and 45 seconds past each 30-minute mark.
     * Recomputes if signal is missing or expired.
     */
    @Scheduled(cron = "30,45 0,30 9-15 * * MON-FRI", zone = "Asia/Kolkata")
    public void fallbackDetectionCheck() {
        LocalTime now = LocalTime.now(ZoneId.of("Asia/Kolkata"));

        if (now.isBefore(MARKET_OPEN) || now.isAfter(MARKET_CLOSE)) {
            return;
        }

        Optional<Strategy> templateOpt = strategyRepository.findById(JB610_TEMPLATE_ID);
        if (templateOpt.isEmpty()) {
            return;
        }

        Strategy template = templateOpt.get();

        var resultOpt = engineService.getSignal(UNDERLYING);
        if (resultOpt.isEmpty() || !resultOpt.get().isValid()) {
            logger.info("[JB610-SCHEDULER] Fallback: Recomputing for {} | Reason={}",
                    UNDERLYING, resultOpt.isEmpty() ? "missing" : "invalid");
            try {
                engineService.computeAndStore(template, UNDERLYING);
            } catch (Exception e) {
                logger.error("[JB610-SCHEDULER] Fallback error for {} | Error={}", UNDERLYING, e.getMessage());
            }
        }
    }
}