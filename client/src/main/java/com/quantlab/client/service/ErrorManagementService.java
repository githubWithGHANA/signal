package com.quantlab.client.service;

import com.quantlab.common.entity.*;
import com.quantlab.common.exception.custom.StrategyNotFoundException;
import com.quantlab.common.repository.*;
import com.quantlab.common.utils.staticstore.dropdownutils.*;
import com.quantlab.common.utils.staticstore.dropdownutils.Status;
import com.quantlab.signal.dto.redisDto.MarketData;
import com.quantlab.signal.service.AuthService;
import com.quantlab.signal.service.CugUsersService;
import com.quantlab.signal.service.GrpcErrorService;
import com.quantlab.signal.service.GrpcService;
import com.quantlab.signal.service.redisService.TouchLineService;
import com.quantlab.signal.strategy.SignalService;
import com.quantlab.signal.utils.CommonUtils;
import com.quantlab.signal.utils.DiyStrategyCommonUtil;
import com.quantlab.signal.web.service.MarketDataFetch;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

import static com.quantlab.common.utils.staticstore.AppConstants.*;
import static com.quantlab.signal.utils.staticdata.StaticStore.EXCEPTION_DATE;

@Service
@Transactional
public class ErrorManagementService {
    private static final Logger logger = LoggerFactory.getLogger(ErrorManagementService.class);


    @Autowired
    StrategyRepository strategyRepository;

    @Autowired
    StrategyLegRepository strategyLegRepository;

    @Autowired
    TouchLineService touchLineService;

    @Autowired
    DiyStrategyCommonUtil diyStrategyCommonUtil;

    @Autowired
    MarketDataFetch marketDataFetch;

    @Autowired
    CommonUtils commonUtils;

    @Autowired
    SignalRepository signalRepository;

    @Autowired
    SignalService signalService;

    @Autowired
    AuthService authService;

    @Autowired
    GrpcErrorService grpcErrorService;

    @Autowired
    DeploymentErrorsRepository deploymentErrorsRepository;

    @Autowired
    private GrpcService grpcService;

    @Autowired
    CugUsersService cugUsersService;


    @Transactional
    public Map<String, String>  manuallyTraded(String clientId, Long strategyId) throws Exception {
        AppUser appUser = authService.getUserFromCLientId(clientId);
        Optional<Strategy> strategyOptional = strategyRepository.findById(strategyId);
        Map<String, String> validation = inputValidation(strategyOptional, appUser);
        if (!validation.containsKey("success"))
            return validation;
        Map<String, String> cancelValidation = new HashMap<>();
        Strategy strategy = strategyOptional.get();
        List<Signal> errorSignals = signalRepository.findByStrategyIdAndStatusOrderByIdAsc(strategy.getId(),Status.ERROR.getKey());
        if (!errorSignals.isEmpty()) {
                Signal signal = errorSignals.get(0);
            if (errorSignals.size() >1){
                cancelValidation = cancelSignals(errorSignals.subList(1, errorSignals.size()), strategy);
            }
                if (signal.getSignalLegs().isEmpty()) {
                    noLegsFoundErrorManagement(signal, "Unable to Manually Trade as no legs generated");
                    validation.put("fail", "Unable to Manually Trade as no legs generated");
                    return validation;
                }

                AtomicBoolean exitStatus = manuallyTradeSignal(signal);
                if (!cancelValidation.containsKey("fail")) {
                    if (exitStatus.get()) {
                        signal.setStatus(LegStatus.EXIT.getKey());
                        signal.getStrategy().setStatus(StrategyStatus.EXIT.getKey());
                        signal.getStrategy().setStatus(StrategyStatus.EXITED_MANUALLY.getKey());
                    } else {
                        signal.setStatus(Status.LIVE.getKey());
                        signal.getStrategy().setStatus(Status.LIVE.getKey());
                    }
                }
            grpcErrorService.placingOrderLogs("Strategy Set to Manually Traded", signal, signal.getStrategy().getStatus());

            signalRepository.saveAllAndFlush(errorSignals);
        }else {
            validation.put("fail", "unable to manually trade, no signal found in error");
            return validation;
        }
        return validation;
    }


    @Transactional
    public Map<String, String> orderCancelled(String clientId, Long strategyId) throws Exception {
        AppUser appUser = authService.getUserFromCLientId(clientId);
        Optional<Strategy> strategyOptional = strategyRepository.findById(strategyId);

        Map<String, String> validation = inputValidation(strategyOptional, appUser);
        if (!validation.containsKey("success"))
            return validation;

        Strategy strategy = strategyOptional.get();
        List<Signal> errorSignals = signalRepository.findByStrategyIdAndStatusOrderByIdAsc(strategy.getId(),Status.ERROR.getKey());

        if (!errorSignals.isEmpty() ){

            return cancelSignals(errorSignals, strategy);
            }else {
            strategy.setStatus(StrategyStatus.CANCELLED.getKey());
            strategyRepository.save(strategy);
            validation.put("fail", "Strategy cancelled, no Error Signals found");
            return validation;
        }
    }

