package com.quantlab.signal.service.redisService;

import com.quantlab.signal.dto.PNLHoldingDTO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;

// ...existing code...
@Service
public class PNLRedisRepository {
    private static final Logger logger = LoggerFactory.getLogger(PNLRedisRepository.class);
    private static final String KEY_PREFIX = "PNL_UI_";

    private final RedisTemplate<String, PNLHoldingDTO> redisTemplate;

    @Autowired
    public PNLRedisRepository(RedisTemplate<String, PNLHoldingDTO> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    public void savePNL(String userId, PNLHoldingDTO pnlHoldingDTO) {
        try {
            if (userId == null || pnlHoldingDTO == null) return;
            String key = KEY_PREFIX + userId;
            redisTemplate.opsForValue().set(key, pnlHoldingDTO);
        } catch (Exception e) {
            logger.error("Error saving PNL for user {} to Redis: {}", userId, e.getMessage(), e);
        }
    }

    public void savePNLWithTTL(String userId, PNLHoldingDTO pnlHoldingDTO, Duration ttl) {
        try {
            if (userId == null || pnlHoldingDTO == null) return;
            String key = KEY_PREFIX + userId;
            redisTemplate.opsForValue().set(key, pnlHoldingDTO, ttl);
        } catch (Exception e) {
            logger.error("Error saving PNL with TTL for user {} to Redis: {}", userId, e.getMessage(), e);
        }
    }

    public PNLHoldingDTO fetchPNL(String userId) {
        try {
            if (userId == null) return null;
            String key = KEY_PREFIX + userId;
            return redisTemplate.opsForValue().get(key);
        } catch (Exception e) {
            logger.error("Error fetching PNL for user {} from Redis: {}", userId, e.getMessage(), e);
            return null;
        }
    }

    public void deletePNL(String userId) {
        try {
            if (userId == null) return;
            String key = KEY_PREFIX + userId;
            redisTemplate.delete(key);
        } catch (Exception e) {
            logger.error("Error deleting PNL for user {} from Redis: {}", userId, e.getMessage(), e);
        }
    }
}
