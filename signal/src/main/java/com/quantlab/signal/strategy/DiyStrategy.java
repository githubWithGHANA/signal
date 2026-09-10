package com.quantlab.signal.strategy;

import com.quantlab.common.dao.StrategyStatusResignalDao;
import com.quantlab.common.entity.Signal;
import com.quantlab.common.entity.Strategy;
import com.quantlab.common.repository.StrategyRepository;
import com.quantlab.common.utils.staticstore.dropdownutils.ExecutionTypeMenu;
import com.quantlab.common.utils.staticstore.dropdownutils.SignalStatus;
import com.quantlab.common.utils.staticstore.dropdownutils.Status;
import com.quantlab.signal.service.CugUsersService;
import com.quantlab.signal.service.DiyStrategyService;
import com.quantlab.signal.service.GrpcService;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

import static com.quantlab.common.utils.staticstore.AppConstants.*;

@Service("DiyStrategy")
public class DiyStrategy implements StrategiesImplementation<DiyStrategy> {

    private static final Logger logger = LogManager.getLogger(DiyStrategy.class);


    @Autowired
    private SignalService signalService;

    @Autowired
    private GrpcService grpcService;

    @Autowired
    private DiyStrategyService diyStrategyService;

    @Autowired
    private StrategyRepository strategyRepository;

    @Autowired
    CugUsersService cugUsersService;

    @Override
    public Signal runStrategy(Strategy strategy) {
        logger.info("Run Strategy is called: {} ", strategy.getId());
        // has to fetch the master
        Signal signal = new Signal();
        try{
             signal = signalService.createDiySignal(strategy);
            if (signal != null && !strategy.getExecutionType().equalsIgnoreCase(ExecutionTypeMenu.PAPER_TRADING.getKey())
                    && (cugUsersService.isUserInCug(signal.getAppUser().getTenentId()))){
                grpcService.sendSignal(signal);
            }
        }catch (Exception e){
            logger.error(e);
            throw new RuntimeException("### internal error deploying  diy strategy: "+strategy.getName()+" ,"+e.getMessage());
        }
        return signal;
    }

    @Override
    public void exitStrategy(Strategy strategy) {
        try {
            logger.info("Exit Strategy invoked -> id: {} | inside exit method", strategy.getId());
            Signal newSignal = signalService.createExit(strategy);
            if (newSignal == null) {
                logger.error("Failed to create new signal");
                throw new RuntimeException("Failed to create new signal");
            }
            if (newSignal != null && !strategy.getExecutionType().equalsIgnoreCase(ExecutionTypeMenu.PAPER_TRADING.getKey())
                    && (cugUsersService.isUserInCug(newSignal.getAppUser().getTenentId()))){

                grpcService.sendExitSignal(newSignal);
            }
            return;
        }catch (Exception e){
            logger.error(e);
        }
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void check(Strategy strategy) {

        boolean entry = diyStrategyService.checkDiyEntry(strategy);
        if (entry) {
            Optional<Strategy> newStrategy = strategyRepository.findById(strategy.getId());
            if (newStrategy.isPresent() && newStrategy.get().getStatus().equalsIgnoreCase(Status.ACTIVE.getKey())) {
                runStrategy(newStrategy.get());
            }
        }
        // check if the strategy is not active then need to check with the strategy is live
        else if (strategy.getStatus().equalsIgnoreCase(SignalStatus.LIVE.getKey())) {
            boolean exitStatus = diyStrategyService.checkDiyExit(strategy);
            if (exitStatus) {
                Optional<StrategyStatusResignalDao> strategySnapshot = strategyRepository.findStatusAndCountsById(strategy.getId());
                if (strategySnapshot.isPresent() && strategySnapshot.get().getStatus().equalsIgnoreCase(Status.LIVE.getKey())) {
                    logger.info("exit for the strategy = {}", strategy.getId());
                    exitStrategy(strategy);
                }
            }
        }
    }
}
