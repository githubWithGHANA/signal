package com.quantlab.signal.sheduler;

import com.quantlab.signal.service.TradingDayRuntimeCleanupService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalTime;
import java.time.ZoneId;

@Component
public class TradingDayRuntimeCleanupScheduler {

    private static final Logger logger = LoggerFactory.getLogger(TradingDayRuntimeCleanupScheduler.class);
    private static final ZoneId MARKET_ZONE = ZoneId.of("Asia/Kolkata");
    private static final LocalTime MARKET_GUARD_START = LocalTime.of(9, 0);
    private static final LocalTime MARKET_GUARD_END = LocalTime.of(15, 45);

    private final TradingDayRuntimeCleanupService tradingDayRuntimeCleanupService;

    public TradingDayRuntimeCleanupScheduler(TradingDayRuntimeCleanupService tradingDayRuntimeCleanupService) {
        this.tradingDayRuntimeCleanupService = tradingDayRuntimeCleanupService;
    }


    @Scheduled(cron = "0 0 0 * * *", zone = "Asia/Kolkata")
    public void cleanupForNewTradingDay() {
        LocalTime now = LocalTime.now(MARKET_ZONE);
        if (isMarketGuardWindow(now)) {
            logger.warn("Skipping trading day runtime cleanup during market guard window. time={}", now);
            return;
        }

        tradingDayRuntimeCleanupService.cleanupForNewTradingDay();
    }

    private boolean isMarketGuardWindow(LocalTime now) {
        return !now.isBefore(MARKET_GUARD_START) && !now.isAfter(MARKET_GUARD_END);
    }
}
