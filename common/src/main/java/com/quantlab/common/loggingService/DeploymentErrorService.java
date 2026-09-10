package com.quantlab.common.loggingService;

import com.quantlab.common.entity.DeploymentErrors;
import com.quantlab.common.entity.Strategy;
import com.quantlab.common.repository.DeploymentErrorsRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Collections;

@Service
public class DeploymentErrorService {

    private static final Logger logger = LoggerFactory.getLogger(DeploymentErrorService.class);

    @Autowired
    DeploymentErrorsRepository deploymentErrorsRepository;

    @Async
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void saveStrategyUpdateLogs(Strategy strategy, String description){
        try {
            DeploymentErrors deploymentErrors = new DeploymentErrors();
            deploymentErrors.setStrategy(strategy);
            deploymentErrors.setDescription(Collections.singletonList(description));
            deploymentErrors.setStatus(strategy.getStatus());
            deploymentErrors.setAppUser(strategy.getAppUser());
            deploymentErrors.setDeployedOn(Instant.now());
            deploymentErrorsRepository.save(deploymentErrors);

        } catch (RuntimeException e) {
            logger.error(e.getMessage());
        }
    }
}
