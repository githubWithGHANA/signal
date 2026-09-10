package com.quantlab.signal.service.redisService;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.quantlab.signal.dto.redisDto.HolidayResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class CUGRedisService {



    private final RedisTemplate<String, Object> redisTemplateForKeys;
    private final ObjectMapper objectMapper;

    public CUGRedisService(RedisTemplate<String, Object> redisTemplateForKeys, ObjectMapper objectMapper) {
        this.redisTemplateForKeys = redisTemplateForKeys;
        this.objectMapper = objectMapper;
    }


    public List<String> getCUGUsers(String key) {
        Object value = redisTemplateForKeys.opsForValue().get(key);
        if (value == null) {
            return null;
        }

        try {
            return objectMapper.readValue(value.toString(), List.class);
        } catch (Exception e) {
            throw new RuntimeException("Failed to deserialize getCUGUsers key into CUGRedisService", e);
        }
    }

    public boolean saveCUGUsers(String key, List<String> cugUsers) {
        try {
            String serializedValue = objectMapper.writeValueAsString(cugUsers);
            redisTemplateForKeys.opsForValue().set(key, serializedValue);
            return true;
        } catch (Exception e) {
            throw new RuntimeException("Failed to serialize CUGUsers in CUGRedisService", e);
        }
    }


}
