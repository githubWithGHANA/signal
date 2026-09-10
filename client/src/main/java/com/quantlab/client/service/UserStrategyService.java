package com.quantlab.client.service;

import com.quantlab.client.dto.*;
import com.quantlab.client.utils.UserStrategyUtils;
import com.quantlab.common.dao.StrategySummaryDao;
import com.quantlab.common.entity.*;
import com.quantlab.common.exception.custom.*;

import com.quantlab.common.loggingService.DeploymentErrorService;
import com.quantlab.common.repository.*;
import com.quantlab.common.utils.staticstore.dropdownutils.*;
import com.quantlab.common.utils.staticstore.dropdownutils.Status;
import com.quantlab.common.utils.staticstore.dropdownutils.StrategyCategoryType;
import com.quantlab.signal.dto.DeployedErrorDTO;
import com.quantlab.signal.dto.StrategyErrorDetails;
import com.quantlab.signal.dto.StrategyLegTableDTO;
import com.quantlab.signal.service.AuthService;
import com.quantlab.signal.sheduler.BodSchedule;
import com.quantlab.signal.sheduler.StrategyScheduler;
import com.quantlab.signal.strategy.driver.Parser;
import org.hibernate.Hibernate;
import org.modelmapper.ModelMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import com.quantlab.client.websockets.PNLSocketUI;

import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static com.quantlab.common.utils.staticstore.AppConstants.*;
import static com.quantlab.signal.utils.staticdata.StaticStore.EXCEPTION_DATE;


@Service
@Transactional
public class UserStrategyService {

    private static final Logger log = LoggerFactory.getLogger(UserStrategyService.class);

    @Autowired
    StrategyRepository strategyRepository;

    @Autowired
    StrategyLegRepository strategyLegRepository;

    @Autowired
    EntryDetailsRepository entryDetailsRepository;

    @Autowired
    ExitDetailsRepository exitDetailsRepository;

    @Autowired
    private ModelMapper modelMapper;

    @Autowired
    SignalRepository signalRepository;

    @Autowired
    AppUserRepository appUserRepository;

    @Autowired
    private Parser parser;

    @Autowired
    UnderlyingRespository underlyingRespository;

    @Autowired
    DeploymentErrorsRepository deploymentErrorsRepository;

    @Autowired
    EntryDaysRespository entryDaysRespository;

    @Autowired
    UserStrategyUtils userStrategyUtils;

    @Autowired
    AuthService authService;

    @Autowired
    UserSignalService userSignalService;

    @Autowired
    DeploymentErrorService deploymentErrorService;

    @Autowired
    StrategyScheduler strategyScheduler;

    @Autowired
    private ApplicationEventPublisher publisher;

    @Autowired
    BodSchedule bodSchedule;

    @Transactional
    public DeployDropdownDto getDeployDropdownList(boolean isLimited){
        log.info("Entering getDeployDropdownList() with isLimited: {}", isLimited);

        List<Underlying> underlyingList = underlyingRespository.findAll();
        List<EntryDays> entryDaysList = entryDaysRespository.findAll(Sort.by(Sort.Order.asc("id")));

        DeployDropdownDto deployDropdownDto = new DeployDropdownDto();
        // Conditionally filter ATM Types for deploySave
        List<AtmType> filteredAtmTypes = isLimited
                ? List.of(AtmType.SPOT_ATM, AtmType.FUTURE_ATM, AtmType.SYNTHETIC_ATM)
                : Arrays.asList(AtmType.values());

        deployDropdownDto.setAtmType(filteredAtmTypes.stream()
                .map(type -> new SelectionMenuStringDto(type.getKey(), type.getLabel()))
                .toList());

//        deployDropdownDto.setMultiplier(Arrays.stream(MultiplierMenu.values())
//                .map(type -> new SelectionMenuLongDto(type.getKey(), type.getLabel()))
//                .toList());
        List<SelectionMenuLongDto> multipliers = IntStream.rangeClosed(1, 100)
                .mapToObj(i -> new SelectionMenuLongDto((long) i, i + "x"))
                .toList();
        deployDropdownDto.setMultiplier(multipliers);

        if (!underlyingList.isEmpty()) {
            deployDropdownDto.setUnderlying(underlyingList.stream()
                    .map(list -> new SelectionMenuLongDto(list.getId(), list.getName()))
                    .toList());
        }

        deployDropdownDto.setOrder(Arrays.stream(StrategyType.values())
                .map(type -> new SelectionMenuStringDto(type.getKey(), type.getLabel()))
                .toList());

        deployDropdownDto.setExecutionType(Arrays.stream(ExecutionTypeMenu.values())
                .map(type -> new SelectionMenuStringDto(type.getKey(), type.getLabel()))
                .toList());

        deployDropdownDto.setExpiry(Arrays.stream(ExpiryType.values())
                .map(type -> new SelectionMenuStringDto(type.getKey(), type.getLabel()))
                .toList());

        if (!entryDaysList.isEmpty()) {
            deployDropdownDto.setEntryDays(entryDaysList.stream()
                    .map(list -> new SelectionMenuLongDto(list.getId(), list.getDay()))
                    .toList());
        }

        deployDropdownDto.setMtmType(Arrays.stream(MtmMenu.values())
                .map(type -> new SelectionMenuStringDto(type.getKey(), type.getLabel()))
                .toList());

        log.info("Exiting getDeployDropdownList()");
        return deployDropdownDto;
    }


    @Transactional
    public AllStrategiesResDto getAllStrategies(String clientId, boolean isLimited) {
        AppUser appUser = authService.getUserFromCLientId(clientId);
        AllStrategiesResDto res = new AllStrategiesResDto();

        // Lists to categorize strategies
        List<StrategyDto> diyList = new ArrayList<>();
        List<StrategyDto> inHouseList = new ArrayList<>();
        List<StrategyDto> prebuiltList = new ArrayList<>();
        List<StrategyDto> popularList = new ArrayList<>();

        try {
                // Fetch strategies based on user ID
                List<Strategy> strategies2 = strategyRepository.findAllByAppUser_IdOrderByIdAsc(appUser.getAppUserId());
            List<Strategy> strategies = strategies2.stream().filter(strategy -> !strategy.getIsHidden()).toList();
                log.info("Found {} strategies for user ID {}", strategies.size(), appUser.getAppUserId());
                    for (Strategy strategy : strategies) {
                    // Convert Strategy to StrategyDto
                    List<StrategyLeg> defaultLegs = strategyLegRepository.findDefaultStrategyLegs(strategy.getId());
                    StrategyDto strategyDto = userStrategyUtils.converToStrategyDto(strategy, defaultLegs);

                    // Categorize strategies based on category ID
                    userStrategyUtils.categorizeStrategy(strategy, strategyDto, diyList, inHouseList, prebuiltList, popularList);
                }

            // Assemble the response DTO with categorized strategies
            AllStrategyCategoryDto allCategoryDto = new AllStrategyCategoryDto();
            allCategoryDto.setDiy(diyList);
            allCategoryDto.setInHouse(inHouseList);
            allCategoryDto.setPreBuilt(prebuiltList);
            allCategoryDto.setPopular(popularList);
            res.setStrategies(allCategoryDto);

            // Get additional dropdown data
            DeployDropdownDto deployDropdownDto = getDeployDropdownList(isLimited);
            res.setDropdownList(deployDropdownDto);

            return res; // Return the populated response

        } catch (Exception e) {
            log.error("Error fetching strategies for user ID {}: {}", appUser.getAppUserId(), e.getMessage());
            throw new RuntimeException("Failed to fetch strategies", e); // Handle this gracefully or rethrow as a custom exception
        }
    }

