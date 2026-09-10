package com.quantlab.client.websockets;

import com.quantlab.common.dto.StrategyStatus;
import com.quantlab.common.repository.StrategyRepository;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import static com.quantlab.client.websockets.SocketDataProcessorService.subscribedAppUsers;

@Service
public class StrategyStatusUpdatesUI {
    private static final Logger logger = LoggerFactory.getLogger(StrategyStatusUpdatesUI.class);
    private static final LocalTime MARKET_OPEN = LocalTime.of(9, 15);
    private static final LocalTime MARKET_CLOSE = LocalTime.of(15, 31);
    private static final long INTERVAL_MS = 1000L;

    @Autowired
    StrategyRepository strategyRepository;

    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "strategy-status-updates");
        t.setDaemon(true);
        return t;
    });

    OpenPositionsSocketHandler openPositionsSocketHandler = new OpenPositionsSocketHandler();

    @PostConstruct
    public void startStrategyStatusSocket() {
        scheduler.scheduleWithFixedDelay(this::tick, 0, INTERVAL_MS, TimeUnit.MILLISECONDS);
    }

    @PreDestroy
    public void shutdown() {
        scheduler.shutdownNow();
    }

    private void tick() {
        try {
            LocalTime now = LocalTime.now();
            if (now.isBefore(MARKET_OPEN) || now.isAfter(MARKET_CLOSE)) {
                return;
            }
            sendSubscribedStrategyStatus();
        } catch (Exception e) {
            logger.error("Error processing strategy status tick: {}", e.getMessage());
        }
    }

    private void sendSubscribedStrategyStatus() {
        try {
            for (String userId: subscribedAppUsers){
                List<StrategyStatus> strategyStatuses = fetchSubscribedStrategiesStatus(userId);

                Map<String, Object> wrapper = new HashMap<>();
                wrapper.put("type", "STRATEGY_STATUS");
                wrapper.put("data", strategyStatuses);

                openPositionsSocketHandler.sendOpenPositionsToUser(userId, wrapper);
            }
        } catch (Exception e) {
            logger.error("error in sendSubscribedStrategyStatus(): "+ e.getMessage());
//            e.printStackTrace();
        }
    }



    @Transactional
    public List<StrategyStatus> fetchSubscribedStrategiesStatus(String userId) {
        return strategyRepository.findStrategyIdAndStatusBySubscriptionY(Long.valueOf(userId));
    }

}
