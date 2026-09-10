package com.quantlab.client.service;

import com.quantlab.client.dto.*;
import com.quantlab.client.utils.DiyUtils;
import com.quantlab.client.websockets.PNLSocketUI;
import com.quantlab.common.entity.*;
import com.quantlab.common.exception.custom.*;
import com.quantlab.common.loggingService.DeploymentErrorService;
import com.quantlab.common.repository.*;
import com.quantlab.common.utils.staticstore.dropdownutils.*;
import com.quantlab.common.utils.staticstore.dropdownutils.Status;
import com.quantlab.common.utils.staticstore.dropdownutils.StrategyCategoryType;
import com.quantlab.signal.service.AuthService;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.IntStream;

import static com.quantlab.common.utils.staticstore.AppConstants.*;

@Service
@Transactional
public class DiyService {

    private static final Logger logger = LogManager.getLogger(DiyService.class);

    @PersistenceContext
    private EntityManager entityManager;

    @Autowired
    DiyUtils diyUtils;

    @Autowired
    StrategyRepository strategyRepository;

    @Autowired
    StrategyLegRepository strategyLegRepository;

    @Autowired
    AppUserRepository appUserRepository;

    @Autowired
    UnderlyingRespository underlyingRespository;

    @Autowired
    UserStrategyService userStrategyService;

    @Autowired
    EntryDaysRespository entryDaysRespository;

    @Autowired
    AuthService authService;

    @Autowired
    DeploymentErrorService deploymentErrorService;

    @Transactional(readOnly = true)
    public DiyDropDownDto getDiyDropdownList() {
        logger.info("Fetching DIY dropdown list");
        List<Underlying> underlyingList = underlyingRespository.findAll();
        List<EntryDays> entryDaysList = entryDaysRespository.findAll(Sort.by(Sort.Order.asc("id")));
        DiyDropDownDto diyDropDownDto = new DiyDropDownDto();
        diyDropDownDto.setStrategyType(Arrays.stream(StrategyType.values()).map(type -> {
            return new SelectionMenuStringDto(type.getKey(), type.getLabel());
        }).toList());
        if(!entryDaysList.isEmpty()){
            diyDropDownDto.setDaysMenu(entryDaysList.stream().map(list -> {
                return new SelectionMenuLongDto(list.getId(),list.getDay());
            }).toList());
        }
        diyDropDownDto.setOrder(Arrays.stream(OrderTypeMenu.values()).map(type -> {
            return new SelectionMenuStringDto(type.getKey(), type.getLabel());
        }).toList());
        diyDropDownDto.setSegmentType(Arrays.stream(SegmentType.values()).map(type -> {
            return new SelectionMenuStringDto(type.getKey(), type.getLabel());
        }).toList());
        diyDropDownDto.setProfitMtm(Arrays.stream(MtmMenu.values()).map(type -> {
            return new SelectionMenuStringDto(type.getKey(), type.getLabel());
        }).toList());
        diyDropDownDto.setExpiryType(Arrays.stream(ExpiryType.values()).map(type -> {
            return new SelectionMenuStringDto(type.getKey(), type.getLabel());
        }).toList());
        diyDropDownDto.setLot(IntStream.range(0,100).mapToObj(type -> {
            return new SelectionMenuLongDto((long) type+1, String.valueOf(type+1));
        }).toList());
        diyDropDownDto.setStrikeSelection(Arrays.stream(StrikeSelectionMenu.values()).map(type -> {
            return new SelectionMenuStringDto(type.getKey(), type.getLabel());
        }).toList());
        diyDropDownDto.setStrikeType(Arrays.stream(StrikeTypeMenu.values()).map(type -> {
            return new SelectionMenuStringDto(type.getKey(), type.getLabel());
        }).toList());
        if(!underlyingList.isEmpty()) {
            diyDropDownDto.setUnderlyingMenu(underlyingList.stream().map(item -> {
                return new SelectionMenuLongDto(item.getId(), item.getName());
            }).toList());
        }
        diyDropDownDto.setExitAfterEntry(Arrays.stream(ExitAfterEntryMenu.values()).map(type -> {
            return new SelectionMenuIntegerDto(type.getKey(), type.getLabel());
        }).toList());
        diyDropDownDto.setTgt(Arrays.stream(TgtMenu.values()).map(type -> {
            return new SelectionMenuStringDto(type.getKey(), type.getLabel());
        }).toList());
        diyDropDownDto.setTrl(Arrays.stream(TslMenu.values()).map(type -> {
            return new SelectionMenuStringDto(type.getKey(), type.getLabel());
        }).toList());
        diyDropDownDto.setExitOnExpiry(Arrays.stream(ExitExpiry.values()).map(type -> {
            return new SelectionMenuStringDto(type.getKey(), type.getLabel());
        }).toList());
        diyDropDownDto.setExecutionType(Arrays.stream(ExecutionTypeMenu.values()).map(type -> {
            return new com.quantlab.common.dto.SelectionMenuStringDto(type.getKey(),type.getLabel());
        }).toList());
        diyDropDownDto.setDescriptions(DiyUtils.mapToStrategyDescriptionInfo());
        diyDropDownDto.setPositionType(Arrays.stream(OptionType.values()).map(type -> {
            return new SelectionMenuStringDto(type.getKey(), type.getLabel());
        }).toList());

        logger.info("Dropdown list populated successfully");
        return diyDropDownDto;
    }

