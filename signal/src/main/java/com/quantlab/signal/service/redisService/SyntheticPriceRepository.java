package com.quantlab.signal.service.redisService;

import com.quantlab.signal.dto.redisDto.SyntheticPrice;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.ListOperations;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public class SyntheticPriceRepository {



    @Autowired
    @Qualifier("redisTemplateSynthetic")
    private final RedisTemplate<String, SyntheticPrice> redisTemplate;

    public SyntheticPriceRepository(RedisTemplate<String, SyntheticPrice> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    public void save(String key, SyntheticPrice syntheticPrice) {
        redisTemplate.opsForValue().set("SYNTHETIC_"+key, syntheticPrice);
//           ListOperations<String, MasterResponseFO> listOps = redisTemplate.opsForList();
//           listOps.rightPushAll(key, masterResponseFO);

    }



    public List<SyntheticPrice> findAll(String key) {
        ListOperations<String, SyntheticPrice> listOps = redisTemplate.opsForList();
        return listOps.range(key, 0, -1);
    }

    public void clearList(String key) {
        redisTemplate.delete(key);
    }


    public SyntheticPrice find(String key) {
        return redisTemplate.opsForValue().get("SYNTHETIC_"+key);
    }


}
