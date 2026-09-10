package com.quantlab.client.service;

import com.quantlab.client.dto.OneClickDeployDto;
import com.quantlab.common.entity.Signal;
import com.quantlab.common.entity.Strategy;
import com.quantlab.common.repository.SignalRepository;
import com.quantlab.common.repository.StrategyRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;

@Service
public class DataLayerService {

    private static final Logger logger = LoggerFactory.getLogger(DataLayerService.class);

    @Autowired
   private StrategyRepository strategyRepository;

    @Autowired
   private SignalRepository signalRepository;


    @Async
    public CompletableFuture<Signal> saveSignal(Signal signal) {
        logger.info("Initiating saveSignal process for signal: "+ signal);
        return CompletableFuture.completedFuture(signalRepository.save(signal));
    }


    public String CreateDeploy(OneClickDeployDto oneClickDeployDto){
        logger.info("Initiating createDeploy process for signal: "+ signalRepository.count());
        Optional<Strategy> strategy = strategyRepository.findById(oneClickDeployDto.getStrategyId());
        if(strategy.isPresent()) {

        }
        return  "hello";
    }
}