    @Transactional
    public AllStrategiesResDto updateStrategy(String clientId, DiyReqDto  diyReqDto) {
        try{
            logger.info("Processing update DIY strategy: {}", diyReqDto);
            AppUser appUser = authService.getUserFromCLientId(clientId);

            Optional<Strategy> strategyOptional = strategyRepository.findById(diyReqDto.getStrategyId());
            if(strategyOptional.isEmpty()) {
                throw new StrategyNotFoundException("Strategy not found with ID : " + diyReqDto.getStrategyId());
            }
            Strategy strategy = strategyOptional.get();
            if(!Objects.equals(strategy.getAppUser().getId(), appUser.getId())) {
                throw new Exception("You cannot update other user's strategy");
            }

            // Check if the strategy name already exists
            Optional<Strategy> strategyByName = strategyRepository.findByNameAndAppUser(diyReqDto.getStrategyName().trim(), appUser);
            if (strategyByName.isPresent() && !Objects.equals(strategyByName.get().getId(), diyReqDto.getStrategyId())) {
                throw new StrategyAlreadyExistsException("Strategy name already exists: " + diyReqDto.getStrategyName());
            }
            if (Status.LIVE.getKey().equalsIgnoreCase(strategy.getStatus())) {
                throw new StrategyAlreadyLiveException("Cannot Modify Live strategy, exit/unsubscribe to modify: " + diyReqDto.getStrategyName());
            }


            // Update strategy main details
            diyUtils.getStrategyFromDIY(diyReqDto, strategy);

            if(diyReqDto.getLegs().isEmpty()){
                strategyLegRepository.deleteByStrategyId(diyReqDto.getStrategyId());
                strategy.setStrategyLeg(new ArrayList<>());
            }else{
                List<Long> existingIds = diyReqDto.getLegs().stream().filter(leg -> leg.getLegId() != null).map(DiyLegDTO::getLegId).toList();

                strategyLegRepository.deleteEntitiesNotInAndByStrategyId(existingIds, diyReqDto.getStrategyId());

                List<StrategyLeg> updatedLegs = new ArrayList<>();
                for (DiyLegDTO leg : diyReqDto.getLegs()) {
                    if(leg.getLegId() == null){
                        StrategyLeg newLeg = diyUtils.getStrategyLegFromDIY(leg);
                        newLeg.setStrategy(strategy);
                        newLeg.setAppUser(appUser);
                        newLeg.setUserAdmin(appUser.getAdmin());
                        updatedLegs.add(newLeg);
                    }else{
                        Optional<StrategyLeg> existingLegOpt = strategy.getStrategyLeg().stream().filter(item -> Objects.equals(item.getId(), leg.getLegId())).findFirst();
                        if(existingLegOpt.isEmpty()){
                            throw new Exception("Leg not found with ID : " + leg.getLegId());
                        }
                        StrategyLeg existingLeg = existingLegOpt.get();
                        diyUtils.updateStrategyLegFromDIY(leg, existingLeg);
                        updatedLegs.add(existingLeg);
                    }
                }

                strategy.setStrategyLeg(updatedLegs);
            }
            List<EntryDays> entryDaysList = strategy.getEntryDetails().getEntryDays();
            boolean isTodayEntryDay = entryDaysList.stream()
                    .anyMatch(entryDay -> entryDay.getDay().equalsIgnoreCase(LocalDate.now().getDayOfWeek().name()));

            if (isTodayEntryDay && !Status.LIVE.getKey().equalsIgnoreCase(strategy.getStatus())) {
                strategy.setStatus(Status.ACTIVE.getKey());
            }else if(!Status.LIVE.getKey().equalsIgnoreCase(strategy.getStatus())) {
                strategy.setStatus(Status.STANDBY.getKey());
            }

            strategy = strategyRepository.save(strategy);
            deploymentErrorService.saveStrategyUpdateLogs(strategy, "Strategy is customized and Subscribed");

            return userStrategyService.getAllStrategies(clientId, false);

        } catch (InvalidExpiryLabelException |
                 UserNotFoundException |
                 StrategyAlreadyLiveException |
                 StrategyAlreadyExistsException |
                 UnderlyingNotFoundException e) {
            logger.error("Error saving DIY strategy: {}", e.getMessage(), e);
            throw e;
        } catch (Exception e) {
            logger.error("Unexpected error saving DIY strategy", e);
            throw new RuntimeException("Unexpected error saving DIY strategy", e);
        }
    }