    @Transactional
    public Map<String, String> cancelSignals(List<Signal> errorSignals, Strategy strategy) {
        Map<String, String> validation = new HashMap<>();
        for(Signal signal : errorSignals) {

            if (signal.getSignalLegs().isEmpty()) {
                noLegsFoundErrorManagement(signal, "No orders generated, cancelled the order successfully.");
                continue;
            }
            AtomicBoolean exitStatus = new AtomicBoolean(false);
            signal.getSignalLegs().forEach(leg -> {
                if (leg.getLegType().equalsIgnoreCase(LegStatus.EXIT.getKey())) {
                    exitStatus.set(true);
                }
            });
            if (exitStatus.get()){
                grpcErrorService.placingOrderLogs("Error cancelling exit order, try manually closing", signal, Status.ERROR.getKey());
                validation.put("fail", "Failed to cancel order, it is exit order");
                return validation;
            }
            else {
                if(signalIsPartiallyFilled(signal)){
                    signal.setStatus(Status.LIVE.getKey());
                    String legNames = signal.getSignalLegs().stream()
                            .filter(leg -> leg.getName() != null && leg.getBuySellFlag() != null && leg.getFilledQuantity() != null
                                    && leg.getFilledQuantity() > 0)
                            .map(leg -> leg.getBuySellFlag() + " " + leg.getName())
                            .collect(Collectors.joining(", "));
                    strategy.setStatus(Status.LIVE.getKey());
                    grpcErrorService.placingOrderLogs("Cancelling order(s): "+legNames, signal,  signal.getStrategy().getStatus());

                }
                else {
                    signal.setStatus(Status.CANCELLED.getKey());
                    strategy.setStatus(Status.CANCELLED.getKey());
                    grpcErrorService.placingOrderLogs("Cancelling strategy: no successfully placed orders found", signal,  signal.getStrategy().getStatus());

                }
            }
        }
        strategy.setManualExitType(ManualExit.ENABLED.getKey());
        strategyRepository.save(strategy);
        validation.put("success", "Strategy cancelled successfully");
        return validation;
    }

    private boolean signalIsPartiallyFilled(Signal s) {
        return s.getSignalLegs() != null &&
                s.getSignalLegs().stream().anyMatch(l -> l.getFilledQuantity() != null && l.getFilledQuantity() > 0);
    }


