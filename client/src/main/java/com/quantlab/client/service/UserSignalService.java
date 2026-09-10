package com.quantlab.client.service;

import com.quantlab.client.dto.*;
import com.quantlab.common.dao.ActiveStrategyDao;
import com.quantlab.common.entity.*;
import com.quantlab.common.exception.custom.StrategyNotFoundException;
import com.quantlab.common.exception.custom.UnauthorizedAccessException;
import com.quantlab.common.repository.*;
import com.quantlab.common.utils.staticstore.dropdownutils.*;
import com.quantlab.common.utils.staticstore.dropdownutils.Status;
import com.quantlab.common.utils.staticstore.dropdownutils.StrategyCategoryType;
import com.quantlab.signal.dto.LegHoldingDTO;
import com.quantlab.signal.dto.StrategyLegTableDTO;
import com.quantlab.signal.service.AuthService;
import org.hibernate.Hibernate;
import org.modelmapper.ModelMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.IntStream;

import static com.quantlab.common.utils.staticstore.AppConstants.*;
import static com.quantlab.signal.utils.staticdata.StaticStore.roundToTwoDecimalPlaces;

@Service
public class UserSignalService {

    private static final Logger log = LoggerFactory.getLogger(UserSignalService.class);

    private final StrategyCategoryRepository strategyCategoryRepository;
    private final userRoleRepository userRoleRepository;

    @Autowired
    private ModelMapper modelMapper;

    @Autowired
    DataLayerService dataLayerService;

    @Autowired
    SignalRepository signalRepository;

    @Autowired
    StrategyRepository strategyRepository;

    @Autowired
    StrategyLegRepository strategyLegRepository;

    @Autowired
    UserAuthConstantsRepository userAuthConstantsRepository;

    @Autowired
    AuthService authService;