    public AllStrategiesResDto deploySaveStrategy(String clientId, DeployReqDto deployReqDto) {
        deploySaveStrategyV2(clientId, deployReqDto);
        return getAllStrategies(clientId, true);
    }


    @Transactional
    public StrategyDto deploySaveStrategyV2(String clientId, DeployReqDto deployReqDto) {
        log.info("Deploying strategy with ID: " + deployReqDto.getStrategyId());

        try {
            AppUser appUser = authService.getUserFromCLientId(clientId);
            Strategy strategy = strategyRepository.findById(deployReqDto.getStrategyId())
                    .orElseThrow(() -> new StrategyNotFoundException("No strategy found with ID: " + deployReqDto.getStrategyId()));

            if(!Objects.equals(strategy.getAppUser().getAppUserId(), appUser.getAppUserId())){
                throw new UnauthorizedAccessException("Cannot modify other user's strategy");
            }

            LocalDate date = LocalDate.now();
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd MMM yyyy", Locale.ENGLISH);

            // Basic strategy updates
            strategy.setAtmType(deployReqDto.getAtmType());
            strategy.setMinCapital(deployReqDto.getMinCapital()*AMOUNT_MULTIPLIER);
            strategy.setPositionType(deployReqDto.getOrderId());
            strategy.setMultiplier(deployReqDto.getMultiplier());
            strategy.setExecutionType(deployReqDto.getExecutionTypeId());
//            strategy.setReSignalCount(deployReqDto.getFreshEntryCount());
            strategy.setExpiry(deployReqDto.getExpiry());
            strategy.setSubscription(SubscriptionStatus.START.getKey());
            strategy.setLastDeployedOn(date.format(formatter));
            strategy.setManualExitType(ManualExit.DISABLED.getKey());
            if (strategy.getStatus().equalsIgnoreCase(Status.INACTIVE.getKey())) {
                strategy.setStatus(Status.STANDBY.getKey());
            }

            // Underlying update
            Underlying underlying = underlyingRespository.findById(deployReqDto.getIndex())
                    .orElseThrow(() -> new UnderlyingNotFoundException("Underlying asset not found for ID: " + deployReqDto.getIndex()));
            strategy.setUnderlying(underlying);

            EntryDetails entryDetails = strategy.getEntryDetails();
            if (entryDetails == null) {
                entryDetails = new EntryDetails();
                entryDetails.setStrategy(strategy);  // Maintain bidirectional relationship
                strategy.setEntryDetails(entryDetails);
            }

            entryDetails.setEntryHourTime(deployReqDto.getEntryHours());
            entryDetails.setEntryMinsTime(deployReqDto.getEntryMinutes());
            LocalDateTime localDateTime = LocalDateTime.now()
                    .withHour(deployReqDto.getEntryHours())   // Set the hour
                    .withMinute(deployReqDto.getEntryMinutes()) // Set the minutes
                    .withSecond(0) // Optional: Reset seconds to 0
                    .withNano(0);  // Optional: Reset nanoseconds to 0

            ZonedDateTime zonedDateTime = localDateTime.atZone(ZoneId.systemDefault());
            entryDetails.setEntryTime(zonedDateTime.toInstant());
            // Properly handle the EntryDays collection (over writing the existing data with the new data.)
            List<EntryDays> entryDaysList = entryDaysRespository.findByIdIn(deployReqDto.getDays());
            entryDetails.setEntryDays(new ArrayList<>());
//            entryDetails.getEntryDays().clear();
            entryDetails.getEntryDays().addAll(entryDaysList);

            DayOfWeek today = LocalDate.now().getDayOfWeek();
            boolean isTodayEntryDay = entryDaysList.stream()
                    .anyMatch(entryDay -> entryDay.getDay().equalsIgnoreCase(today.name()));

            if (!isTodayEntryDay && EXCEPTION_DATE != null)
                isTodayEntryDay = EXCEPTION_DATE.equals(LocalDate.now());

            if (!(StrategyOption.ENABLE_HOLD.getKey().equalsIgnoreCase(strategy.getHoldType()))){
                if (isTodayEntryDay && !Status.LIVE.getKey().equalsIgnoreCase(strategy.getStatus())) {
                    strategy.setStatus(Status.ACTIVE.getKey());
                } else if (!Status.LIVE.getKey().equalsIgnoreCase(strategy.getStatus())) {
                    strategy.setStatus(Status.STANDBY.getKey());
                }
            }

            // Exit details update
            ExitDetails exitDetails = strategy.getExitDetails();
            if (exitDetails == null) {
                exitDetails = new ExitDetails();
                exitDetails.setStrategy(strategy);
                strategy.setExitDetails(exitDetails);
            }

            LocalDateTime localDateTimeExit = LocalDateTime.now()
                    .withHour(deployReqDto.getExitHours())   // Set the hour
                    .withMinute(deployReqDto.getExitMinutes()) // Set the minutes
                    .withSecond(0) // Optional: Reset seconds to 0
                    .withNano(0);  // Optional: Reset nanoseconds to 0

            ZonedDateTime zonedDateTimeExit = localDateTimeExit.atZone(ZoneId.systemDefault());
            exitDetails.setExitHourTime(deployReqDto.getExitHours());
            exitDetails.setExitMinsTime(deployReqDto.getExitMinutes());
            exitDetails.setExitTime(zonedDateTimeExit.toInstant());
            exitDetails.setTargetUnitToggle(deployReqDto.getProfitMtmToggle());
            exitDetails.setTargetUnitType(deployReqDto.getProfitMtmType());
            exitDetails.setTargetUnitValue(deployReqDto.getProfitMtmValue());
            exitDetails.setStopLossUnitToggle(deployReqDto.getStoplossToggle());
            exitDetails.setStopLossUnitType(deployReqDto.getStoplossType());
            exitDetails.setStopLossUnitValue(deployReqDto.getStoplossValue());
            assignStopLossMinCapital(exitDetails, strategy);

            List<StrategyLeg> newLegs = strategyLegRepository.findDefaultStrategyLegs(strategy.getId());
            for (StrategyLeg leg : newLegs) {
                if (leg.getLegType().equalsIgnoreCase(StrategyCategoryType.DIY.getKey())) {
                    log.info("Processing leg in side the if : {}", leg.getId());
                   leg.setLegExpName(deployReqDto.getExpiry());
                }
            }

            // Save the strategy
            strategyLegRepository.saveAll(newLegs);
            strategy = strategyRepository.saveAndFlush(strategy);
            PNLSocketUI.dirtyUsers.add(String.valueOf(strategy.getAppUser().getId()));
            deploymentErrorService.saveStrategyUpdateLogs(strategy, "Entry time met; strategy is now live");
            return userStrategyUtils.converToStrategyDto(strategy, newLegs);
        } catch (StrategyNotFoundException | UnauthorizedAccessException | UnderlyingNotFoundException e) {
            log.warn("Error during deployment: {}", e.getMessage());
            throw e;
        } catch (Exception e) {
            log.error("Error during strategy save operation", e);
            throw new RuntimeException("Failed to deploy strategy: " + e.getMessage(), e);
        }
    }

    @Transactional
    public AllStrategiesResDto oneClickDeploy(String clientId, OneClickDeployDto oneClickDeployDto) {
        log.info("Initiating one-click deploy for strategy with ID: {}", oneClickDeployDto.getStrategyId());

        updateOneClickDeployV2(clientId, oneClickDeployDto);
        return getAllStrategies(clientId, true);
    }

