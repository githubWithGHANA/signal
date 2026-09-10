package com.quantlab.signal.strategy.driver;

import com.quantlab.common.entity.Strategy;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class StrategyControllerService {


    @Autowired
    private Parser parser;

    @Autowired
    TaskManager taskManager;

    public void checkStrategy(Strategy strategy){
       // logger.info("Checking strategy... for the strategy: " + strategy.getId());
        ProcessStrategy processStrategy = new ProcessStrategy(parser,strategy);
        taskManager.submitTask(processStrategy);
    }

}
