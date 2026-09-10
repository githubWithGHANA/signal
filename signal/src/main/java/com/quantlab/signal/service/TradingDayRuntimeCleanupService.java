package com.quantlab.signal.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Instant;

@Service
public class TradingDayRuntimeCleanupService {

    private static final Logger logger = LoggerFactory.getLogger(TradingDayRuntimeCleanupService.class);

    private final ApplicationEventPublisher eventPublisher;

    public TradingDayRuntimeCleanupService(ApplicationEventPublisher eventPublisher) {
        this.eventPublisher = eventPublisher;
    }

    public void cleanupForNewTradingDay() {
        int liveStrategyPnlSize = DiyStrategyService.liveStrategyPNL.size();
        int strategyNonLivePnlSize = ExitPNL.strategyNonLivePNL.size();

        logger.info("Trading day runtime cleanup started: liveStrategyPNL={}, strategyNonLivePNL={}",
                liveStrategyPnlSize, strategyNonLivePnlSize);

        DiyStrategyService.liveStrategyPNL.clear();
        ExitPNL.strategyNonLivePNL.clear();
        eventPublisher.publishEvent(new TradingDayRuntimeCleanupEvent(Instant.now()));
        DiyStrategyService.liveStrategyPNL.clear();
        ExitPNL.strategyNonLivePNL.clear();

        logger.info("Trading day runtime cleanup completed: liveStrategyPNL={}, strategyNonLivePNL={}",
                DiyStrategyService.liveStrategyPNL.size(), ExitPNL.strategyNonLivePNL.size());
    }
}
