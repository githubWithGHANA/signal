package com.quantlab.signal.service.redisService;
import com.quantlab.signal.dto.redisDto.MarketData;
import com.quantlab.signal.dto.redisDto.MasterResponseFO;
import com.quantlab.signal.dto.redisDto.TouchlineBinaryResposne;
import com.quantlab.signal.grpcserver.PositionStreamGrpc;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Repository;

import java.time.Duration;
import java.util.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static com.quantlab.common.utils.staticstore.AppConstants.MASTERDATA;
import static com.quantlab.common.utils.staticstore.AppConstants.RATE_LIMIT_WINDOW_SECONDS;

@Repository
    public class TouchLineRepository {
    private static final Logger logger = LoggerFactory.getLogger(TouchLineRepository.class);


    @Autowired
    @Qualifier("redisTemplate1")
    private final RedisTemplate<String, MarketData> redisTemplate;

    @Autowired
    @Qualifier("redisTemplateList")
    private final RedisTemplate<String, String> redisExpiryDatesTemplate;

    @Autowired
    @Qualifier("redisTemplate5")
    private final RedisTemplate<String, MasterResponseFO> redisMasterResponseFOTemplate;

    @Autowired
    @Qualifier("redisTemplateTouchlineBinaryResponse")
    private final RedisTemplate<String, TouchlineBinaryResposne> redisTemplateTouchlineBinaryResponseTemplate;



    public TouchLineRepository(RedisTemplate<String, MarketData> redisTemplate,
                               @Qualifier("redisTemplateList") RedisTemplate<String, String> redisExpiryDatesTemplate,
                               @Qualifier("redisTemplate5") RedisTemplate<String, MasterResponseFO> redisMasterResponseFOTemplate, RedisTemplate<String, TouchlineBinaryResposne> redisTemplateTouchlineBinaryResponseTemplate) {
        this.redisTemplate = redisTemplate;
        this.redisMasterResponseFOTemplate = redisMasterResponseFOTemplate;
        this.redisExpiryDatesTemplate = redisExpiryDatesTemplate;
        this.redisTemplateTouchlineBinaryResponseTemplate = redisTemplateTouchlineBinaryResponseTemplate;
    }

    public void save(String key, MarketData touchLine) {
        try {
            redisTemplate.opsForValue().set(key, touchLine);
        } catch (Exception e) {
            // Log the exception details
            logger.error("Error setting value in Redis: {}", e.getMessage());
//            e.printStackTrace();
        }

    }

    public MarketData find(String key) {
        int maxRetries = 2;
        int backoffMs = 100;

        for (int attempt = 1; attempt <= maxRetries; attempt++) {
            try {
                return redisTemplate.opsForValue().get(key);
            } catch (Exception e) {
                if (attempt == maxRetries) {
                    logger.error("Redis GET failed after {} attempts for key: {}, error: {}", maxRetries, key, e.getMessage());
                    return null;
                }
                logger.warn("Redis GET attempt {}/{} failed for key: {}, retrying in {}ms", attempt, maxRetries, key, backoffMs * attempt);
                try {
                    Thread.sleep(backoffMs * attempt);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    logger.error("Interrupted during Redis retry for key: {}", key);
                    return null;
                }
            }
        }
        return null;
    }

    public Map<String, MarketData> findMultiple(List<String> keys) {
        Map<String, MarketData> result = new HashMap<>();
        try {
            List<MarketData> values = redisTemplate.opsForValue().multiGet(keys);
            if (values == null) {
                return Collections.emptyMap();
            }

            for (int i = 0; i < keys.size(); i++) {
                String key = keys.get(i);
                MarketData value = values.get(i);
                if (value != null) {
                    result.put(key, value);
                }
            }
            return result;
        } catch (Exception e) {
            logger.error("Error finding multiple values in Redis", e);
            return Collections.emptyMap();
        }
    }


    public MasterResponseFO findMaster(String key) {
        MasterResponseFO touchlineBinaryResposne = null;
        try {
            touchlineBinaryResposne = redisMasterResponseFOTemplate.opsForValue().get(key);
        }
        catch (Exception e) {
            // Log the exception details
            logger.error("Error finding value in Redis: {}", e.getMessage());
//            e.printStackTrace();
        }
        return touchlineBinaryResposne;
    }

    public void delete(String key) {
        try {
            redisTemplate.delete(key);
        } catch (Exception e) {
            // Log the exception details
            logger.error("Error deleting value in Redis: {}", e.getMessage());
//            e.printStackTrace();
        }
    }

    public List<String> fetchExpiryDates(String listKey) {
        List<String> redisData = null;
        try {
            redisData = redisExpiryDatesTemplate.opsForList().range(listKey, 0, -1);
        }
        catch (Exception e) {
            logger.error("Error getting value listKey:{}, in Redis: {}", listKey, e.getMessage());
//            e.printStackTrace();
        }
        return redisData;
    }

    public List<MasterResponseFO> fetchMasterResponseFO(String instrumentExpiryDateKey) {
        List<MasterResponseFO> redisData = null;
        try {
            redisData = redisMasterResponseFOTemplate.opsForList().range(instrumentExpiryDateKey, 0, -1);
        }
        catch (Exception e) {
            logger.error("Error getting value:{} in Redis: {}", instrumentExpiryDateKey, e.getMessage());
//            e.printStackTrace();
        }
        return redisData;
    }

    public String fetchTokens(String key) {
        String value = null;
        try {
            value = redisExpiryDatesTemplate.opsForValue().get(key);
        }
        catch (Exception e) {
            logger.error("Error finding tokens in Redis for: {},  error : {}", key, e.getMessage());
        }
        return value;
    }

    public String saveTokens(String key, String value) {
        try {
            redisExpiryDatesTemplate.opsForValue().set(key, value, Duration.ofSeconds(900));
        }
        catch (Exception e) {
            logger.error("Error saving tokens in Redis key: {}, value: {}, error: {}", key, value, e.getMessage());
//            e.printStackTrace();
        }
        return value;
    }

    public String saveRateLimiter(String key, String value) {
        try {
            redisExpiryDatesTemplate.opsForValue().set(key, value, Duration.ofSeconds(RATE_LIMIT_WINDOW_SECONDS));
        }
        catch (Exception e) {
            logger.error("Error saveRateLimiter in Redis key: {}, value: {}, error: {}", key, value, e.getMessage());
        }
        return value;
    }

    public String fetchRateLimiter(String key) {
        String value = null;
        try {
            value = redisExpiryDatesTemplate.opsForValue().get(key);
        }
        catch (Exception e) {
            logger.error("Error while fetchRateLimiter in Redis for: {},  error : {}", key, e.getMessage());
        }
        return value;
    }

    public void saveTouchLine(String key, TouchlineBinaryResposne touchLine) {
        try {
            redisTemplateTouchlineBinaryResponseTemplate.opsForValue().set(key, touchLine);
        } catch (Exception e) {
            logger.error("Error setting value in Redis: {}", e.getMessage());
            throw new RuntimeException("Failed to fetch TouchlineBinaryResposne with key: ", e);

        }

    }
    public List<MarketData> findAllMarketData() {
        List<MarketData> result = new ArrayList<>();
        try {
            Set<String> keys = redisTemplate.keys("MD_*");
            if (keys != null && !keys.isEmpty()) {
                for (String key : keys) {
                    MarketData value = redisTemplate.opsForValue().get(key);
                    if (value != null) {
                        result.add(value);
                    }
                }
            }
        } catch (Exception e) {
            // Log the exception details
            logger.error("Error fetching MarketData in Redis: {}", e.getMessage());
            throw new RuntimeException("Failed to fetch MarketData with key: ", e);
        }
        return result;
    }

    public void saveMarketData(String key, MarketData marketData) {
        try {
            redisTemplate.opsForValue().set(key, marketData);
        } catch (Exception e) {
            // Log the exception details
            logger.error("Error setting value MarketData in Redis: {}", e.getMessage());
            throw new RuntimeException("Failed to fetch MarketData with key: ", e);
        }

    }

    public List<TouchlineBinaryResposne> findAllTouchLines() {
        List<TouchlineBinaryResposne> result = new ArrayList<>();
        try {
            Set<String> keys = redisTemplate.keys("TL_*");
            if (keys != null && !keys.isEmpty()) {
                for (String key : keys) {
                    TouchlineBinaryResposne value = redisTemplateTouchlineBinaryResponseTemplate.opsForValue().get(key);
                    if (value != null) {
                        result.add(value);
                    }
                }
            }
        } catch (Exception e) {
            logger.error("Error getting values from Redis: {}", e.getMessage());
            throw new RuntimeException("Failed to Load touchlines: " + e);

        }
        return result;
    }
}