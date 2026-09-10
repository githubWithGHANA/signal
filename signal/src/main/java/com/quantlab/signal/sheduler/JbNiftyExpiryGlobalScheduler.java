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
 * Global scheduler for JB Nifty Expiry Special strategy.
 *
 * Runs at 15-minute candle boundaries with 15-second offset.
 * Computes RSI and Supertrend on NIFTY ATM straddle combined premium.
 *
 * Uses the same engine service as BANKNIFTY (JbBankniftyExpiryEngineService)
 * since the logic is identical — only the underlying and template ID differ.
 */
@Component
public class JbNiftyExpiryGlobalScheduler {

    private static final Logger logger = LoggerFactory.getLogger(JbNiftyExpiryGlobalScheduler.class);

    private static final Long JB_NIFTY_TEMPLATE_ID = 66L;

    private static final String UNDERLYING = "NIFTY";

    private static final LocalTime MARKET_OPEN = LocalTime.of(9, 15);
    private static final LocalTime MARKET_CLOSE = LocalTime.of(15, 20);

    @Autowired
    private JbBankniftyExpiryEngineService engineService;

    @Autowired
    private StrategyRepository strategyRepository;

    /**
     * Runs at 15 seconds past every 15-minute mark during trading hours.
     * Cron: second=15, minute=0,15,30,45, hour=9-15, weekdays
     */
    @Scheduled(cron = "15 0,15,30,45 9-15 * * MON-FRI", zone = "Asia/Kolkata")
    public void computeAtCandleBoundary() {
        LocalTime now = LocalTime.now(ZoneId.of("Asia/Kolkata"));

        if (now.isBefore(MARKET_OPEN) || now.isAfter(MARKET_CLOSE)) {
            return;
        }

        logger.info("[JB-NIFTY-SCHEDULER] ===== 15-Min Candle Boundary Triggered at {} =====", now);

        Optional<Strategy> templateOpt = strategyRepository.findById(JB_NIFTY_TEMPLATE_ID);
        if (templateOpt.isEmpty()) {
            logger.error("[JB-NIFTY-SCHEDULER] Template strategy {} not found", JB_NIFTY_TEMPLATE_ID);
            return;
        }

        Strategy template = templateOpt.get();

        try {
            logger.info("[JB-NIFTY-SCHEDULER] Computing JB Nifty Expiry detection for {}", UNDERLYING);
            engineService.computeAndStore(template, UNDERLYING);
            logger.info("[JB-NIFTY-SCHEDULER] Computation complete for {}", UNDERLYING);
        } catch (Exception e) {
            logger.error("[JB-NIFTY-SCHEDULER] Error computing for {} | Error={}",
                    UNDERLYING, e.getMessage(), e);
        }
    }

    /**
     * Fallback at 30 and 45 seconds past each 15-minute mark.
     */
    @Scheduled(cron = "30,45 0,15,30,45 9-15 * * MON-FRI", zone = "Asia/Kolkata")
    public void fallbackDetectionCheck() {
        LocalTime now = LocalTime.now(ZoneId.of("Asia/Kolkata"));

        if (now.isBefore(MARKET_OPEN) || now.isAfter(MARKET_CLOSE)) {
            return;
        }

        Optional<Strategy> templateOpt = strategyRepository.findById(JB_NIFTY_TEMPLATE_ID);
        if (templateOpt.isEmpty()) {
            return;
        }

        Strategy template = templateOpt.get();

        var resultOpt = engineService.getSignal(UNDERLYING);
        if (resultOpt.isEmpty() || !resultOpt.get().isValid()) {
            logger.info("[JB-NIFTY-SCHEDULER] Fallback: Recomputing for {} | Reason={}",
                    UNDERLYING, resultOpt.isEmpty() ? "missing" : "invalid");
            try {
                engineService.computeAndStore(template, UNDERLYING);
            } catch (Exception e) {
                logger.error("[JB-NIFTY-SCHEDULER] Fallback error for {} | Error={}", UNDERLYING, e.getMessage());
            }
        }
    }
}