        @Transactional
    public StrategyDto updateOneClickDeployV2(String clientId, OneClickDeployDto oneClickDeployDto) {
        log.info("updateOneClickDeploy for strategy with ID: {}", oneClickDeployDto.getStrategyId());

        try {
            AppUser appUser = authService.getUserFromCLientId(clientId);
            Strategy strategy = strategyRepository.findById(oneClickDeployDto.getStrategyId())
                    .orElseThrow(() -> new StrategyNotFoundException("No strategy found with ID: " + oneClickDeployDto.getStrategyId()));

            if (!Objects.equals(strategy.getAppUser().getAppUserId(), appUser.getAppUserId())) {
                throw new Exception("Cannot deploy other user's strategy");
            }

            if (Status.LIVE.getKey().equalsIgnoreCase(strategy.getStatus())) {
                throw new StrategyAlreadyLiveException("Strategy is already live for ID: " + oneClickDeployDto.getStrategyId());
            }

            Hibernate.initialize(strategy.getEntryDetails());
            EntryDetails entryDetails = strategy.getEntryDetails();

            // Set entry time from hour & minutes
            LocalDateTime localDateTime = LocalDateTime.now()
                    .withHour(entryDetails.getEntryHourTime())
                    .withMinute(entryDetails.getEntryMinsTime())
                    .withSecond(0)
                    .withNano(0);
            ZonedDateTime zonedDateTime = localDateTime.atZone(ZoneId.systemDefault());
            entryDetails.setEntryTime(zonedDateTime.toInstant());

            List<EntryDays> entryDaysList = entryDetails.getEntryDays();

            // Fetch original legs (before update) to retain expiries
            List<StrategyLeg> originalLegs = strategyLegRepository.findByStrategyIdAndSignalIdIsNull(strategy.getId());
            Map<Long, String> originalExpiryMap = originalLegs.stream()
                    .collect(Collectors.toMap(StrategyLeg::getId, StrategyLeg::getLegExpName, (a, b) -> a));

            // Fetch current default legs to update
            List<StrategyLeg> newLegs = strategyLegRepository.findDefaultStrategyLegs(strategy.getId());

            for (StrategyLeg leg : newLegs) {
                log.info("Processing leg: {}", leg.getId());

                if (leg.getLegType().equalsIgnoreCase(StrategyCategoryType.DIY.getKey())) {
                    String existingExpiry = originalExpiryMap.get(leg.getId());
                    leg.setLegExpName(
                            (existingExpiry != null && !existingExpiry.isEmpty())
                                    ? existingExpiry
                                    : strategy.getExpiry() // fallback
                    );
                }
            }

            strategyLegRepository.saveAll(newLegs);

            // Check if today is a valid entry day
            DayOfWeek today = LocalDate.now().getDayOfWeek();
            boolean isTodayEntryDay = entryDaysList.stream()
                    .anyMatch(entryDay -> entryDay.getDay().equalsIgnoreCase(today.name()));

            if (!isTodayEntryDay && EXCEPTION_DATE != null) {
                isTodayEntryDay = EXCEPTION_DATE.equals(LocalDate.now());
            }

            String tagBasedStatus = bodSchedule.determineStrategyStatus(strategy);
            if (tagBasedStatus != null) {
                strategy.setStatus(tagBasedStatus);
            } else {
                strategy.setStatus(isTodayEntryDay ? Status.ACTIVE.getKey() : Status.STANDBY.getKey());
            }
            // First-time deployment updates
            if (!SubscriptionStatus.START.getKey().equalsIgnoreCase(strategy.getSubscription())) {
                strategy.setLastDeployedOn(LocalDate.now().format(DateTimeFormatter.ofPattern("dd MMM yyyy", Locale.ENGLISH)));
                strategy.setSubscription(SubscriptionStatus.START.getKey());
                strategy.setMultiplier(oneClickDeployDto.getMultiplier());
                strategy.setExecutionType(oneClickDeployDto.getExecutionTypeId());
                strategy.setManualExitType(ManualExit.DISABLED.getKey());

                entryDetailsRepository.saveAndFlush(entryDetails);
                deploymentErrorService.saveStrategyUpdateLogs(strategy, "oneClick strategy deployed");
                saveStrategyAndPublish(strategy);
            }

            log.info("Strategy ID: {} deployed successfully.", oneClickDeployDto.getStrategyId());
            List<StrategyLeg> defaultLegs = strategyLegRepository.findDefaultStrategyLegs(strategy.getId());
            PNLSocketUI.dirtyUsers.add(String.valueOf(strategy.getAppUser().getId()));
            return userStrategyUtils.converToStrategyDto(strategy, defaultLegs);


        } catch (StrategyNotFoundException | StrategyAlreadyLiveException e) {
            log.warn("Deployment failed for strategy with ID: {} - {}", oneClickDeployDto.getStrategyId(), e.getMessage());
            throw e;
        } catch (Exception e) {
            log.error("Unexpected error during one-click deployment for strategy ID: {}", oneClickDeployDto.getStrategyId(), e);
            throw new RuntimeException("Failed to deploy strategy due to unexpected error", e);
        }
    }
    public List<LegorderDto> getDiyLegs(List<StrategyLeg> strategyLegs) {
        log.info("Generating DIY Legs.");
        List<LegorderDto> legs = new ArrayList<>();
        for (StrategyLeg strategyLeg : strategyLegs) {
            LegorderDto leg = modelMapper.map(strategyLeg, LegorderDto.class);

        }
        return null;
    }

//    public boolean exitAll(ExitAllDto exitAllDto){
//        log.info("Received request to exit all strategies for tenant ID: "+  exitAllDto.getTenentId());
//        // has to change ar prod leval
//        List<StrategyLeg> reaultLegs = new ArrayList<>();
//        // has to change to id in exitAllDto in prod
//        Optional<AppUsers> user= appUserRepository.findById(Long.parseLong(exitAllDto.getTenentId()));
//        if (user.isPresent()) {
//            List<Signal> activeSignals = signalRepository.findAllByAppUsersIdAndStatus(user.get().getId(), "live");
//            for (Signal signal : activeSignals) {
//                if (signal.getStatus().equalsIgnoreCase("live")) {
//                    Hibernate.initialize(signal.getStrategyLeg());
//                    List<StrategyLeg> legs = signal.getStrategyLeg();
//                    Strategy strategy = signal.getStrategy();
//                    List<StrategyLeg> newLegs = createExitLegs(legs,strategy,signal);
//                    signal.setStatus("exit");
//                    signalRepository.save(signal);
//                }
//            }
//            return true;
//        }else {
//            return false;
//        }
//    }
    @Transactional
    public boolean exitAll(String clientId) {
        try {
            AppUser appUser = authService.getUserFromCLientId(clientId);
            log.info("Received request to exit all strategies for user ID: {}", appUser.getAppUserId());

            List<Strategy> activeStrategies = strategyRepository.findAllByAppUserIdAndStatus(appUser.getAppUserId(), "live");
            log.info("{} active signals found for user ID: {}", activeStrategies.size(), appUser.getAppUserId());

            if (activeStrategies.isEmpty()) {
                log.info("No active signals found for user Id: {}", appUser.getAppUserId());
                return true;
            }

            for (Strategy strategy : activeStrategies) {
                if ("live".equalsIgnoreCase(strategy.getStatus())) {
                    deploymentErrorService.saveStrategyUpdateLogs(strategy, "Strategy manually exited due to exit all");
                    strategy.setManualExitType(ManualExit.ENABLED.getKey());
                }
            }
//            activeStrategies = strategyRepository.saveAllAndFlush(activeStrategies);
            for (Strategy strategy : activeStrategies) {
                saveStrategyAndPublish(strategy);
                log.info("Exit process initiated for strategy ID: {}", strategy.getId());
            }

            return true;
        } catch (UserNotFoundException e) {
            log.error("User validation failed for clientId: {}", clientId, e);
            throw e;
        } catch (Exception e) {
            log.error("Unexpected error occurred while processing exit all request for clientId: {}", clientId, e);
            throw new RuntimeException("Unexpected error occurred for clientId: " + clientId, e);
        }
    }


