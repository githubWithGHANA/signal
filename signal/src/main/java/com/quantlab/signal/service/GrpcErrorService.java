package com.quantlab.signal.service;

import com.google.protobuf.ProtocolStringList;
import com.quantlab.common.emailService.EmailService;
import com.quantlab.common.entity.DeploymentErrors;
import com.quantlab.common.entity.Signal;
import com.quantlab.common.repository.DeploymentErrorsRepository;
import com.quantlab.common.repository.SignalRepository;
import com.quantlab.common.repository.StrategyLegRepository;
import com.quantlab.common.repository.StrategyRepository;
import com.quantlab.common.utils.staticstore.dropdownutils.LegExchangeStatus;
import com.quantlab.common.utils.staticstore.dropdownutils.LegStatus;
import com.quantlab.common.utils.staticstore.dropdownutils.Status;
import com.quantlab.signal.dto.TrOrdersDto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.quantlab.common.utils.staticstore.AppConstants.*;
import static org.apache.commons.collections4.IteratorUtils.forEach;

@Component
public class GrpcErrorService {

    private static final Logger logger = LoggerFactory.getLogger(GrpcErrorService.class);

    @Autowired
    DeploymentErrorsRepository deploymentErrorsRepository;

    @Autowired
    SignalRepository signalRepository;

    @Autowired
    StrategyRepository strategyRepository;

    @Autowired
    StrategyLegRepository strategyLegRepository;

    @Autowired
    EmailService emailService;

    // XTS response handling
    @Async
    @Transactional
    public void processGrpcResponse(com.market.proto.xts.PlaceOrderResponse res, Signal signal) {
        try {

            if (res == null){
                saveStrategyAndSignalAsError(signal);
                DeploymentErrors deploymentErrors = new DeploymentErrors();
                deploymentErrors.setStatus(Status.ERROR.getKey());
                deploymentErrors.setStrategy(signal.getStrategy());
                deploymentErrors.setAppUser(signal.getAppUser());
                deploymentErrors.setDeployedOn(Instant.now());
                deploymentErrors.setDescription(Collections.singletonList("Error: Unable to connect to server"));
                deploymentErrorsRepository.save(deploymentErrors);
                updateExchangeStatus(signal, LegExchangeStatus.ERROR_PLACING_ORDER.getKey());
            }
            else if (res.getStatus().equalsIgnoreCase(Status.FAILURE.getKey())) {
                signal.setStatus(Status.ERROR.getKey());
                signal.getStrategy().setStatus(Status.ERROR.getKey());
                saveStrategyAndSignalAsError(signal);
                DeploymentErrors deploymentErrors = new DeploymentErrors();
                deploymentErrors.setStatus(res.getStatus());
                deploymentErrors.setStrategy(signal.getStrategy());
                deploymentErrors.setAppUser(signal.getAppUser());
                deploymentErrors.setDeployedOn(Instant.now());
                deploymentErrors.setErrorCode(res.getErrorsList().toString());
                deploymentErrors.setDescription(fetchDescription(res.getErrorsList()));
                deploymentErrorsRepository.save(deploymentErrors);
            }
            else{
                updateExchangeStatus(signal, LegExchangeStatus.INTERACTIVE_PLACED.getKey());
            }
        } catch (Exception e) {
            handleGrpcFailure(e, signal);
        }
    }