    @Transactional
    public AllStrategiesResDto saveStrategy(String clientId, DiyReqDto diyReqDto) {
        try {
            LocalDate date = LocalDate.now();
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd MMM yyyy", Locale.ENGLISH);

            logger.info("Processing save DIY strategy: {}", diyReqDto);
            AppUser appUser = authService.getUserFromCLientId(clientId);
            if (appUser == null) {
                throw new UserNotFoundException("User not found for client ID: " + clientId);
            }

            // Check if the strategy name already exists
            Optional<Strategy> strategyOptional = strategyRepository.findByNameAndAppUser(diyReqDto.getStrategyName().trim(), appUser);
            if (strategyOptional.isPresent()) {
                throw new StrategyAlreadyExistsException("Strategy name already exists: " + diyReqDto.getStrategyName());
            }

            // Create the new strategy object from the DTO
            Strategy strategy = new Strategy();
            diyUtils.getStrategyFromDIY(diyReqDto, strategy);
            strategy.setAppUser(appUser);  // Associate user with the strategy
            strategy.setTypeOfStrategy(StrategyCategoryType.DIY.getKey());
            strategy.setUserAdmin(appUser.getAdmin());
            strategy.setCategory(StrategyCategoryType.DIY.getKey());
            strategy.setUnderlingType(DEFAULT_UNDERLING_TYPE);
            strategy.setAtmType(diyReqDto.getLegs().get(0).getStrikeSelection());
            strategy.setStopLoss(0L);
            strategy.setTarget(0L);
            strategy.setReSignalCount(1);
            strategy.setMultiplier(1L);
            //we need to stop assigning deployed on date when we change to "save" and "save and subscribe"
//            strategy.setLastDeployedOn(date.format(formatter));

            // Handle strategy legs if they exist
            List<StrategyLeg> strategyLegs = new ArrayList<>();
            if (diyReqDto.getLegs() != null && !diyReqDto.getLegs().isEmpty()) {
                for (DiyLegDTO leg : diyReqDto.getLegs()) {
                    StrategyLeg strategyLeg = diyUtils.getStrategyLegFromDIY(leg);
                    strategyLeg.setStrategy(strategy);
                    strategyLeg.setAppUser(appUser);
                    strategyLeg.setUserAdmin(appUser.getAdmin());
                    strategyLegs.add(strategyLeg);
                }
            }

            // Save strategy and legs
            strategy.setStrategyLeg(strategyLegs);
            strategy.setSubscription(SubscriptionStatus.END.getKey());
            List<EntryDays> entryDaysList = strategy.getEntryDetails().getEntryDays();
            DayOfWeek today = LocalDate.now().getDayOfWeek();
            boolean isTodayEntryDay = entryDaysList.stream()
                    .anyMatch(entryDay -> entryDay.getDay().equalsIgnoreCase(today.name()));

            if (!SubscriptionStatus.START.getKey().equalsIgnoreCase(strategy.getSubscription())) {
                strategy.setStatus(Status.INACTIVE.getKey());
            }else if (isTodayEntryDay && !Status.LIVE.getKey().equalsIgnoreCase(strategy.getStatus())) {
                strategy.setStatus(Status.ACTIVE.getKey());
            }else if(!Status.LIVE.getKey().equalsIgnoreCase(strategy.getStatus())) {
                strategy.setStatus(Status.STANDBY.getKey());
            }
            strategy = strategyRepository.save(strategy);
            deploymentErrorService.saveStrategyUpdateLogs(strategy, "Strategy is Save and Subscribed");

            // Return all strategies after saving
            PNLSocketUI.dirtyUsers.add(String.valueOf(strategy.getAppUser().getId()));
            return userStrategyService.getAllStrategies(clientId, false);

        } catch (InvalidExpiryLabelException |
                 UserNotFoundException |
                 StrategyAlreadyExistsException |
                 UnderlyingNotFoundException e) {
            logger.error("Error saving DIY strategy: {}", e.getMessage(), e);
            throw e;
        } catch (Exception e) {
            logger.error("Unexpected error saving DIY strategy", e);
            throw new RuntimeException("Unexpected error saving DIY strategy", e);
        }
    }

    public DiyReqDto getDiyById(Long id){
        try{
            logger.info("Fetching DIY for the ID: {}", id);
            DiyReqDto diyReqDto = new DiyReqDto();
            Optional<Strategy> strategy = strategyRepository.findById(id);
            if(strategy.isEmpty()){
                throw new StrategyNotFoundException("No strategy found with ID: " + id);
            }
            diyUtils.mapStrategyToDiyReqDto(diyReqDto,strategy.get());
            return diyReqDto;
        } catch (StrategyNotFoundException e) {
            logger.error("Strategy not found for ID: {}", id, e);
            throw e;
        } catch (Exception e) {
            logger.error("Unexpected error occurred while fetching DIY for ID: {}", id, e);
            throw new RuntimeException("Error fetching DIY strategy. Please try again later.");
        }
    }
}