    public List<StrategyLeg> createExitLegs(List<StrategyLeg> strategyLegs,Strategy strategy,Signal signal) {
        log.info("Creating exit legs for signal ID: " + signal.getId() + " with strategy ID: " + strategy.getId());
        List<StrategyLeg> newLegs = new ArrayList<>();
        for (StrategyLeg dto : strategyLegs) {
            if(!dto.getLegType().equalsIgnoreCase("open")) {
                continue;
            }
            String buySellFlag = dto.getBuySellFlag().equalsIgnoreCase("sell") ? "cover_sell" : "cover_buy";
            //  TouchlineBinaryResposne instrument = touchLineService.getTouchLine(dto.getExchangeInstrumentId().toString());
            LegorderDto newDto = new LegorderDto();
//                    newDto.setExchangeInstrumentID((long) instrument.getExchangeInstrumentId());
//                    newDto.setPrice((long) instrument.getLTP() * 1000);
            newDto.setQuantity((long) dto.getLatestUpdatedQuantity());
            newDto.setCreatedAt(Instant.now());
            newDto.setCreatedBy("bot");
            newDto.setUpdatedAt(Instant.now());
            newDto.setUpdatedBy("bot");
            newDto.setStatus("live");
            newDto.setDeleteIndicator("N");
            newDto.setBuySellFlag(buySellFlag);
            newDto.setNoOfLots(dto.getNoOfLots());
            newDto.setLegType(dto.getLegType());

            if (strategy.getStopLoss() != null) newDto.setStopLossUnitValue(strategy.getStopLoss());
            if (strategy.getTarget() != null) newDto.setTargetUnitValue(strategy.getTarget());

            newDto.setOptionType(dto.getOptionType());
            newDto.setMultiOrdersFlag("y");
            newDto.setSegment("NSEFO");

            // Map DTO to StrategyLeg
            StrategyLeg leg = modelMapper.map(newDto, StrategyLeg.class);
            leg.setAppUser(strategy.getAppUser());
            leg.setUserAdmin(strategy.getUserAdmin());
            leg.setLatestUpdatedQuantity(dto.getLatestUpdatedQuantity());
            leg.setSignal(signal);
            leg.setLegType("exit");
            leg.setId(null);

//            logger.info("Mapped Leg: " + leg);
            newLegs.add(leg);
        }
        signal.getStrategyLeg().addAll(newLegs);

         return  newLegs;
    }


    @Transactional
    public List<StrategyLeg> exitSingleStrategy(String clientId, ExitSingleStrategyDto exitSingleStrategyDto) {
        try {
            log.info("Received request to exit single strategy for signal ID: " + exitSingleStrategyDto.getSignalId());
            AppUser appUser = authService.getUserFromCLientId(clientId);

            Optional<Strategy> strategy = strategyRepository.findById(exitSingleStrategyDto.getStrategyId());

            if (strategy.isPresent()) {
                Strategy strategyObj = strategy.get();
                if (!Objects.equals(strategy.get().getAppUser().getUserId(), appUser.getUserId())) {
                    throw new Exception("cannot exit other users strategies");
                }
                if (!strategy.get().getStatus().equalsIgnoreCase(SignalStatus.EXIT.getKey())) {
                    if (strategyObj.getStatus().equalsIgnoreCase(Status.ACTIVE.getKey())) {
                        strategy.get().setStatus(Status.STANDBY.getKey());
                        log.info("Exiting strategy with ID: {}", exitSingleStrategyDto.getStrategyId());
                    }
                    strategyObj.setManualExitType(ManualExit.ENABLED.getKey());
                    strategyObj = strategyRepository.saveAndFlush(strategyObj);
                    deploymentErrorService.saveStrategyUpdateLogs(strategyObj, "Strategy manually exited");
                    saveStrategyAndPublish(strategyObj);
                    log.info("strategy has exited {}", exitSingleStrategyDto.getStrategyId());
                    return new ArrayList<>();
                }else
                    return null;
            }
            throw new RuntimeException("Something went wrong");
        }catch (Exception e) {
            // handle exceptions here
            throw new RuntimeException("Something went wrong", e);
        }
    }

    private void checkStrategyAsync(Strategy strategy) {
        strategyScheduler.submitStrategyToManuallyExit(strategy);
    }

