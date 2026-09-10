package com.quantlab.signal.service.redisService;

import com.quantlab.signal.dto.redisDto.OpenInterestBinaryResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class OpenInterestService {

    @Autowired
    private OpenInterestRepository repository;

    public void saveOpenInterest(String key, OpenInterestBinaryResponse openInterest){
        repository.save(key, openInterest);
    }
    public List<OpenInterestBinaryResponse> getOpenInterest(String key){
      return   repository.findAll(key);
    }

    public List<OpenInterestBinaryResponse> getOpenDepth(String key){
        return repository.findAll(key);
    }

    public void deleteMarketDepth(String key){
        repository.clearList(key);
    }




}


