package com.quantlab.client.websockets;

import com.quantlab.signal.dto.PNLHoldingDTO;
import com.quantlab.signal.dto.redisDto.MarketData;
import com.quantlab.signal.service.redisService.TouchLineService;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.io.IOException;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

@Service
public class LiveIndexDataService {

    private static final Logger logger = LoggerFactory.getLogger(LiveIndexDataService.class);

    @Autowired
    TouchLineService touchLineService;

    @Autowired
    OpenPositionsSocketHandler openPositionsSocketHandler;

    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(4);
    int indexUIThreads = 0;

    @PostConstruct
    public void socketUIThread() {
        try {
            if (indexUIThreads < 1) {
                Runnable task = () -> {
                    try {
                        updateAndBroadcastIndexData();
                    } catch (Exception e) {
                        logger.error("Error processing updateAndBroadcastIndexData task: {}", e.getMessage());
                    }
                };
                scheduler.scheduleWithFixedDelay(() -> {
                    if (LocalTime.now().isAfter(LocalTime.of(9, 15)) &&
                            LocalTime.now().isBefore(LocalTime.of(15, 31))) {
                        task.run();
                    }
                }, 0, 1000, TimeUnit.MILLISECONDS);

                // off-market schedule (every 1000ms)
                scheduler.scheduleWithFixedDelay(() -> {
                    if (!(LocalTime.now().isAfter(LocalTime.of(9, 15)) &&
                            LocalTime.now().isBefore(LocalTime.of(15, 31)))) {
                        task.run();
                    }
                }, 0, 2000, TimeUnit.MILLISECONDS);

                indexUIThreads++;
            }
        } catch (Exception e) {
            logger.error("Error in socketUIThread : {}", e.getMessage());
        }
    }

    public void updateAndBroadcastIndexData() {
        try {
            List<Map<String, Object>> indexData = fetchLiveIndexData();

            Map<String, Object> wrapper = new HashMap<>();
            wrapper.put("type", "INDEX_DATA");
            wrapper.put("data", indexData);
            openPositionsSocketHandler.broadcastIndexData(wrapper);
        } catch (Exception e) {
            logger.error("Error broadcasting index data: {}", e.getMessage());
        }
    }

    public List<Map<String, Object>> fetchLiveIndexData() {
        List<Map<String, Object>> indexDataList = new ArrayList<>();

        Map<String, String> indexKeyMap = new LinkedHashMap<>();
        indexKeyMap.put("NIFTY", "26000");
        indexKeyMap.put("BANKNIFTY", "26001");
        indexKeyMap.put("SENSEX", "26065");
        indexKeyMap.put("FINNIFTY", "26034");
        indexKeyMap.put("MIDCAPNIFTY", "26005");

        List<String> keys = new ArrayList<>(indexKeyMap.values());

        Map<String, MarketData> marketDataMap = touchLineService.getMultipleTouchLines(keys);

        for (Map.Entry<String, String> entry : indexKeyMap.entrySet()) {
            String indexName = entry.getKey();
            String key = entry.getValue();

            MarketData marketData = marketDataMap.get("MD_" + key);

            if (marketData != null) {
                double ltp = marketData.getLTP();
                double close = marketData.getClose();
                double change = ltp - close;
                double percentChange = (change / close) * 100;

                indexDataList.add(createIndexEntry(
                        indexName,
                        ltp,
                        change,
                        percentChange
                ));
            } else {
//                logger.warn("Market data not found for key: " + key + ", using random value as fallback");
                double fallbackValue = getFallbackValue(indexName);
                // For fallback, we'll use default values for change/percentChange
                indexDataList.add(createIndexEntry(
                        indexName,
                        fallbackValue,
                        10,
                        1.23
                ));
            }
        }

        return indexDataList;
    }

    private Map<String, Object> createIndexEntry(String name, double value, double change, double percentChange) {
        Map<String, Object> entry = new HashMap<>();
        entry.put("name", name);
        entry.put("value", Math.round(value * 100.0) / 100.0);
        entry.put("change", Math.round(change * 100.0) / 100.0);
        entry.put("percentChange", Math.round(percentChange * 100.0) / 100.0);
        return entry;
    }


    private double getFallbackValue(String indexName) {
        switch(indexName) {
            case "NIFTY":
                return 0;
            case "BANKNIFTY":
                return 0;
            case "SENSEX":
                return 0;
            case "FINNIFTY":
                return 0;
            case "MIDCAPNIFTY":
                return 0;
            default:
                return 0.0;
        }
    }
}
