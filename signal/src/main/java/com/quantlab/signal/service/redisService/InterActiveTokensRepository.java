package com.quantlab.signal.service.redisService;

import com.quantlab.signal.dto.redisDto.InterActiveTokensDTO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.ListOperations;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Set;

@Repository
public class InterActiveTokensRepository {



    @Autowired
    @Qualifier("redisTemplateInteractiveTokens")
    private final RedisTemplate<String, InterActiveTokensDTO> redisTemplate;

    public InterActiveTokensRepository(RedisTemplate<String, InterActiveTokensDTO> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    public void save(String key, InterActiveTokensDTO interActiveTokensDTO) {
        redisTemplate.opsForValue().set("INTERACTIVE_"+key, interActiveTokensDTO);
    }



    public List<InterActiveTokensDTO> findAll(String key) {
        ListOperations<String, InterActiveTokensDTO> listOps = redisTemplate.opsForList();
        return listOps.range(key, 0, -1);
    }

    public Boolean delete(String key) {
        return redisTemplate.delete(key);
    }


    public InterActiveTokensDTO find(String key) {
        return redisTemplate.opsForValue().get("INTERACTIVE_"+key);
    }

    public Set<String> findAllInteractiveKeys() {
        return redisTemplate.keys("INTERACTIVE_*");
    }

    public boolean exists(String key) {
        String redisKey = key.startsWith("INTERACTIVE_") ? key : "INTERACTIVE_" + key;
        Boolean exists = redisTemplate.hasKey(redisKey);
        return Boolean.TRUE.equals(exists);
    }

}