    // TR response handling
    @Async
    @Transactional
    public void processGrpcResponse(com.market.proto.tr.PlaceOrderResponse res, Signal signal, List<TrOrdersDto> trOrders) {
        try
        {
            if (res == null){
                saveStrategyAndSignalAsError(signal);
                DeploymentErrors deploymentErrors = new DeploymentErrors();
                deploymentErrors.setStatus(Status.ERROR.getKey());
                deploymentErrors.setStrategy(signal.getStrategy());
                deploymentErrors.setAppUser(signal.getAppUser());
                deploymentErrors.setDeployedOn(Instant.now());
                deploymentErrors.setDescription(Collections.singletonList("Error: Unable to connect to server"));
                deploymentErrorsRepository.save(deploymentErrors);
                updateExchangeStatus(signal, LegExchangeStatus.ERROR_PLACING_ORDER.getKey());
            }
            else if (res.getStatus().equalsIgnoreCase(Status.FAILURE.getKey()) || (res.getStatus().equalsIgnoreCase("success") && res.getResponse(0).getStatus().equalsIgnoreCase("not_ok"))) {
                saveStrategyAndSignalAsError(signal);
                DeploymentErrors deploymentErrors = new DeploymentErrors();
                deploymentErrors.setStatus(res.getStatus());
                deploymentErrors.setStrategy(signal.getStrategy());
                deploymentErrors.setAppUser(signal.getAppUser());
                deploymentErrors.setDeployedOn(Instant.now());
                deploymentErrors.setErrorCode(res.getErrorsList().toString());
                deploymentErrors.setDescription(fetchDescription(res.getErrorsList()));
                deploymentErrorsRepository.save(deploymentErrors);
                updateExchangeStatus(signal, LegExchangeStatus.ERROR_PLACING_ORDER.getKey());
            }else {
                updateExchangeStatus(signal, LegExchangeStatus.INTERACTIVE_PLACED.getKey());
            }
        } catch (Exception e) {
            handleGrpcFailure(e, signal);
        }
    }

    @Transactional
    public void updateExchangeStatus( Signal signal, String exchangeStatus) {
        logger.info("Successfully sent order for signal ID: {}, exchangeStatus :{}", signal.getId(), exchangeStatus);
        signal.getSignalLegs().stream().filter(leg -> leg.getStatus().equalsIgnoreCase(LegStatus.EXCHANGE.getKey()))
                .forEach(leg -> {
                    leg.setExchangeStatus(exchangeStatus);
                    strategyLegRepository.updateExchangeStatus(leg.getId(), exchangeStatus);
                });
    }

    private void handleGrpcFailure(Exception e, Signal signal) {
        String errorMessage = e.getMessage() != null
                ? e.getMessage()
                : RUN_TIME_EXCEPTION + ": error while Sending Signal";
        logger.error("error while Sending Signal , {}", e.getMessage());
        logger.error("error in signal is  , {}", errorMessage);
        placingOrderLogs(ERROR_PLACING_ORDER_DESCRIPTION, signal, Status.ERROR.getKey());
    }

    @Async
    @Transactional
    public void placingOrderLogs(String errorMessage, Signal signal, String status) {
        if (signal.getId() != null) {
            try {
                if (status.equalsIgnoreCase(Status.ERROR.getKey())) {
                    saveStrategyAndSignalAsError(signal);
                    emailService.sendStrategyErrorEmail(signal.getStrategy(), errorMessage);
                }
                DeploymentErrors deploymentErrors = new DeploymentErrors();
                deploymentErrors.setStrategy(signal.getStrategy());
                deploymentErrors.setAppUser(signal.getAppUser());
                deploymentErrors.setDeployedOn(Instant.now());
                deploymentErrors.setStatus(status);
                deploymentErrors.setDescription(new ArrayList<>(List.of(errorMessage)));
                deploymentErrorsRepository.save(deploymentErrors);
            } catch (Exception e) {
                logger.error("error when saving DeploymentErrors logs for strategy = {}, error message = {}",
                        signal.getStrategy().getId(), errorMessage);
            }
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void saveStrategyAndSignalAsError(Signal signal) {
        updateExchangeStatus(signal, LegExchangeStatus.ERROR_PLACING_ORDER.getKey());
        signalRepository.updateSignalStatus(signal.getId(), Status.ERROR.getKey());
        strategyRepository.updateStrategyStatus(signal.getStrategy().getId(), Status.ERROR.getKey());

    }

    public static ArrayList<String> fetchDescription(ProtocolStringList errorResponses) {
        if (errorResponses == null) return null;

        ArrayList<String> descriptions = new ArrayList<>();
        for (String error : errorResponses) {
            Matcher matcher = Pattern.compile("\"description\":\"(.*?)\"").matcher(error);
            if (matcher.find()) {
                descriptions.add(ERROR_PLACING_ORDER_DESCRIPTION + matcher.group(1));
            }
        }

        return descriptions.isEmpty()
                ? new ArrayList<>(List.of("Error: Unable to place order; Please contact Admin"))
                : descriptions;
    }
}

