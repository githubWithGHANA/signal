package com.quantlab.signal.service.redisService;

import com.quantlab.signal.dto.redisDto.MarketData;
import com.quantlab.signal.dto.redisDto.MasterResponseFO;
import com.quantlab.signal.dto.redisDto.TouchlineBinaryResposne;
import com.quantlab.signal.strategy.SignalService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
    public class TouchLineService {


    private  TouchLineRepository repository;

    private static final Logger logger = LoggerFactory.getLogger(TouchLineService.class);


    public TouchLineService(TouchLineRepository touchLineRepository) {
        this.repository = touchLineRepository;
    }

    public void saveTouchLine(String key, MarketData touchLine) {
        repository.save(key, touchLine);
    }

    public MarketData getTouchLine(String key) {
        String fullKey = "MD_" + key;
        try {
            MarketData marketData = repository.find(fullKey);
            if (marketData == null) {
//                logger.warn("MarketData cache miss for key: {}", fullKey);
            }
            return marketData;
        } catch (Exception e) {
            logger.error("Redis operation failed for key: {}", fullKey, e);
            return null;
        }
    }

    public Map<String, MarketData> getMultipleTouchLines(List<String> keys) {
        try {
            List<String> prefixedKeys = keys.stream()
                    .map(key -> "MD_" + key)
                    .collect(Collectors.toList());

            return repository.findMultiple(prefixedKeys);
        } catch (Exception e) {
            logger.error("Error fetching multiple MarketData entries", e);
            return Collections.emptyMap();
        }
    }


    public MasterResponseFO getMaster_(String key) {
        return repository.findMaster("MASTER_" +key);
    }

    public String getToken(String key) {
        return repository.fetchTokens("TOKEN_" +key);
    }


    public String saveToken(String key, String value) {
        return repository.saveTokens("TOKEN_" +key, value);
    }

    public String saveRateLimiter(String key, String value) {
        return repository.saveRateLimiter(key, value);
    }

    public String getRateLimiter(String key) {
        return repository.fetchRateLimiter(key);
    }


    public List<String> getExpiryDates(String key) {
        return repository.fetchExpiryDates(key);
    }

    public List<MasterResponseFO> getMasterResponseFO(String instrumentExpiryDateKey) {
        return repository.fetchMasterResponseFO(instrumentExpiryDateKey);
    }

//    public Boolean keyExists(String key) {
//        return repository.keyExists(key);
//    }

    public void deleteTouchLine(String key) {
        repository.delete(key);
    }

    public List<TouchlineBinaryResposne> getTouchLines() {
        return repository.findAllTouchLines();
    }

    public void saveTouchLine(String key, TouchlineBinaryResposne touchLine) {
            repository.saveTouchLine(key, touchLine);
    }

    public List<MarketData> findAllMarketData() {
        return repository.findAllMarketData();
    }

    public void saveMarketData(String key, MarketData marketData) {
            repository.saveMarketData(key, marketData);
    }
}
