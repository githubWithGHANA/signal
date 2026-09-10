package com.quantlab.client.service;

import com.quantlab.client.websockets.OpenPositionsSocketHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;

import java.util.HashMap;
import java.util.Map;

public class OpenPositionsService {

    private static final Logger logger = LoggerFactory.getLogger(OpenPositionsService.class);

    @Autowired
    OpenPositionsSocketHandler openPositionsSocketHandler;

    public void updateMarketData(String userId, Map<String, Object> marketData) {
        logger.info("Updating market data for user ID: " + userId + " with data: " + marketData);
        openPositionsSocketHandler.sendOpenPositionsToUser(userId, marketData);
        logger.info("Market data successfully sent to user ID: " + userId);
    }

    public void sendSampleMarketData() {
        Map<String, Object> marketData = new HashMap<>();
        marketData.put("symbol", "AAPL");
        marketData.put("price", Math.random() * 150 + 100);
        updateMarketData("123", marketData);
    }
}
