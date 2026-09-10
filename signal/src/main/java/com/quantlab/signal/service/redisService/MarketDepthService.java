package com.quantlab.signal.service.redisService;

import com.quantlab.signal.dto.redisDto.MarketDepthBinaryResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class MarketDepthService {

    @Autowired
    private MarketDepthRepository repository;

    public void saveMarketDepthList(String key, List<MarketDepthBinaryResponse> marketDepth){
        repository.saveList(key, marketDepth);
    }

    public List<MarketDepthBinaryResponse> getMarketDepthList(String key){
        return repository.findAll(key);
    }

    public void deleteMarketDepth(String key){
        repository.clearList(key);
    }


    public void saveMarketDepth(String key, MarketDepthBinaryResponse marketDepth) {
        repository.save(key, marketDepth);
    }

    public MarketDepthBinaryResponse getMarketDepth(String key) {
        return repository.find(key);
    }


}
