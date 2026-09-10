package com.quantlab.signal.sheduler;

import com.quantlab.common.entity.Signal;
import com.quantlab.common.entity.Strategy;
import com.quantlab.common.repository.SignalRepository;
import com.quantlab.common.repository.StrategyRepository;
import com.quantlab.signal.service.GrpcService;
import com.quantlab.signal.service.StrategyService;
import com.quantlab.signal.strategy.DeltaNeutralStrategy;
import com.quantlab.signal.strategy.DeltaNeutralStrategyHedge;
import com.quantlab.signal.strategy.DiyStrategy;
import com.quantlab.signal.strategy.SignalService;
import com.quantlab.signal.strategy.driver.Parser;
import com.quantlab.signal.strategy.driver.StrategyControllerService;
import com.quantlab.signal.strategy.driver.TaskManager;
import org.hibernate.Hibernate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.FutureTask;

@Component
public class Schedule {

    private final static org.apache.logging.log4j.Logger logger = org.apache.logging.log4j.LogManager.getLogger(Schedule.class);

    @Autowired
    private StrategyRepository strategyRepository;


    @Autowired
    private SignalRepository signalRepository;


    @Autowired
    private StrategyControllerService strategyControllerService;

    @Autowired
    private SignalService signalService;

    @Autowired
    private GrpcService grpcService;

    @Autowired
    private Parser parser;

    @Autowired
    private TaskManager taskManager;

    private volatile List<Strategy> strategyCache = List.of();

//    @Scheduled(fixedRate = 5000)
    public void refreshStrategies() {
        if (LocalTime.now().isAfter(LocalTime.of(9, 15)) && LocalTime.now().isBefore(LocalTime.of(15, 30))) {

            List<Strategy> strategies = strategyRepository.findAllByDeleteIndicatorAndSubscription("N", "Y");
            strategyCache = List.copyOf(strategies); // immutable snapshot
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW )
    public void shedule() {
        long startTime = System.currentTimeMillis();

        List<Strategy> strategies = strategyRepository.findAllByDeleteIndicatorAndSubscription("N","Y");
        for (Strategy strategy : strategies) {
            if (strategy.getId() > 22) {
                strategyControllerService.checkStrategy(strategy);
            }
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void scheduleTask(){

        logger.info("Schedule task started");
        List<FutureTask<Void>> tasks = new ArrayList<>();
        Instant startTime = Instant.now();
        logger.info("Big Query to fetch all strategies started at: {}", Instant.now());
        List<Strategy> strategies = strategyCache;

        logger.info("Big Query to fetch all strategies completed at: {} size of the strategies is   : {}", Instant.now() , strategies.size());
        for (Strategy strategy : strategies) {
            FutureTask<Void> task = new FutureTask<>(() -> {
                parser.check(strategy);
                return null;
            });
            taskManager.submitTask(task);
            tasks.add(task);
        }
        for (FutureTask<Void> task : tasks) {
            try {
                task.get();
            } catch (InterruptedException | ExecutionException e) {
                logger.error("Error executing task: {} ,  for the strategy  " , e.getMessage());

//                e.printStackTrace();
            }
        }
        Instant endTime = Instant.now();
        long duration = endTime.toEpochMilli() - startTime.toEpochMilli();
        logger.info("Schedule task completed in {} ms", duration);

    }

}
