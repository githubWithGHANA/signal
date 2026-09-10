package com.quantlab.signal.sheduler;

import com.quantlab.common.entity.Strategy;
import com.quantlab.common.repository.StrategyRepository;
import com.quantlab.signal.strategy.engine.JbBankniftyExpiryEngineService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalTime;
import java.time.ZoneId;
import java.util.Optional;

/**
 * Global scheduler for JB Banknifty Expiry Special strategy.
 *
 * Runs at 15-minute candle boundaries with 15-second offset.
 * Computes RSI and Supertrend on ATM straddle combined premium.
 *
 * Schedule: 09:15:15, 09:30:15, 09:45:15, 10:00:15, ... 15:15:15
 * (Every 15 minutes aligned to market candles)
 *
 * This is expiry-day only strategy, but the scheduler runs every day.
 * The strategy itself will check if it's expiry day before taking action.
 */
@Component
public class JbBankniftyExpiryGlobalScheduler {

    private static final Logger logger = LoggerFactory.getLogger(JbBankniftyExpiryGlobalScheduler.class);

    // Template strategy ID for JB Banknifty Expiry Special
    private static final Long JB_BANKNIFTY_TEMPLATE_ID = 65L;

    private static final String UNDERLYING = "BANKNIFTY";

    private static final LocalTime MARKET_OPEN = LocalTime.of(9, 15);
    private static final LocalTime MARKET_CLOSE = LocalTime.of(15, 20);

    @Autowired
    private JbBankniftyExpiryEngineService engineService;

    @Autowired
    private StrategyRepository strategyRepository;

    /**
     * Runs at 15 seconds past every 15-minute mark during trading hours.
     * 15-min candle boundaries: 09:15, 09:30, 09:45, 10:00, ...
     *
     * Cron: second=15, minute=0,15,30,45, hour=9-15, weekdays
     */
    @Scheduled(cron = "15 0,15,30,45 9-15 * * MON-FRI", zone = "Asia/Kolkata")
    public void computeAtCandleBoundary() {
        LocalTime now = LocalTime.now(ZoneId.of("Asia/Kolkata"));

        if (now.isBefore(MARKET_OPEN) || now.isAfter(MARKET_CLOSE)) {
            logger.debug("[JB-SCHEDULER] Outside trading hours, skipping");
            return;
        }

        logger.info("[JB-SCHEDULER] ===== 15-Min Candle Boundary Triggered at {} =====", now);

        Optional<Strategy> templateOpt = strategyRepository.findById(JB_BANKNIFTY_TEMPLATE_ID);
        if (templateOpt.isEmpty()) {
            logger.error("[JB-SCHEDULER] Template strategy {} not found", JB_BANKNIFTY_TEMPLATE_ID);
            return;
        }

        Strategy template = templateOpt.get();

        try {
            logger.info("[JB-SCHEDULER] Computing JB Banknifty Expiry detection for {}", UNDERLYING);
            engineService.computeAndStore(template, UNDERLYING);
            logger.info("[JB-SCHEDULER] JB Banknifty Expiry computation complete for {}", UNDERLYING);
        } catch (Exception e) {
            logger.error("[JB-SCHEDULER] Error computing for {} | Error={}",
                    UNDERLYING, e.getMessage(), e);
        }
    }

    /**
     * Fallback scheduler at 30 and 45 seconds past each 15-minute mark.
     * Recomputes if signal is missing or expired.
     */
    @Scheduled(cron = "30,45 0,15,30,45 9-15 * * MON-FRI", zone = "Asia/Kolkata")
    public void fallbackDetectionCheck() {
        LocalTime now = LocalTime.now(ZoneId.of("Asia/Kolkata"));

        if (now.isBefore(MARKET_OPEN) || now.isAfter(MARKET_CLOSE)) {
            return;
        }

        Optional<Strategy> templateOpt = strategyRepository.findById(JB_BANKNIFTY_TEMPLATE_ID);
        if (templateOpt.isEmpty()) {
            return;
        }

        Strategy template = templateOpt.get();

        var resultOpt = engineService.getSignal(UNDERLYING);
        if (resultOpt.isEmpty() || !resultOpt.get().isValid()) {
            logger.info("[JB-SCHEDULER] Fallback: Recomputing for {} | Reason={}",
                    UNDERLYING, resultOpt.isEmpty() ? "missing" : "invalid");
            try {
                engineService.computeAndStore(template, UNDERLYING);
            } catch (Exception e) {
                logger.error("[JB-SCHEDULER] Fallback error for {} | Error={}", UNDERLYING, e.getMessage());
            }
        }
    }
}