    @Transactional
    public Map<String, String> retrySignal(String clientId, Long strategyId){
        try {
            AppUser appUser = authService.getUserFromCLientId(clientId);
            Optional<Strategy> strategyOptional = strategyRepository.findById(strategyId);
            Map<String, String> validation = inputValidation(strategyOptional, appUser);
            if (!validation.containsKey("success"))
                return validation;

            int cancelStatus = 0;
            Strategy strategy = strategyOptional.get();
            List<Signal> errorSignals = signalRepository.findByStrategyIdAndStatusOrderByIdAsc(strategy.getId(),Status.ERROR.getKey());
            if (!errorSignals.isEmpty()) {
                Signal signal = errorSignals.get(0);
                if (errorSignals.size() > 1) {
                     cancelSignals(errorSignals.subList(1, errorSignals.size()), strategy);
                }
                boolean exitStatus = false;

                boolean failedSignal = true;
                if (!signal.getSignalLegs().isEmpty()) {
                    for (StrategyLeg leg : signal.getSignalLegs()) {
                        if (! leg.getExchangeStatus().equalsIgnoreCase(LegExchangeStatus.ERROR_PLACING_ORDER.getKey()) ||
                        leg.getFilledQuantity() > 0 || leg.getStatus().equalsIgnoreCase(Status.LIVE.getKey())) {
                            failedSignal = false;
                            break;
                        }
                    }
                    if (failedSignal) {
                        String description = "Retrying strategy as no live order found in signalId :"+signal.getId();

                        grpcErrorService.placingOrderLogs(description, signal, Status.RMS_ERROR.getKey());
                        updateStrategyToActive(strategy, signal);
                        logger.info("Retrying strategy as no live order found for strategyId: {}, in signalId :{}",strategy.getId(), signal.getId());
                        return validation;
                    }

                    for (StrategyLeg leg : signal.getSignalLegs()) {
                        if (leg.getLegType().equalsIgnoreCase(LegStatus.EXIT.getKey())) {
                            exitStatus = true;
                            break;
                        }
                    }
                    String legNames = signal.getSignalLegs().stream()
                            .filter(leg -> leg.getName() != null && leg.getBuySellFlag() != null && leg.getStatus().equalsIgnoreCase(LegStatus.EXCHANGE.getKey()))
                            .map(leg -> leg.getBuySellFlag()+" " +(leg.getQuantity()/ leg.getLotSize()) + " lots of " + leg.getName())
                            .collect(Collectors.joining(", "));

                    if (exitStatus) {
                        grpcErrorService.placingOrderLogs("Retrying Exit Order: " + legNames, signal, SignalStatus.EXIT.getKey());
                        createExitOrders(signal);
                    } else {
                        grpcErrorService.placingOrderLogs("Retrying Place Order: " + legNames, signal, Status.RETRYING.getKey());
                        updatePlaceOrders(signal);
                    }
                }else {
                    noLegsFoundRetrying(strategy, signal);
                }
            }else {
                noErrorSignalsFound(strategy);
            }
            return validation;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Transactional
    private void updateStrategyToActive(Strategy strategy, Signal signal) {
        signalRepository.updateSignalStatus(signal.getId(), Status.RMS_ERROR.getKey());
        strategyRepository.updateStrategyStatus(strategy.getId(), Status.ACTIVE.getKey());
    }

    @Transactional
    public void noLegsFoundRetrying(Strategy strategy, Signal signal) {
        signal.setStatus(SignalStatus.EXIT.getKey());
        isActiveDay(strategy);
        strategyRepository.updateStrategyStatus(strategy.getId(), strategy.getStatus());
    }


    public void updatePlaceOrders(Signal signal) {
        if (signal.getExecutionType().equalsIgnoreCase(ExecutionTypeMenu.LIVE_TRADING.getKey())
                && signal.getStrategy().getExecutionType().equalsIgnoreCase(ExecutionTypeMenu.LIVE_TRADING.getKey())
                && (cugUsersService.isUserInCug(signal.getAppUser().getTenentId()))){
            signal.setStatus(Status.LIVE.getKey());
            List<StrategyLeg> legsToUpdate = new ArrayList<>();
            for (StrategyLeg leg : signal.getSignalLegs()) {
                if(leg.getExchangeStatus().equalsIgnoreCase(LegExchangeStatus.ERROR_PLACING_ORDER.getKey())
                        && !leg.getFilledQuantity().equals(leg.getQuantity())){
                    leg.setStatus(LegType.OPEN.getKey());
                    leg.setExchangeStatus(LegExchangeStatus.CREATED.getKey());
                    assignLatestLTP(leg);
                    legsToUpdate.add(leg);
                }
            }
            strategyLegRepository.saveAllAndFlush(legsToUpdate);
            grpcService.sendSignal(signal);
        }else {
            signal.setStatus(SignalStatus.LIVE.getKey());
            signal.getSignalLegs().forEach((leg) ->{
                leg.setStatus(Status.LIVE.getKey());
            });
            signalRepository.saveAndFlush(signal);
        }
    }

    private void assignLatestLTP(StrategyLeg leg) {
        MarketData marketData = marketDataFetch.getInstrumentData(leg.getExchangeInstrumentId());
        if (marketData != null)
            leg.setPrice((long) (marketData.getLTP()*AMOUNT_MULTIPLIER));

    }

    public void createExitOrders(Signal signal) {
        if (signal.getExecutionType().equalsIgnoreCase(ExecutionTypeMenu.LIVE_TRADING.getKey())
                && signal.getStrategy().getExecutionType().equalsIgnoreCase(ExecutionTypeMenu.LIVE_TRADING.getKey())
                && (cugUsersService.isUserInCug(signal.getAppUser().getTenentId()))){
            signal.setStatus(Status.LIVE.getKey());
            signal.getSignalLegs().forEach((leg)->{
                if( leg.getLegType().equalsIgnoreCase(LegStatus.TYPE_EXIT.getKey())
                        && !Objects.equals(leg.getFilledQuantity(),leg.getQuantity())
                        && leg.getExchangeStatus().equalsIgnoreCase(LegExchangeStatus.ERROR_PLACING_ORDER.getKey())){
                    leg.setStatus(LegType.OPEN.getKey());
                    leg.setExchangeStatus(LegExchangeStatus.CREATED.getKey());
                    assignLatestLTP(leg);
                }});
            grpcService.sendExitSignal(signal);
        }else {
            signal.setStatus(SignalStatus.EXIT.getKey());
            signal.getSignalLegs().forEach((leg) ->{
                leg.setStatus(SignalStatus.EXIT.getKey());
            });
            signalRepository.saveAndFlush(signal);
        }
    }

    private Signal checkMultipleSignalErrorCase(List<Signal> errorSignals) {
        if (errorSignals.size() >1){
            errorSignals.get(0);
        }
        return null;
    }


    private Map<String, String> inputValidation(Optional<Strategy> strategyOptional, AppUser appUser) {
        Map<String, String> result = new HashMap<>();

        if (strategyOptional.isEmpty()) {
            result.put("fail", "Strategy not found with ID");
            return result;
        }

        if (!Objects.equals(strategyOptional.get().getAppUser().getId(), appUser.getId())) {
            result.put("fail", "You cannot update another user's strategy");
            return result;
        }

        Strategy strategy = strategyOptional.get();

        if (!Objects.equals(strategy.getStatus(), Status.ERROR.getKey())) {
            result.put("fail", "Strategy is already Live");
            return result;
        }

        if (LocalTime.now().isAfter(LocalTime.of(15, 30)) || LocalTime.now().isBefore(LocalTime.of(9, 15))) {
            result.put("fail", "Market has already closed for the day, cannot manage errors");
            return result;
        }

        result.put("success", "Validation passed");
        return result;
    }


    @Transactional
    public void noLegsFoundErrorManagement(Signal signal, String description){
        signal.setStatus(SignalStatus.EXIT.getKey());
        signal.getStrategy().setStatus(SignalStatus.EXIT.getKey());
        signal.getStrategy().setStatus(StrategyStatus.EXIT.getKey());
        signalRepository.saveAndFlush(signal);
        grpcErrorService.placingOrderLogs(description, signal, SignalStatus.EXIT.getKey());

    }

    AtomicBoolean manuallyTradeSignal(Signal signal) {
        AtomicBoolean exitStatus =new AtomicBoolean(false);
        int exitLegsCount  = 0;
        int liveLegsCount = 0;
        for(StrategyLeg leg : signal.getSignalLegs()){

            if (leg.getExchangeStatus() != null && leg.getExchangeStatus().equalsIgnoreCase(LegExchangeStatus.ERROR_PLACING_ORDER.getKey())) {

                leg.setExchangeStatus(LegExchangeStatus.MANUALLY_TRADED.getKey());
                leg.setFilledQuantity(leg.getQuantity());
                if (leg.getLegType().equalsIgnoreCase(LegStatus.EXIT.getKey())) {
                    leg.setStatus(LegStatus.EXIT.getKey());
                } else {
                    leg.setStatus(Status.LIVE.getKey());
                }
            }

            if (leg.getLegType().equalsIgnoreCase(LegStatus.EXIT.getKey())) {
                exitLegsCount++;
            } else {
                liveLegsCount++;
            }
        }
        if (exitLegsCount >= liveLegsCount)
            exitStatus.set(true);

        return exitStatus;
    }

    public void isActiveDay(Strategy strategy) {
        String currentDayName = LocalDate.now().getDayOfWeek().name().substring(0, 1).toUpperCase() + LocalDate.now().getDayOfWeek().name().substring(1).toLowerCase();

        ArrayList<String> availableDays = new ArrayList<>();
        if (strategy.getEntryDetails().getEntryDays() != null) {
            for (EntryDays entryDay : strategy.getEntryDetails().getEntryDays()) {
                availableDays.add(entryDay.getDay());
            }
        }
        if (dayIsPresent(availableDays, currentDayName)) {
            strategy.setStatus(Status.ACTIVE.getKey());
        } else if (!Status.INACTIVE.getKey().equalsIgnoreCase(strategy.getStatus())) {
            strategy.setStatus(Status.STANDBY.getKey());
        }
    }

    public boolean dayIsPresent(ArrayList<String> allDays,String currentDay){
        for (String activeDay:allDays){
            if (activeDay.equalsIgnoreCase(currentDay) || (EXCEPTION_DATE != null && EXCEPTION_DATE.equals(LocalDate.now())))
                return true;
        }
        return false;

    }

    @Transactional
    public boolean noErrorSignalsFound(Strategy strategy){
        List<Signal> signalOptional = signalRepository.findByStrategyIdAndStatus(strategy.getId(), Status.LIVE.getKey());
        if (signalOptional.isEmpty()) {
            Signal signal = new Signal();
            signal.setStrategy(strategy);
            signal.setAppUser(strategy.getAppUser());
            grpcErrorService.placingOrderLogs("Retrying Strategy, no error signals found, checking entry condition", signal, Status.ACTIVE.getKey());
            isActiveDay(strategy);

        }else {
            signalOptional.forEach(signal -> {
                grpcErrorService.placingOrderLogs("Live signal Found when retrying, changing strategy to live", signal, Status.LIVE.getKey());
            });
            strategy.setStatus(Status.LIVE.getKey());
            strategyRepository.updateStrategyStatus(strategy.getId(), strategy.getStatus());

        }
        return true;
    }
}