    public UserSignalService(StrategyCategoryRepository strategyCategoryRepository, userRoleRepository userRoleRepository) {
        this.strategyCategoryRepository = strategyCategoryRepository;
        this.userRoleRepository = userRoleRepository;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public DeployedStratrgiesDto getActiveStrategies(String clientId) {
        log.info("Fetching active strategies");

        try {
            // Fetch signals with live status
            AppUser appUser = authService.getUserFromCLientId(clientId);
//            List<Signal> signalsList = signalRepository.findByUserIdAndStatus(userId,STATUS_LIVE);
//            resList.addAll(signalsList.stream().map(signal -> modelMapper.map(signal, ActiveStrategiesResponseDto.class)).toList());

            // Fetch strategies with active or stand-by signals

          //  List<Strategy> strategyList = strategyRepository.findByStatusInAndAppUsers(STRATEGY_STATUS_LIVE_PAPER,appUsers);
            List<Strategy> strategyList = strategyRepository.findAllBySubscriptionAndAppUserOrderByIdAsc(SubscriptionStatus.START.getKey(), appUser)
                            .stream().filter(strategy -> strategy.getIsHidden() != true).toList();

            Hibernate.initialize(strategyList);
            List<ActiveStrategiesResponseDto> resList = new ArrayList<>(strategyList.stream().map(item -> {
                ActiveStrategiesResponseDto res = new ActiveStrategiesResponseDto();
                res.setSId(item.getId());
                res.setName(item.getName());
                res.setCapital((item.getMinCapital()/AMOUNT_MULTIPLIER)*item.getMultiplier());
                res.setRequiredCapital(item.getMinCapital()/AMOUNT_MULTIPLIER);
                res.setDeployedOn(item.getLastDeployedOn()); // Consider setting a default or placeholder value here if necessary
                res.setPositionType(item.getPositionType());
//                MultiplierMenu multiplier = MultiplierMenu.fromKey(item.getMultiplier());
//                res.setMultiplier(multiplier.getLabel());
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
                List<Signal> liveSignals = signalRepository.findSignalsByPositionTypeAndStrategy(StrategyType.POSITIONAL.getKey(), StrategyType.INTRADAY.getKey(),Status.LIVE.getKey(), item.getId()) ;
                if (!liveSignals.isEmpty()){
                StrategyLegTableDTO strategyLegTableDTO = storeLegHoldingDTOS(item, liveSignals, false);
                res.setStrategyLegTableDTO(strategyLegTableDTO);
                res.setCounter(item.getReSignalCount());
                return res;
                }
                else {
                    res.setStrategyLegTableDTO(new StrategyLegTableDTO());
                    res.setCounter(0);
                    return res;
                }
            }).toList());
            DeployedStratrgiesDto deployedStratrgiesDto = addActiveDropdowns(resList);
            log.info("Successfully fetched {} active strategies", resList.size());
            return deployedStratrgiesDto;

        } catch (Exception e) {
            log.error("Inside userSignalService, Error while retrieving active strategies: {} , ",e.getMessage(), e);

            throw new RuntimeException("Error while fetching active strategies");
        }
    }

    private DeployedStratrgiesDto addActiveDropdowns(List<ActiveStrategiesResponseDto> resList) {
        DeployedStratrgiesDto deployedStratrgiesDto = new DeployedStratrgiesDto();
        deployedStratrgiesDto.setActiveStrategiesResponse(resList);
        ActiveStrategiesDropdownDto activeStrategiesDropdownDto = fetchActiveStrategiesDropdowns();


        deployedStratrgiesDto.setActiveStrategiesDropdown(activeStrategiesDropdownDto);
        return deployedStratrgiesDto;
    }

    private ActiveStrategiesDropdownDto fetchActiveStrategiesDropdowns() {
        ActiveStrategiesDropdownDto activeStrategiesDropdownDto = new ActiveStrategiesDropdownDto();

        List<SelectionMenuLongDto> multipliers = IntStream.rangeClosed(1, 100)
                .mapToObj(i -> new SelectionMenuLongDto((long) i, i + "x"))
                .toList();
        activeStrategiesDropdownDto.setMultiplier(multipliers);

        activeStrategiesDropdownDto.setExecutionType(Arrays.stream(ExecutionTypeMenu.values())
                .map(type -> new SelectionMenuStringDto(type.getKey(), type.getLabel()))
                .toList());

        return activeStrategiesDropdownDto;
    }

    public StrategyLegTableDTO storeLegHoldingDTOS(Strategy strategy,List<Signal> signals, boolean isDownload){
        try {
            StrategyLegTableDTO strategyLegTableDTO = new StrategyLegTableDTO();
            Long pNLTotal = strategy.getTodayPNL();
            double latestIndexPrice = 0.0;
            double baseIndexPrice = 0.0;
            double strategyMTM = pNLTotal != null ? (pNLTotal / ((double) AMOUNT_MULTIPLIER)) : 0.0;
            if (!signals.isEmpty()) {
                SignalAdditions signalAdditions = signals.get(0).getSignalAdditions();
                latestIndexPrice = signals.get(0).getLatestIndexPrice() != null ? signals.get(0).getLatestIndexPrice() / (double) AMOUNT_MULTIPLIER : 0.0;
                if (signalAdditions != null)
                    baseIndexPrice = signalAdditions.getEntryUnderlingPrice() != null ? signalAdditions.getEntryUnderlingPrice() / (double) AMOUNT_MULTIPLIER : 0.0;
            }

            strategyMTM = roundToTwoDecimalPlaces(strategyMTM);
            strategyLegTableDTO.setStrategyStatus(strategy.getStatus());
            strategyLegTableDTO.setStrategyMTM(strategyMTM);
            strategyLegTableDTO.setStrategyId(strategy.getId());
            strategyLegTableDTO.setIndexCurrentPrice(latestIndexPrice);
            strategyLegTableDTO.setIndexBasePrice(baseIndexPrice);
            strategyLegTableDTO.setData(createLegHoldingDTO(signals, isDownload, strategyLegTableDTO));
            strategyLegTableDTO.setTotalOrders(strategyLegTableDTO.getData().size());
            strategyLegTableDTO.setClosedOrders(strategyLegTableDTO.getTotalOrders() - strategyLegTableDTO.getOpenOrders());
            return strategyLegTableDTO;
        } catch (Exception e) {
            log.error("inside storeLegHoldingDTOS, error processing : {}", e.getMessage());
            throw new RuntimeException(e);
        }
    }


    @Transactional
    public Long fetchLatestStrategyMTM(Strategy strategy) {
        return  signalRepository.findMTMByStrategyId(strategy.getId());
    }

//    @SneakyThrows
//    @Transactional
//    public Signal createSignal(Strategy strategy, TouchlineBinaryResposne ctouchlineResposne, TouchlineBinaryResposne ptouchlineResposne , Map<String,Integer> lotsSize, List<SignalMapperDto> signalMapperDto ) {
//        log.info("Creating new signal for strategy: " + strategy.getName());
//        // Create a new Signal object and set its fields
//        Signal signal = new Signal();
//        signal.setAppUsers(strategy.getAppUsers());
//        signal.setStrategy(strategy);
//        signal.setMultiplier(1L);
//        signal.setCapital(100000L);
//        signal.setExecutionType("intraday");
//        signal.setStatus("live");
//
//        createStrategyLegs(signalMapperDto,strategy,signal);
//        long startTime = System.currentTimeMillis();
//        CompletableFuture<Signal> orderResponse = dataLayerService.saveSignal(signal);
//        log.debug("Time taken: for db1 " + (System.currentTimeMillis() - startTime));
//        log.info("Order response: " + orderResponse);
//        log.debug("Time taken: for db2 " + (System.currentTimeMillis() - startTime));
//        // Save the Signal object to the database
//        return orderResponse.get();
//    }
//
//    public void createStrategyLegs(List<SignalMapperDto> mapperSignal, Strategy strategy, Signal signal) {
//        log.info("Creating strategy legs for signal: " + signal.getId());
//        List<StrategyLeg>  newLegs =  mapperSignal.stream().map(dto->{
//            LegorderDto newDto = new LegorderDto();
//            newDto.setExchangeInstrumentID((long)dto.getTouchlineBinaryResposne().getExchangeInstrumentId());
//            newDto.setPrice((long)dto.getTouchlineBinaryResposne().getLTP()*1000);
//            newDto.setQuantity((long)dto.getLots());
//            newDto.setCreatedAt(Instant.now());
//            newDto.setCreatedBy("bot");
//            newDto.setUpdatedAt(Instant.now());
//            newDto.setUpdatedBy("bot");
//            newDto.setStatus("live");
//            newDto.setDeleteIndicator("N");
//            newDto.setBuySellFlag(dto.getBuySellFlag());
//            newDto.setNoOfLots((long)dto.getLots());
//            newDto.setLegType(dto.getLegType());
//            if (strategy.getStopLoss() !=null)newDto.setStopLossUnitValue(strategy.getStopLoss());
//            if (strategy.getTarget() !=null) newDto.setTargetUnitValue(strategy.getTarget());
//            newDto.setOptionType(dto.getPositionType());
//            newDto.setMultiOrdersFlag("y");
//            newDto.setSegment("NSEFO");
//            StrategyLeg leg = modelMapper.map(newDto, StrategyLeg.class);
//            leg.setAppUsers(strategy.getAppUsers());
//            leg.setUserAdmin(strategy.getUserAdmin());
//            leg.setSignal(signal);
//            leg.setLatestUpdatedQuantity((long)dto.getQuantity());
//            signal.getStrategyLeg().add(leg);
//            log.debug("Mapped Leg: " + leg); // Debug print
//            return leg;
//        }).toList();
//    }
//
//    public List<StrategyLeg> createExitStrategyLegs( List<SignalMapperDto> mapperSignal,Strategy strategy,Signal signal) {
//        log.info("Creating exit strategy legs for signal ID: " + signal.getId() + " and strategy ID: " + strategy.getId());
//        List<StrategyLeg>  newLegs =  mapperSignal.stream().map(dto->{
//            LegorderDto newDto = new LegorderDto();
//            newDto.setExchangeInstrumentID((long)dto.getTouchlineBinaryResposne().getExchangeInstrumentId());
//            newDto.setPrice((long)dto.getTouchlineBinaryResposne().getLTP()*1000);
//            newDto.setQuantity((long)dto.getLots());
//            newDto.setCreatedAt(Instant.now());
//            newDto.setCreatedBy("bot");
//            newDto.setUpdatedAt(Instant.now());
//            newDto.setUpdatedBy("bot");
//            newDto.setStatus("live");
//            newDto.setDeleteIndicator("N");
//            newDto.setBuySellFlag(dto.getBuySellFlag());
//            newDto.setNoOfLots((long)dto.getLots());
//            newDto.setLegType(dto.getLegType());
//            if (strategy.getStopLoss() !=null)newDto.setStopLossUnitValue(strategy.getStopLoss());
//            if (strategy.getTarget() !=null) newDto.setTargetUnitValue(strategy.getTarget());
//            newDto.setOptionType(dto.getPositionType());
//            newDto.setMultiOrdersFlag("y");
//            newDto.setSegment("NSEFO");
//            StrategyLeg leg = modelMapper.map(newDto, StrategyLeg.class);
//            leg.setAppUsers(strategy.getAppUsers());
//            leg.setUserAdmin(strategy.getUserAdmin());
//            leg.setSignal(signal);
//            signal.getStrategyLeg().add(leg);
//            log.debug("Mapped Leg: " + leg); // Debug print
//            return leg;
//        }).collect(Collectors.toList());
//        return newLegs;
//    }

    @Transactional
    public List<OpenLegsResDto> getOpenLegs(String clientId, Long signalId) {
        try {
            AppUser appUser = authService.getUserFromCLientId(clientId);
            List<StrategyLeg> strategyLegList = strategyLegRepository.findBySignal_IdAndAppUser_AppUserId(signalId, appUser.getAppUserId());
            return strategyLegList.stream().map(item -> {
                return new OpenLegsResDto(item.getExchangeInstrumentId().toString());
            }).toList();
        }catch (Exception e) {
            log.error(e.getMessage());
            throw new RuntimeException("Something went wrong", e);
        }
    }

    @Transactional
    public DeployedStratrgiesDto strategyDataDownload(String clientId, Long strategyId) {
        log.info("fetching entire data of Strategy {}",strategyId);
        try {
            AppUser appUser = authService.getUserFromCLientId(clientId);
            Strategy strategy = strategyRepository.findById(strategyId)
                    .orElseThrow(() -> new StrategyNotFoundException("No strategy found with ID: " + strategyId));

            if(!Objects.equals(strategy.getAppUser().getAppUserId(), appUser.getAppUserId())){
                throw new UnauthorizedAccessException("Cannot access other user's strategy");
            }

            ActiveStrategiesResponseDto res = new ActiveStrategiesResponseDto();
            res.setSId(strategy.getId());
            res.setName(strategy.getName());
            res.setCapital((strategy.getMinCapital()/AMOUNT_MULTIPLIER)*strategy.getMultiplier());
            res.setRequiredCapital(strategy.getMinCapital()/AMOUNT_MULTIPLIER);
            res.setDeployedOn(strategy.getLastDeployedOn());
            res.setPositionType(strategy.getPositionType());
            res.setMultiplier(strategy.getMultiplier().toString());
            ExecutionTypeMenu type = ExecutionTypeMenu.fromKey(strategy.getExecutionType());
            res.setExecution(type.getKey());
            res.setCategory(strategy.getCategory());
            res.setStatus(strategy.getStatus());
            List<Signal> allSignals = signalRepository.findByStrategyAndStatusOrderById(strategy, Status.EXIT.getKey()) ;
            if (!allSignals.isEmpty()){
                StrategyLegTableDTO strategyLegTableDTO = storeLegHoldingDTOS(strategy, allSignals, true);
                res.setStrategyLegTableDTO(strategyLegTableDTO);
                res.setCounter(allSignals.size());
            }
            else {
                res.setStrategyLegTableDTO(new StrategyLegTableDTO());
            }

            DeployedStratrgiesDto deployedStratrgiesDto = new DeployedStratrgiesDto();
            deployedStratrgiesDto.setActiveStrategiesResponse(new ArrayList<>(List.of(res)));

            return deployedStratrgiesDto;

        } catch (Exception e) {
            log.error("Inside userSignalService, Error while fetching entire Strategy data: {} , ",e.getMessage(), e);

            throw new RuntimeException("Error while fetching entire strategy data");
        }
    }

    public ArrayList<LegHoldingDTO> createLegHoldingDTO(List<Signal> signals, boolean isDownload, StrategyLegTableDTO strategyLegTableDTO) {

        ArrayList<LegHoldingDTO> legTableDTO = new ArrayList<>();
        long liveCount = 0;
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd-MM-yyyy HH:mm:ss");
        for (Signal signal : signals) {
            Hibernate.initialize(signal.getSignalLegs());
            for (StrategyLeg leg : signal.getSignalLegs()) {
                if(LegType.OPEN.getKey().equalsIgnoreCase(leg.getLegType())) {
                    LegHoldingDTO legDTO = new LegHoldingDTO();
                    legDTO.setLegId(leg.getId());
                    legDTO.setName(leg.getName());
                    //we are making the non-live signal quantity as 0
                    if ((Objects.equals(signal.getStatus(), Status.LIVE.getKey()) && Objects.equals(leg.getStatus(), Status.LIVE.getKey()))|| isDownload) {
                        liveCount++;
                        if ((Objects.equals(signal.getStatus(), Status.LIVE.getKey()) && Objects.equals(leg.getStatus(), Status.LIVE.getKey())))
                            legDTO.setLegLTP(roundToTwoDecimalPlaces((leg.getLtp() != null ? leg.getLtp() / ((double) AMOUNT_MULTIPLIER) : 0)));
                        if (leg.getBuySellFlag().equalsIgnoreCase(LegSide.SELL.getKey()))
                            legDTO.setLegQuantity((leg.getFilledQuantity()/leg.getLotSize())* -1);
                        else
                            legDTO.setLegQuantity(leg.getFilledQuantity()/leg.getLotSize());
                    }
                    else {
                        legDTO.setLegQuantity(0);
                        legDTO.setLegLTP(null);
                    }
                    legDTO.setLots(leg.getLotSize());
                    legDTO.setInstrumentId(leg.getExchangeInstrumentId());
                    legDTO.setSignalId(leg.getSignal().getId());
                    legDTO.setPAndL(roundToTwoDecimalPlaces(leg.getProfitLoss() != null ? leg.getProfitLoss() / ((double) AMOUNT_MULTIPLIER) : 0));
                    legDTO.setExecutedPrice(roundToTwoDecimalPlaces(leg.getExecutedPrice() != null ? leg.getExecutedPrice() / ((double) AMOUNT_MULTIPLIER) : 0));
                    legDTO.setCurrentIV(roundToTwoDecimalPlaces(leg.getCurrentIV() != null ? leg.getCurrentIV() / ((double) AMOUNT_MULTIPLIER) : 0));
                    legDTO.setCurrentDelta(roundToTwoDecimalPlaces(leg.getCurrentDelta() != null ? leg.getCurrentDelta() / ((double) GREEK_MULTIPLIER) : 0));
                    legDTO.setConstantIV(roundToTwoDecimalPlaces(leg.getConstantIV() != null ? leg.getConstantIV() / ((double) AMOUNT_MULTIPLIER) : 0));
                    legDTO.setConstantDelta(roundToTwoDecimalPlaces(leg.getConstantDelta() != null ? leg.getConstantDelta() / ((double) GREEK_MULTIPLIER) : 0));
                    if (leg.getBaseIndexPrice() != null)
                        legDTO.setIndexBasePrice(leg.getBaseIndexPrice()/(double)AMOUNT_MULTIPLIER);
                    if (leg.getLatestIndexPrice() != null)
                        legDTO.setIndexCurrentPrice(leg.getLatestIndexPrice()/(double)AMOUNT_MULTIPLIER);
                    Instant entryInstant = leg.getExecutedTime() != null? leg.getExecutedTime() : leg.getCreatedAt();
                    ZonedDateTime istTime = entryInstant.atZone(ZoneId.of("Asia/Kolkata"));
                    legDTO.setDeployedTimeStamp(istTime.format(formatter));
                    legTableDTO.add(legDTO);
                }
            }
            addExitTimePrice(legTableDTO, signal);
        }
        strategyLegTableDTO.setOpenOrders(liveCount);
        return legTableDTO;
    }


    private static final String EXIT_LEG_PREFIX = "EQ_EXIT_";

    private void addExitTimePrice(ArrayList<LegHoldingDTO> tableRow, Signal signal) {
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd-MM-yyyy HH:mm:ss");

        Map<Long, LegHoldingDTO> holdingByInstrument = new HashMap<>(tableRow.size());
        Map<Long, LegHoldingDTO> holdingByLegId = new HashMap<>(tableRow.size());
        for (LegHoldingDTO h : tableRow) {
            holdingByInstrument.put(h.getInstrumentId(), h);
            holdingByLegId.put(h.getLegId(), h);
        }

        boolean signalLive = Status.LIVE.getKey().equalsIgnoreCase(signal.getStatus());
        boolean signalExited = Status.EXIT.getKey().equalsIgnoreCase(signal.getStatus()) ||
                Status.EXIT_PENDING.getKey().equalsIgnoreCase(signal.getStatus());
        Map<Long, StrategyLeg> openLegById = null;  // built lazily — only tranche-tagged exits need it

        for (StrategyLeg leg : signal.getSignalLegs()) {
            if (!LegType.EXIT.getKey().equalsIgnoreCase(leg.getLegType())) continue;

            // Tranche-aware match: createEquitySingleLegExit stamps "EQ_EXIT_<openLegId>"
            // on the exit leg's legIdentifier so multi-tranche strategies (EMA-BB-Sell
            // T1/T2/TRAIL on one instrument) can pair the right open-row instead of
            // collapsing under instrumentId. Strategies without this prefix fall through
            // to the original instrumentId match — unchanged behavior.
            LegHoldingDTO dto = null;
            StrategyLeg openLeg = null;
            String identifier = leg.getLegIdentifier();
            if (identifier != null && identifier.startsWith(EXIT_LEG_PREFIX)) {
                try {
                    long parentLegId = Long.parseLong(identifier.substring(EXIT_LEG_PREFIX.length()));
                    dto = holdingByLegId.get(parentLegId);
                    if (openLegById == null) {
                        openLegById = new HashMap<>();
                        for (StrategyLeg l : signal.getSignalLegs()) {
                            if (LegType.OPEN.getKey().equalsIgnoreCase(l.getLegType())) {
                                openLegById.put(l.getId(), l);
                            }
                        }
                    }
                    openLeg = openLegById.get(parentLegId);
                } catch (NumberFormatException ignored) {}
            }
            if (dto == null) dto = holdingByInstrument.get(leg.getExchangeInstrumentId());
            if (dto == null) continue;

            dto.setExitTime(leg.getCreatedAt().atZone(ZoneId.of("Asia/Kolkata")).format(formatter));
            Long priceLong = leg.getExecutedPrice() != null ? leg.getExecutedPrice() : leg.getPrice();
            dto.setExitPrice(priceLong / (double) AMOUNT_MULTIPLIER);

            // Restore qty on a tranche row that exited while the signal is still LIVE OR if signal is exited.
            // createLegHoldingDTO zeros qty for any non-LIVE leg — correct for full exits
            // (signal flips non-LIVE alongside the legs) but hides the closed tranche's
            // size for partial exits. Only fires when paired via the EQ_EXIT_ tag, so
            // other strategies are untouched.
            if (openLeg != null && (signalLive || signalExited) && openLeg.getLotSize() != null && openLeg.getLotSize() > 0) {
                long qty = openLeg.getFilledQuantity() / openLeg.getLotSize();
                if (LegSide.SELL.getKey().equalsIgnoreCase(openLeg.getBuySellFlag())) qty = -qty;
                dto.setLegQuantity(qty);
            }
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public DeployedStratrgiesDto getActiveStrategiesV2(String clientId) {
        log.info("Fetching active strategies v2 for clientId:{}", clientId);
        try {
            // Fetch signals with live status
            AppUser appUser = authService.getUserFromCLientId(clientId);
            List<ActiveStrategyDao> strategyList = strategyRepository.findAllActiveStrategies(SubscriptionStatus.START.getKey(),appUser.getAppUserId())
                    .stream().filter(strategy -> !strategy.getIsHidden()).toList();
            Hibernate.initialize(strategyList);
            List<ActiveStrategiesResponseDto> resList = new ArrayList<>(strategyList.stream().map(this::convertToActiveStrategiesResponseDto).toList());
            DeployedStratrgiesDto deployedStratrgiesDto = addActiveDropdowns(resList);
            log.info("Successfully fetched {} active strategies V2", resList.size());
            return deployedStratrgiesDto;

        } catch (Exception e) {
            log.error("Inside userSignalService, Error while retrieving active strategiesV2: {} , ",e.getMessage(), e);

            throw new RuntimeException("Error while fetching active strategies");
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public DetailedStrategyDto activeStrategyDetailsV2(String clientId, Long strategyId) {
        log.info("Fetching active strategy details for client: {} and strategy: {} ", clientId ,strategyId);

        try {
            // Fetch signals with live status

            Strategy strategy = strategyRepository.findById(strategyId).orElseThrow(() -> new RuntimeException("Strategy not found"));
             ActiveStrategiesResponseDto res = new ActiveStrategiesResponseDto();
            res.setSId(strategy.getId());
            res.setName(strategy.getName());
            res.setCapital((strategy.getMinCapital()/AMOUNT_MULTIPLIER)*strategy.getMultiplier());
            res.setRequiredCapital(strategy.getMinCapital()/AMOUNT_MULTIPLIER);
            res.setDeployedOn(strategy.getLastDeployedOn()); // Consider setting a default or placeholder value here if necessary
            res.setPositionType(strategy.getPositionType());

            res.setMultiplier(strategy.getMultiplier().toString());
            ExecutionTypeMenu type = ExecutionTypeMenu.fromKey(strategy.getExecutionType());
            res.setExecution(type.getKey());
            String category;
            if (strategy.getCategory().equalsIgnoreCase(StrategyCategoryType.INHOUSE.getKey()))
                category =  "In-House";
            else if (strategy.getCategory().equalsIgnoreCase(StrategyCategoryType.DIY.getKey()))
                category = "DIY";
            else
                category = strategy.getCategory();
            res.setCategory(category);
            res.setStatus(strategy.getStatus());
            List<Signal> liveSignals = signalRepository.findTodayAndLiveSignalsByStrategy(strategy.getId(), Status.LIVE.getKey()) ;
            if (!liveSignals.isEmpty()){
                StrategyLegTableDTO strategyLegTableDTO = storeLegHoldingDTOS(strategy, liveSignals, false);
                res.setStrategyLegTableDTO(strategyLegTableDTO);
                res.setCounter(strategy.getReSignalCount());
            }
            else {
                res.setStrategyLegTableDTO(new StrategyLegTableDTO());
                res.setCounter(0);

            }

            DetailedStrategyDto detailedStrategyDto = createDetailedStrategyDto(res);
            log.info("Successfully fetched {} active strategies", res.getName());
            return detailedStrategyDto;

        } catch (Exception e) {
            log.error("Inside userSignalService, Error while retrieving active strategies: {} , ",e.getMessage(), e);

            throw new RuntimeException("Error while fetching active strategies");
        }
    }

    private DetailedStrategyDto createDetailedStrategyDto(ActiveStrategiesResponseDto res) {
        DetailedStrategyDto detailedStrategyDto= new DetailedStrategyDto();
        detailedStrategyDto.setDetailedStrategyResponse(res);
//        ActiveStrategiesDropdownDto activeStrategiesDropdownDto = fetchActiveStrategiesDropdowns();
//        detailedStrategyDto.setActiveStrategiesDropdown(activeStrategiesDropdownDto);
        return detailedStrategyDto;
    }

    ActiveStrategiesResponseDto convertToActiveStrategiesResponseDto (ActiveStrategyDao item){
        ActiveStrategiesResponseDto res = new ActiveStrategiesResponseDto();
        res.setSId(item.getSId());
        res.setName(item.getName());
        res.setCapital((item.getCapital()/AMOUNT_MULTIPLIER) * item.getMultiplier());
        res.setRequiredCapital(item.getCapital()/AMOUNT_MULTIPLIER);
        res.setDeployedOn(item.getDeployedOn());
        res.setPositionType(item.getPositionType());
        long strategyMTM = item.getTodayPNL() != null? item.getTodayPNL() : 0L;
        res.setStrategyMtm(strategyMTM / (double) AMOUNT_MULTIPLIER);
        res.setMultiplier(item.getMultiplier().toString());
        ExecutionTypeMenu type = ExecutionTypeMenu.fromKey(item.getExecution());
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

    public ActiveStrategiesResponseDto convertToActiveStrategiesResponseDto(ExitSingleStrategyDto strategyDto){

        ActiveStrategyDao strategy = strategyRepository.findReadyToDeployPageStrategy(strategyDto.getStrategyId());
        if (strategy != null){
            return convertToActiveStrategiesResponseDto(strategy);
        }
        return null;

    }


}