    @Transactional
    public void saveStrategyAndPublish(Strategy strategy) {
        Strategy saved = strategyRepository.saveAndFlush(strategy);
        PNLSocketUI.dirtyUsers.add(String.valueOf(strategy.getAppUser().getId()));
        publisher.publishEvent(saved);
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onStrategySaved(Strategy strategy) {
        if (Status.ACTIVE.getKey().equalsIgnoreCase(strategy.getStatus()) || Status.LIVE.getKey().equalsIgnoreCase(strategy.getStatus())) {
            strategyScheduler.submitStrategyToManuallyExit(strategy);
        }
    }

    @Transactional
    public ActiveStrategiesResponseDto standBy(String clientId, Long strategyId) {
        log.info("Received StandBy request for strategy ID: {}", strategyId);
        try {
            AppUser appUser = authService.getUserFromCLientId(clientId);
            // Fetch strategy by ID
            Strategy strategy = strategyRepository.findById(strategyId)
                    .orElseThrow(() -> new StrategyNotFoundException("No strategy found with ID: " + strategyId));

            if(!Objects.equals(strategy.getAppUser().getAppUserId(), appUser.getAppUserId())){
                throw new Exception("Cannot modify other user's strategy");
            }

            // Check if strategy is already live
            if (Status.LIVE.getKey().equalsIgnoreCase(strategy.getStatus()) || Status.ERROR.getKey().equalsIgnoreCase(strategy.getStatus())) {
                return null;
            }

            boolean change = toggleStrategyStatus(strategy);

            log.info("Strategy ID: {} successfully changed to standby.", strategyId);
            if (strategy.getStatus().equalsIgnoreCase(Status.ACTIVE.getKey())) {
                deploymentErrorService.saveStrategyUpdateLogs(strategy, "Strategy re-started successfully");
            }
            saveStrategyAndPublish(strategy);
            return convertToActiveStrategiesResponseDto(strategy);
        } catch (StrategyNotFoundException | StrategyAlreadyLiveException e) {
            log.warn("Standby operation failed for strategy ID: {} - {}", strategyId, e.getMessage());
            throw e; // Re-throw for centralized handling
        } catch (Exception e) {
            log.error("Unexpected error during standby operation for strategy ID: {}", strategyId, e);
            throw new RuntimeException("Failed to change strategy to standby", e);
        }
    }

    public void pauseByUser(String clientId) {
        try {
            AppUser appUser = authService.getUserFromCLientId(clientId);
            List<Strategy> allStrategies = strategyRepository.findAllByAppUser_Id(appUser.getAppUserId());

            for (Strategy strategy : allStrategies) {

                strategy.setHoldType(StrategyOption.ENABLE_HOLD.getKey());

                if (isTodayAnEntryDay(strategy) && !Status.LIVE.getKey().equalsIgnoreCase(strategy.getStatus())) {
                    strategy.setStatus(Status.STANDBY.getKey());
                }
            }

            strategyRepository.saveAll(allStrategies);
            PNLSocketUI.dirtyUsers.add(String.valueOf(appUser.getAppUserId()));
        } catch (Exception e) {
            throw new RuntimeException("An error occurred", e);
        }
    }

    @Transactional
    public Boolean unsubscribeSingleStrategy(String clientId, Long strategyId) {
        try {
            AppUser appUser = authService.getUserFromCLientId(clientId);
            Strategy strategy = strategyRepository.findById(strategyId)
                    .orElseThrow(() -> new StrategyNotFoundException("No strategy found with ID: " + strategyId));

            if (!Objects.equals(strategy.getAppUser().getAppUserId(), appUser.getAppUserId())) {
                throw new Exception("Cannot modify other user's strategy");
            }

            if (strategy.getStatus().equalsIgnoreCase(Status.LIVE.getKey())){
                return false;
            }

            if(SubscriptionStatus.START.getKey().equalsIgnoreCase(strategy.getSubscription())) {
                strategy.setSubscription(SubscriptionStatus.END.getKey());
                strategy.setStatus(Status.INACTIVE.getKey());
                strategy.setTotalPNL(0L);
                strategy.setTodayPNL(0L);
                strategySquareOff(strategy);
                strategyRepository.save(strategy);
                PNLSocketUI.dirtyUsers.add(String.valueOf(strategy.getAppUser().getAppUserId()));
            }
        } catch (StrategyNotFoundException e) {
            throw e;
        }
        catch (Exception e) {
            throw new RuntimeException("An error occurred", e);
        }
        return true;
    }

    @Transactional
    public void strategySquareOff(Strategy strategy) {
        try {
            List<Signal> allSignals = signalRepository.findAllByStrategyAndStatusIn(strategy, LIVE_ERROR);
            if (allSignals.isEmpty()) {
                log.info("No Live/Error signals found for strategy ID: {}", strategy.getId());
                return;
            }
            for (Signal signal : allSignals) {
                signal.setStatus(Status.RMS_ERROR.getKey());
            }
            signalRepository.saveAll(allSignals);
            log.info("All signals for strategy ID: {} have been set to RMS_ERROR status number of signals {}.", strategy.getId(), allSignals.size());
        } catch (Exception e) {
            log.error("Error while unsubscribing setting RMS_ERROR status for signals of strategy ID: {}", strategy.getId(), e);
        }
    }

    private boolean toggleStrategyStatus(Strategy strategy) {
        if (isTodayAnEntryDay(strategy) && STRATEGY_STATUS_RETRY_LIST.contains(strategy.getStatus())) {
            strategy.setStatus(Status.ACTIVE.getKey());
            strategy.setManualExitType(ManualExit.DISABLED.getKey());
            strategy.setSignalCount(0);
            strategy.setHoldType(StrategyOption.DISABLE_HOLD.getKey());
            return true;
        } else if (Status.ACTIVE.getKey().equals(strategy.getStatus())) {
            strategy.setStatus(Status.PAUSED.getKey());
            strategy.setHoldType(StrategyOption.ENABLE_HOLD.getKey());
            return true;
        } else if (Status.STANDBY.getKey().equals(strategy.getStatus())) {
            strategy.setStatus(Status.ACTIVE.getKey());
            return true;
        }else if (Status.PAUSED.getKey().equals(strategy.getStatus())) {
            strategy.setStatus(Status.STANDBY.getKey());
            strategy.setHoldType(StrategyOption.DISABLE_HOLD.getKey());
            return true;
        }
        return false;
    }

    private boolean isTodayAnEntryDay(Strategy strategy) {
        List<EntryDays> entryDaysList = strategy.getEntryDetails().getEntryDays();
        DayOfWeek today = LocalDate.now().getDayOfWeek();
        return entryDaysList.stream()
                .anyMatch(entryDay -> entryDay.getDay().equalsIgnoreCase(today.name()));
    }

    public DeployedErrorDTO fetchTodayErrorForStrategy(String clientId, Long strategyId ) {
        AppUser appUser = authService.getUserFromCLientId(clientId);

        Instant startOfDay = LocalDate.now().atStartOfDay().toInstant(ZoneOffset.UTC);
        Instant endOfDay = startOfDay.plusSeconds(SECONDS_TO_EOD);

        List<DeploymentErrors> deploymentErrors = deploymentErrorsRepository.
                findTodayByStrategyId(appUser.getAppUserId(), strategyId, startOfDay, endOfDay);

        DeployedErrorDTO deployedErrorDTO = new DeployedErrorDTO();
        if (deploymentErrors.isEmpty()) {
            deploymentErrors = deploymentErrorsRepository.
                    findLatest10ByStrategyId(appUser.getAppUserId(), strategyId);
        }
        deployedErrorDTO = processDeploymentErrors(deploymentErrors,strategyId, appUser);
        return deployedErrorDTO;
    }

    public DeployedErrorDTO fetchAllErrorForStrategy(String clientId, Long strategyId ){
        AppUser appUser = authService.getUserFromCLientId(clientId);

        List<DeploymentErrors> deploymentErrors = deploymentErrorsRepository.
                findByStrategyIdAndError(strategyId, appUser.getAppUserId());

        DeployedErrorDTO deployedErrorDTO = new DeployedErrorDTO();
        if (!deploymentErrors.isEmpty()){
            deployedErrorDTO = processDeploymentErrors(deploymentErrors,strategyId, appUser);
        }
        return deployedErrorDTO;
    }

    @Transactional
    public Boolean changeToLiveTrading(String clientId, Long strategyId) {
        log.info("changing strategy to live trading");
            AppUser appUser = authService.getUserFromCLientId(clientId);
            Optional<Strategy> strategyFetched = strategyRepository.findById(strategyId);
            if (strategyFetched.isEmpty()){
                throw new RuntimeException("No strategy found with ID: "+strategyId);
            }
            if (!appUser.equals(strategyFetched.get().getAppUser())){
                throw new RuntimeException("Selected other user strategy: "+strategyId);
            }
            Strategy strategy = strategyFetched.get();
            if (strategy.getExecutionType().equalsIgnoreCase(ExecutionTypeMenu.LIVE_TRADING.getKey())){
                throw new RuntimeException("strategy is already live Trading: "+strategyId);
            }
            if (strategy.getStatus().equalsIgnoreCase(Status.LIVE.getKey())){
                throw new RuntimeException("Cannot change Execution Mode when strategy is Live");
            }
            changeStrategyExecutionMode(strategy, ExecutionTypeMenu.LIVE_TRADING.getKey());

        return true;
    }

    @Transactional
    public void changeStrategyExecutionMode(Strategy strategy, String executionMode) {
        try {
            strategy.setExecutionType(executionMode);
            strategyRepository.saveAndFlush(strategy);
            PNLSocketUI.dirtyUsers.add(String.valueOf(strategy.getAppUser().getId()));
        } catch (Exception e) {
            throw new RuntimeException("Error changing execution mode for strategy " + strategy.getId(), e);
        }
    }

    @Transactional
    public Boolean changeToPaperTrading(String clientId, Long strategyId) {
        log.info("changing strategy to Forward test");
            AppUser appUser = authService.getUserFromCLientId(clientId);
            Optional<Strategy> strategyFetched = strategyRepository.findById(strategyId);
            if (strategyFetched.isEmpty()){
                throw new RuntimeException("No strategy found with ID: "+strategyId);
            }
            if (!appUser.equals(strategyFetched.get().getAppUser())){
                throw new RuntimeException("Selected other user strategy: "+strategyId);
            }
            Strategy strategy = strategyFetched.get();
            if (strategy.getExecutionType().equalsIgnoreCase(ExecutionTypeMenu.PAPER_TRADING.getKey())){
                throw new RuntimeException("strategy is already Forward Test: "+strategyId);
            }
        if (strategy.getStatus().equalsIgnoreCase(Status.LIVE.getKey())){
            throw new RuntimeException("Cannot change Execution Mode when strategy is Live");
        }
        changeStrategyExecutionMode(strategy, ExecutionTypeMenu.PAPER_TRADING.getKey());

        return true;
    }

    @Transactional
    public Boolean changeStrategyMultiplier(String clientId, OneClickDeployDto oneClickDeployDto) {
        log.info("changing strategy lo multiplier");
        AppUser appUser = authService.getUserFromCLientId(clientId);
        Optional<Strategy> strategyFetched = strategyRepository.findById(oneClickDeployDto.getStrategyId());
        if (strategyFetched.isEmpty()){
            throw new RuntimeException("No strategy found with ID: "+oneClickDeployDto.getStrategyId());
        }
        if (!appUser.equals(strategyFetched.get().getAppUser())){
            throw new RuntimeException("Selected other user strategy: "+oneClickDeployDto.getStrategyId());
        }
        Strategy strategy = strategyFetched.get();
        if (strategy.getStatus().equalsIgnoreCase(Status.LIVE.getKey())){
            throw new RuntimeException("strategy is Live, unable to modify strategy Multiplier: "+oneClickDeployDto.getStrategyId());
        }

            try {
                strategy.setMultiplier(oneClickDeployDto.getMultiplier());
                strategyRepository.save(strategy);
                PNLSocketUI.dirtyUsers.add(String.valueOf(strategy.getAppUser().getId()));
            } catch (Exception e) {
                throw new RuntimeException("Error updating Miltiplier: " + oneClickDeployDto.getStrategyId());
            }
        return true;
    }

    @Transactional
    public void changeActiveStrategiesToStandBy(Long userId, String executionMode) {
        Optional<AppUser> appUsers = appUserRepository.findById(userId);
        if (appUsers.isEmpty())
            throw new RuntimeException("changeActiveStrategiesToStandBy: user not found "+userId);
        strategyRepository.updateStrategyStatusForAppUser(appUsers.get().getId(),Status.STANDBY.getKey(),Status.ACTIVE.getKey(), executionMode);
        strategyRepository.setManualExitTypeForAppUserByExecutionModeAndSubscription(appUsers.get().getId(), ManualExit.ENABLED.getKey(), SubscriptionStatus.START.getKey(), executionMode);
    }

    public DeployedStratrgiesDto processManualEntry(String clientId, Long strategyId) {
        AppUser appUser = authService.getUserFromCLientId(clientId);
        Strategy strategy = strategyRepository.findById(strategyId)
                .orElseThrow(() -> new StrategyNotFoundException("Strategy Not Found"));
        if(!Objects.equals(strategy.getAppUser().getAppUserId(), appUser.getAppUserId())){
            throw new RuntimeException("Cannot access other user's strategy");
        }
        if (Status.LIVE.getKey().equalsIgnoreCase(strategy.getStatus())) {
            throw new RuntimeException("This strategy is already live. Manual entry is not allowed.");
        }
        if (!Status.ERROR.getKey().equalsIgnoreCase(strategy.getStatus())) {
            throw new RuntimeException("Manual entry is only allowed for strategies in 'ERROR' state.");
        }
        strategy.setStatus(Status.ACTIVE.getKey());
        strategyRepository.save(strategy);
        return userSignalService.getActiveStrategies(clientId);
    }

    DeployedErrorDTO processDeploymentErrors(List<DeploymentErrors> deploymentErrors, Long strategyId, AppUser appUser){
        ZoneId zoneId = ZoneId.of("Asia/Kolkata");
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
        DeployedErrorDTO deployedErrorDTO = new DeployedErrorDTO();


        for (DeploymentErrors error : deploymentErrors) {
            StrategyErrorDetails strategyErrorDetails = new StrategyErrorDetails();
            strategyErrorDetails.setDescription(error.getDescription());
            strategyErrorDetails.setStatus(error.getStatus());
            strategyErrorDetails.setTimeStamp(error.getDeployedOn().atZone(zoneId).format(formatter));
            deployedErrorDTO.getStatusList().add(strategyErrorDetails);
        }
        deployedErrorDTO.setStrategyId(strategyId);
        deployedErrorDTO.setUserId(appUser.getUserId());
        return deployedErrorDTO;
    }


    @Transactional
    public StrategyDto updateOneClickDeploy(Long strategyId) {
        log.info("Strategy ID: {} UnDeployed successfully.", strategyId);
        Optional<Strategy> strategyFetched = strategyRepository.findById(strategyId);
        if (strategyFetched.isEmpty()){
            throw new RuntimeException("No strategy found with ID: "+strategyId);
        }
        Strategy strategy = strategyFetched.get();
        List<StrategyLeg> defaultLegs = strategyLegRepository.findDefaultStrategyLegs(strategy.getId());
        return userStrategyUtils.converToStrategyDto(strategy, defaultLegs);
    }

    @Transactional
    public AllStrategiesResDto editStrategyDetails(Long strategyId, String clientId) {
        log.info("Fetch Strategy ID: {} editStrategyDetails.", strategyId);

        Strategy strategy = strategyRepository.findByIdAndAppUser_TenentId(strategyId, clientId).orElseThrow(() -> new RuntimeException("Strategy not found"));

        AllStrategiesResDto res = new AllStrategiesResDto();
        List<StrategyLeg> defaultLegs = strategyLegRepository.findDefaultStrategyLegs(strategy.getId());
        StrategyDto strategyDto = userStrategyUtils.converToStrategyDto(strategy, defaultLegs);
        List<StrategyDto> diyList = new ArrayList<>();
        List<StrategyDto> inHouseList = new ArrayList<>();
        List<StrategyDto> prebuiltList = new ArrayList<>();
        List<StrategyDto> popularList = new ArrayList<>();

        userStrategyUtils.categorizeStrategy(strategy, strategyDto, diyList, inHouseList, prebuiltList, popularList);

        AllStrategyCategoryDto allCategoryDto = new AllStrategyCategoryDto();
        allCategoryDto.setDiy(diyList);
        allCategoryDto.setInHouse(inHouseList);
        allCategoryDto.setPreBuilt(prebuiltList);
        allCategoryDto.setPopular(popularList);
        res.setStrategies(allCategoryDto);

        DeployDropdownDto deployDropdownDto = getDeployDropdownList(true);
        res.setDropdownList(deployDropdownDto);

        return res;
    }

    @Transactional
    public AllStrategiesResDto getUserStrategiesByCategory(String clientId, boolean isLimited, String category) {
        AppUser appUser = authService.getUserFromCLientId(clientId);
        AllStrategiesResDto res = new AllStrategiesResDto();

        // Lists to categorize strategies
        List<StrategyDto> diyList = new ArrayList<>();
        List<StrategyDto> inHouseList = new ArrayList<>();
        List<StrategyDto> prebuiltList = new ArrayList<>();
        List<StrategyDto> popularList = new ArrayList<>();

        try {
            List<StrategySummaryDao> summaries = strategyRepository.findAllStrategySummariesByUserIdAndCategory(appUser.getAppUserId(), category);
            List<StrategySummaryDao> visibleSummaries = summaries.stream()
                    .filter(s -> s.getIsHidden() == null || !s.getIsHidden())
                    .toList();

            log.info("Found {} strategy summaries for user ID {} and category {}", visibleSummaries.size(), appUser.getAppUserId(), category);

            if (visibleSummaries.isEmpty()) {
                // Return empty categorized response quickly
                AllStrategyCategoryDto emptyCategory = new AllStrategyCategoryDto();
                emptyCategory.setDiy(diyList);
                emptyCategory.setInHouse(inHouseList);
                emptyCategory.setPreBuilt(prebuiltList);
                emptyCategory.setPopular(popularList);
                res.setStrategies(emptyCategory);
                res.setDropdownList(getDeployDropdownList(isLimited));
                return res;
            }

            // Collect IDs to batch fetch related data
            List<Long> strategyIds = visibleSummaries.stream().map(StrategySummaryDao::getId).toList();

            // Batch fetch default legs, entry details and exit details
            List<StrategyLeg> allDefaultLegs = new ArrayList<>();
            if (category.equalsIgnoreCase("diy") || category.equalsIgnoreCase(StrategyCategoryType.PREBUILT.getKey()))
                allDefaultLegs = strategyLegRepository.findDefaultStrategyLegsByStrategyIds(strategyIds);

            List<EntryDetails> entryDetailsList = entryDetailsRepository.findAllByStrategy_IdIn(strategyIds);
            List<ExitDetails> exitDetailsList = exitDetailsRepository.findAllByStrategy_IdIn(strategyIds);

            // Map fetched data by strategy id for fast lookup
            Map<Long, List<StrategyLeg>> legsByStrategy = new java.util.HashMap<>();
            for (StrategyLeg leg : allDefaultLegs) {
                if (leg.getStrategy() == null || leg.getStrategy().getId() == null) continue;
                legsByStrategy.computeIfAbsent(leg.getStrategy().getId(), k -> new ArrayList<>()).add(leg);
            }

            Map<Long, EntryDetails> entryByStrategy = new java.util.HashMap<>();
            for (EntryDetails ed : entryDetailsList) {
                if (ed.getStrategy() != null && ed.getStrategy().getId() != null) {
                    entryByStrategy.put(ed.getStrategy().getId(), ed);
                }
            }

            Map<Long, ExitDetails> exitByStrategy = new java.util.HashMap<>();
            for (ExitDetails ex : exitDetailsList) {
                if (ex.getStrategy() != null && ex.getStrategy().getId() != null) {
                    exitByStrategy.put(ex.getStrategy().getId(), ex);
                }
            }

            // Build DTOs using maps
            for (StrategySummaryDao summary : visibleSummaries) {
                List<StrategyLeg> defaultLegs = legsByStrategy.getOrDefault(summary.getId(), List.of());
                EntryDetails entryDetails = entryByStrategy.get(summary.getId());
                ExitDetails exitDetails = exitByStrategy.get(summary.getId());

                StrategyDto strategyDto = userStrategyUtils.convertSummaryToStrategyDto(summary, defaultLegs, entryDetails, exitDetails);

                // Categorize based on the category string value
                String cat = summary.getCategory();
                if (cat != null && cat.equalsIgnoreCase(StrategyCategoryType.DIY.getKey())) {
                    diyList.add(strategyDto);
                } else if (cat != null && cat.equalsIgnoreCase(StrategyCategoryType.INHOUSE.getKey())) {
                    inHouseList.add(strategyDto);
                } else if (cat != null && cat.equalsIgnoreCase("prebuilt")) {
                    prebuiltList.add(strategyDto);
                } else if (cat != null && cat.equalsIgnoreCase("popular")) {
                    popularList.add(strategyDto);
                } else {
                    prebuiltList.add(strategyDto);
                }
            }
            AllStrategyCategoryDto allCategoryDto = new AllStrategyCategoryDto();
            allCategoryDto.setDiy(diyList);
            allCategoryDto.setInHouse(inHouseList);
            allCategoryDto.setPreBuilt(prebuiltList);
            allCategoryDto.setPopular(popularList);
            res.setStrategies(allCategoryDto);

            DeployDropdownDto deployDropdownDto = getDeployDropdownList(isLimited);
            res.setDropdownList(deployDropdownDto);

            return res;

        } catch (UserNotFoundException e) {
            log.error("User validation failed for clientId: {}", clientId, e);
            throw e;
        } catch (Exception e) {
            log.error("Error ReadyToDeploy fetching strategies for user ID {}: {}", appUser.getAppUserId(), e.getMessage(), e);
            throw new RuntimeException("Failed to fetch strategies", e); // Handle this gracefully or rethrow as a custom exception
        }
    }

    public ActiveStrategiesResponseDto convertToActiveStrategiesResponseDto (Strategy item){
        ActiveStrategiesResponseDto res = new ActiveStrategiesResponseDto();
        res.setSId(item.getId());
        res.setName(item.getName());
        res.setCapital((item.getMinCapital()/AMOUNT_MULTIPLIER) * item.getMultiplier());
        res.setRequiredCapital(item.getMinCapital()/AMOUNT_MULTIPLIER);
        res.setDeployedOn(item.getLastDeployedOn());
        res.setPositionType(item.getPositionType());
        long strategyMTM = item.getTodayPNL() != null? item.getTodayPNL() : 0L;
        res.setStrategyMtm(strategyMTM / (double) AMOUNT_MULTIPLIER);
        res.setMultiplier(item.getMultiplier().toString());
        ExecutionTypeMenu type = ExecutionTypeMenu.fromKey(item.getExecutionType());
        res.setExecution(type.getKey());
        String category;
        if (item.getCategory().equalsIgnoreCase(StrategyCategoryType.INHOUSE.getKey()))
            category =  "In-House";
        else if (item.getCategory().equalsIgnoreCase(StrategyCategoryType.DIY.getKey()))
            category = "DIY";
        else
            category = item.getCategory();
        res.setCategory(category);
        res.setStatus(item.getStatus());
        res.setCounter(item.getReSignalCount());
        StrategyLegTableDTO strategyLegTableDTO = new StrategyLegTableDTO();
        strategyLegTableDTO.setStrategyMTM(res.getStrategyMtm());
        res.setStrategyLegTableDTO(strategyLegTableDTO);
        return res;
    }

    @Transactional
    public boolean exitAllByExecutionType(String clientId, String executionType) {
        try {
            AppUser appUser = authService.getUserFromCLientId(clientId);
            log.info("Received request to exit all LiveTrading strategies for user ID: {}", appUser.getAppUserId());

            List<Strategy> activeStrategies = strategyRepository.findAllByAppUserIdAndStatusAndExecutionType(appUser.getAppUserId(), Status.LIVE.getKey(), executionType);
            log.info("{} live LiveTrading signals found for user ID: {}", activeStrategies.size(), appUser.getAppUserId());

            if (activeStrategies.isEmpty()) {
                log.info("No live signals found for user Id: {}, executionMode : {}", appUser.getAppUserId(), executionType);
                return true;
            }

            for (Strategy strategy : activeStrategies) {
                if ("live".equalsIgnoreCase(strategy.getStatus())) {
                    deploymentErrorService.saveStrategyUpdateLogs(strategy, "Strategy exited due to exit all");
                    strategy.setManualExitType(ManualExit.ENABLED.getKey());
                }
            }
            for (Strategy strategy : activeStrategies) {
                saveStrategyAndPublish(strategy);
                log.info("Exit process initiated for strategy ID: {}", strategy.getId());
            }

            return true;
        } catch (UserNotFoundException e) {
            log.error("User validation failed for clientId: {}", clientId, e);
            throw e;
        } catch (Exception e) {
            log.error("Unexpected error occurred while processing exit all request for clientId: {}", clientId, e);
            throw new RuntimeException("Unexpected error occurred for clientId: " + clientId, e);
        }
    }
    public ActiveStrategiesResponseDto convertToActiveStrategiesResponseDtoViaStrategyID(Long strategyId) {
        Optional<Strategy> strategyFetched = strategyRepository.findById(strategyId);
        if (strategyFetched.isEmpty()) {
            throw new RuntimeException("No strategy found with ID: " + strategyId);
        }
        Strategy strategy = strategyFetched.get();
        return convertToActiveStrategiesResponseDto(strategy);
    }

    public ActiveStrategiesResponseDto changeStrategyMultiplierV2(String clientId, OneClickDeployDto oneClickDeployDto) {
        log.info("changing strategy multiplier V2");
        AppUser appUser = authService.getUserFromCLientId(clientId);
        Optional<Strategy> strategyFetched = strategyRepository.findById(oneClickDeployDto.getStrategyId());
        if (strategyFetched.isEmpty()){
            throw new RuntimeException("No strategy found with ID: "+oneClickDeployDto.getStrategyId());
        }
        if (!appUser.equals(strategyFetched.get().getAppUser())){
            throw new RuntimeException("Selected other user strategy: "+oneClickDeployDto.getStrategyId());
        }
        Strategy strategy = strategyFetched.get();
        if (strategy.getStatus().equalsIgnoreCase(Status.LIVE.getKey())){
            throw new RuntimeException("strategy is Live, unable to modify strategy Multiplier: "+oneClickDeployDto.getStrategyId());
        }

        try {
            strategy.setMultiplier(oneClickDeployDto.getMultiplier());
            strategyRepository.save(strategy);
            PNLSocketUI.dirtyUsers.add(String.valueOf(strategy.getAppUser().getId()));
        } catch (Exception e) {
            throw new RuntimeException("Error updating Miltiplier: " + oneClickDeployDto.getStrategyId());
        }
        return convertToActiveStrategiesResponseDto(strategy);
    }

    @Transactional
    public ActiveStrategiesResponseDto changeToPaperTradingV2(String clientId, Long strategyId) {
        log.info("changing strategy to Forward test V2");
        AppUser appUser = authService.getUserFromCLientId(clientId);
        Optional<Strategy> strategyFetched = strategyRepository.findById(strategyId);
        if (strategyFetched.isEmpty()){
            throw new RuntimeException("No strategy found with ID: "+strategyId);
        }
        if (!appUser.equals(strategyFetched.get().getAppUser())){
            throw new RuntimeException("Selected other user strategy: "+strategyId);
        }
        Strategy strategy = strategyFetched.get();
        if (strategy.getExecutionType().equalsIgnoreCase(ExecutionTypeMenu.PAPER_TRADING.getKey())){
            throw new RuntimeException("strategy is already Forward Test: "+strategyId);
        }
        if (strategy.getStatus().equalsIgnoreCase(Status.LIVE.getKey())){
            throw new RuntimeException("Cannot change Execution Mode when strategy is Live");
        }
        changeStrategyExecutionMode(strategy, ExecutionTypeMenu.PAPER_TRADING.getKey());

        deploymentErrorService.saveStrategyUpdateLogs(strategy,"Strategy shifted to Forward Test by user");

        return convertToActiveStrategiesResponseDto(strategy);
    }

    @Transactional
    public ActiveStrategiesResponseDto changeToLiveTradingV2(String clientId, Long strategyId) {
        log.info("V2 changing strategy to live trading");
        AppUser appUser = authService.getUserFromCLientId(clientId);
        Optional<Strategy> strategyFetched = strategyRepository.findById(strategyId);
        if (strategyFetched.isEmpty()){
            throw new RuntimeException("No strategy found with ID: "+strategyId);
        }
        if (!appUser.equals(strategyFetched.get().getAppUser())){
            throw new RuntimeException("Selected other user strategy: "+strategyId);
        }
        Strategy strategy = strategyFetched.get();
        if (strategy.getExecutionType().equalsIgnoreCase(ExecutionTypeMenu.LIVE_TRADING.getKey())){
            throw new RuntimeException("strategy is already live Trading: "+strategyId);
        }
        if (strategy.getStatus().equalsIgnoreCase(Status.LIVE.getKey())){
            throw new RuntimeException("Cannot change Execution Mode when strategy is Live");
        }
        changeStrategyExecutionMode(strategy, ExecutionTypeMenu.LIVE_TRADING.getKey());

        deploymentErrorService.saveStrategyUpdateLogs(strategy,"Strategy shifted to Live trading by user");
        return convertToActiveStrategiesResponseDto(strategy);
    }


    private void assignStopLossMinCapital(ExitDetails exitDetails, Strategy strategy) {
        try {
            Long profitMtmUnitValue = null;
            Long stopLossMtmValue = null;
            Long minCapitalMtmValue = strategy.getMinCapital() / AMOUNT_MULTIPLIER;

            if (exitDetails.getTargetUnitToggle().equalsIgnoreCase(TOGGLE_TRUE)
                    && exitDetails.getTargetUnitType() != null) {

                profitMtmUnitValue = exitDetails.getTargetUnitValue() * strategy.getMultiplier();
                if(exitDetails.getTargetUnitType().equalsIgnoreCase(MtmMenu.PERCENT_OF_CAPITAL.getKey())) {
                    profitMtmUnitValue = Math.round((minCapitalMtmValue * exitDetails.getTargetUnitValue()) / 100.0);
                }
                exitDetails.setProfitMtmUnitValue(profitMtmUnitValue);
            }
            if (exitDetails.getStopLossUnitToggle().equalsIgnoreCase(TOGGLE_TRUE) && exitDetails.getStopLossUnitType() != null) {

                stopLossMtmValue = exitDetails.getStopLossUnitValue() * strategy.getMultiplier();
                if(exitDetails.getStopLossUnitType().equalsIgnoreCase(MtmMenu.PERCENT_OF_CAPITAL.getKey())) {
                    stopLossMtmValue = Math.round((minCapitalMtmValue * exitDetails.getStopLossUnitValue()) / 100.0);
                }
                exitDetails.setStoplossMtmUnitValue(stopLossMtmValue);
            }
            exitDetails.setPremiumCapital(minCapitalMtmValue);

        }catch (Exception e){
            log.error("Error while assignStopLossMinCapital strategy id: {}, exception = {}", strategy.getId(), e.getMessage());
        }
    }


    @Transactional
    public Boolean deleteStrategyService(String clientId, Long strategyId) {
        log.info("deleting the strategy of clientId: {}, strategyId: {}", clientId, strategyId);
        AppUser appUser = authService.getUserFromCLientId(clientId);
        Optional<Strategy> strategyFetched = strategyRepository.findById(strategyId);
        if (strategyFetched.isEmpty()){
            throw new RuntimeException("No strategy found with ID: "+strategyId);
        }
        Strategy strategy = strategyFetched.get();

        if (!appUser.equals(strategy.getAppUser())){
            throw new RuntimeException("Selected other user strategy: "+strategyId);
        }

        if (strategy.getStatus().equalsIgnoreCase(Status.LIVE.getKey()) || strategy.getStatus().equalsIgnoreCase(Status.PENDING.getKey())){
            throw new RuntimeException("Cannot delete when strategy is Live");
        }
        if (!strategy.getCategory().equalsIgnoreCase(StrategyCategoryType.DIY.getKey()) || strategy.getSourceId() != null){
            throw new RuntimeException("Cannot delete strategy which is not DIY");
        }
        strategy.setDeleteIndicator(DELETE_INDICATOR_TRUE);
        strategy.setIsHidden(true);
        strategy.setStatus(Status.DELETED.getKey());
        strategy.setManualExitType(ManualExit.ENABLED.getKey());
        strategyRepository.saveAndFlush(strategy);
        deploymentErrorService.saveStrategyUpdateLogs(strategy,"Strategy deleted by user");
        return true;
    }
}
