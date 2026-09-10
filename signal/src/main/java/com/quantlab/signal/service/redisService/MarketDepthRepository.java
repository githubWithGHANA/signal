package com.quantlab.signal.service.redisService;

import com.quantlab.signal.dto.redisDto.MarketDepthBinaryResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.ListOperations;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public class MarketDepthRepository {

    @Autowired
    @Qualifier("redisTemplate2")
    private final RedisTemplate<String, MarketDepthBinaryResponse> redisTemplate;

    public MarketDepthRepository(RedisTemplate<String, MarketDepthBinaryResponse> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    public void saveList(String key, List<MarketDepthBinaryResponse> marketDepth){
        ListOperations<String, MarketDepthBinaryResponse > listOps = redisTemplate.opsForList();
        listOps.rightPushAll(key, marketDepth);
    }

    public List<MarketDepthBinaryResponse> findAll(String key){
        ListOperations<String, MarketDepthBinaryResponse> listOps = redisTemplate.opsForList();
        return listOps.range(key, 0, -1);
    }

    public void clearList(String key){
        redisTemplate.delete(key);
    }
    public void save(String key, MarketDepthBinaryResponse marketDepth) {
        redisTemplate.opsForValue().set(key, marketDepth);
    }

    public MarketDepthBinaryResponse find(String key) {
        return redisTemplate.opsForValue().get(key);
    }



}
