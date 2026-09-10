package com.quantlab.signal.service.redisService;


import com.quantlab.signal.dto.redisDto.OpenInterestBinaryResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.ListOperations;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
@Qualifier("redisTemplate3")
public class OpenInterestRepository {

    @Autowired
    private final RedisTemplate<String, OpenInterestBinaryResponse> redisTemplate;

    public OpenInterestRepository(RedisTemplate<String, OpenInterestBinaryResponse> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    public void saveList(String key, List<OpenInterestBinaryResponse> openInterest){
        ListOperations<String, OpenInterestBinaryResponse > listOps = redisTemplate.opsForList();
        listOps.rightPushAll(key, openInterest);
    }
    public void save(String key, OpenInterestBinaryResponse openInterest) {
        redisTemplate.opsForValue().set(key, openInterest);
    }
    public List<OpenInterestBinaryResponse> findAll(String key){
        ListOperations<String, OpenInterestBinaryResponse> listOps = redisTemplate.opsForList();
        return listOps.range(key, 0, -1);
    }

    public void clearList(String key){
        redisTemplate.delete(key);
    }


}


