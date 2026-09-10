package com.quantlab.signal.strategy;

import com.quantlab.common.entity.*;
import com.quantlab.common.loggingService.DeploymentErrorService;
import com.quantlab.common.repository.*;
import com.quantlab.common.utils.staticstore.IndexInstruments;
import com.quantlab.common.utils.staticstore.dropdownutils.*;
import com.quantlab.common.utils.staticstore.dropdownutils.Status;
import com.quantlab.common.utils.staticstore.dropdownutils.StrategyCategoryType;
import com.quantlab.signal.dto.LegOrderDto;
import com.quantlab.signal.dto.SignalMapperDto;
import com.quantlab.signal.dto.redisDto.MarketData;
import com.quantlab.signal.dto.redisDto.MasterResponseFO;
import com.quantlab.signal.service.ExitPNL;
import com.quantlab.signal.service.GrpcErrorService;
import com.quantlab.signal.service.GrpcService;
import com.quantlab.signal.service.SaveLegsToDB;
import com.quantlab.signal.service.redisService.HolidayService;
import com.quantlab.signal.service.redisService.RedisLegService;
import com.quantlab.signal.service.redisService.TouchLineService;
import com.quantlab.signal.utils.CommonUtils;
import com.quantlab.signal.utils.DiyStrategyCommonUtil;
import com.quantlab.signal.utils.StrategyUtils;
import com.quantlab.signal.web.dto.MarketLiveDto;
import com.quantlab.signal.web.service.MarketDataFetch;
import jakarta.persistence.EntityManager;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.hibernate.Hibernate;
import org.modelmapper.ModelMapper;
import org.modelmapper.TypeMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.DefaultTransactionDefinition;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.quantlab.common.utils.staticstore.AppConstants.*;
import static com.quantlab.signal.utils.StrategyConstants.DEFAULT_AMOUNT_INTERVAL;

@Service
public class SignalService {

    private static final Logger logger = LoggerFactory.getLogger(SignalService.class);

    private final SignalRepository signalRepository;

    @Autowired
    private ModelMapper modelMapper;

    @Autowired
    EntityManager entityManager;

    @Autowired
    GrpcErrorService grpcErrorService;


    @Autowired
    StrategyRepository strategyRepository;

    @Autowired
    StrategyLegRepository strategyLegRepository;

    @Autowired
    TouchLineService touchLineService;

    @Autowired
    DiyStrategyCommonUtil diyStrategyCommonUtil;

    private final MarketDataFetch marketDataFetch;

    @Autowired
    CommonUtils commonUtils;

    @Autowired
    GrpcService grpcService;

    @Autowired
    DefaultTransactionDefinition defaultTransactionDefinition;

    @Autowired
    PlatformTransactionManager transactionManager;

    @Autowired
    DeploymentErrorService deploymentErrorService;

    @Autowired
    DeploymentErrorsRepository deploymentErrorsRepository;

    @Autowired
    StrategyUtils strategyUtils;

    @Autowired
    ExitPNL exitPNL;

    @Autowired
    SaveLegsToDB saveLegsToDB;

    @Autowired
    RedisLegService redisLegService;

    @Autowired
    ExitDetailsRepository exitDetailsRepository;

    @Autowired
    HolidayService holidayService;

    public static ConcurrentHashMap<Long, Integer> strategyExitErrorCounter = new ConcurrentHashMap<>();
    private volatile boolean legOrderDtoMappingInitialized = false;

    public SignalService(SignalRepository signalRepository , MarketDataFetch marketDataFetch ,
                         StrategyRepository strategyRepository,
                         StrategyLegRepository strategyLegRepository,
                         TouchLineService touchLineService , GrpcErrorService grpcErrorService)
    {
        this.signalRepository = signalRepository;
        this.marketDataFetch = marketDataFetch;
        this.strategyRepository = strategyRepository;
        this.strategyLegRepository = strategyLegRepository;
        this.touchLineService = touchLineService;
        this.grpcErrorService = grpcErrorService;


    }

    @Transactional(propagation = Propagation.REQUIRES_NEW, isolation = Isolation.READ_COMMITTED)
    public Signal createSignal(Strategy strategy, List<SignalMapperDto> signalMapperDto)  {

        logger.info("Creating new signal for strategy: " + strategy.getId());
        // Create a new Signal object and set its fields
        Strategy strategy1 = strategyRepository.findById(strategy.getId()).orElse(null);
        if (strategy1 == null) {
            // has to handle
            return null;
        }
        Long hasLiveId = signalRepository.findLatestSignalId(strategy.getId(), Status.LIVE.getKey());
        if (hasLiveId != null){
            return null;
        }
        if (signalMapperDto == null || signalMapperDto.isEmpty()) {
            throw new IllegalStateException("No legs supplied for signal creation for strategy " + strategy.getId());
        }
        LocalDateTime dateTime = LocalDateTime.now();
        Instant instant = dateTime.toInstant(ZoneOffset.UTC);
        LocalDate today = LocalDate.now();
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd MMM yyyy", Locale.ENGLISH);

        Signal signal = new Signal();
        signal.setAppUser(strategy1.getAppUser());
        signal.setStrategy(strategy1);
        signal.setMultiplier(strategy1.getMultiplier());
        signal.setCapital(strategy1.getMinCapital());
        signal.setExecutionType(strategy1.getExecutionType());
        signal.setDeployedOn(instant.toString());
        signal.setPositionType(strategy.getPositionType());
        signal.setCapital(strategy.getMinCapital());
        signal.setStatus(SignalStatus.LIVE.getKey());
        List<StrategyLeg> newLegs = createStrategyLegs(signalMapperDto, strategy1, signal, LegStatus.OPEN.getKey());
        if (strategy1.getExecutionType().equalsIgnoreCase(ExecutionTypeMenu.LIVE_TRADING.getKey()))
            strategy1.setStatus(Status.PENDING.getKey());
        else
            strategy1.setStatus(Status.LIVE.getKey());
        strategy1.setLastDeployedOn(today.format(formatter));
        setPositionalExitStrategyDate(strategy1);
        strategyRepository.saveAndFlush(strategy1);
        newLegs.forEach(leg -> leg.setStrategy(strategy1));
        strategyLegRepository.saveAllAndFlush(newLegs);

        signal.setSignalLegs(newLegs);
        String underlying = strategy.getUnderlying().getName();
        IndexInstruments instrument = IndexInstruments.fromKey(underlying);

        SignalAdditions signalAdditions = new SignalAdditions();
        Long indexPrice = 0L;
        if (strategy.getAtmType().equalsIgnoreCase(AtmType.SYNTHETIC_ATM.getKey())) {
            double syntheticPrice = marketDataFetch.getSyntheticPrice(strategy,strategy.getUnderlying().getName());
            if (!Double.isFinite(syntheticPrice) || syntheticPrice <= 0) {
                throw new IllegalStateException("Invalid synthetic price for " + underlying + " while creating signal " + strategy.getId());
            }
            indexPrice = (long) (syntheticPrice * AMOUNT_MULTIPLIER);
            signalAdditions.setEntryUnderlingPrice((int)((int)syntheticPrice* AMOUNT_MULTIPLIER));
            signal.setBaseIndexPrice(indexPrice);
            if (signalMapperDto.get(0).getMarketLiveDto() != null) {
                signalAdditions.setCurrentAtm(signalMapperDto.get(0).getMarketLiveDto().getSyntheticAtm());
                signalAdditions.setEntryUnderlingSpotPrice((int) (signalMapperDto.get(0).getMarketLiveDto().getSyntheticPrice()* AMOUNT_MULTIPLIER));
            }
        }else {
            MarketData marketData = marketDataFetch.getInstrumentData(Long.valueOf(instrument.getLabel()));
            if (marketData == null) {
                throw new IllegalStateException("Invalid underlying market data for " + underlying + " while creating signal " + strategy.getId());
            }
            validatePositiveMarketPrice("underlying market data", underlying, marketData.getLTP());
            indexPrice = (long) (marketData.getLTP() * AMOUNT_MULTIPLIER);
            signal.setBaseIndexPrice(indexPrice);
            int atm = marketDataFetch.getATM(strategy.getUnderlying().getName(), (int) marketData.getLTP(), commonUtils.getExpiryShotDateByIndex(strategy.getExpiry(), strategy.getUnderlying().getName(), OptionType.OPTION.getKey()));
            if (signalMapperDto.get(0).getMarketLiveDto() != null) {
                signalAdditions.setEntryUnderlingPrice((int)(signalMapperDto.get(0).getMarketLiveDto().getSpotPrice() * AMOUNT_MULTIPLIER));
                signalAdditions.setEntryUnderlingSpotPrice((int)(signalMapperDto.get(0).getMarketLiveDto().getSpotPrice() * AMOUNT_MULTIPLIER));
            }
            signalAdditions.setCurrentAtm(atm);
        }
        signal.setSignalAdditions(signalAdditions);
        Signal orderResponse = signalRepository.saveAndFlush(signal);

        List<StrategyLeg> strategyLegs = new ArrayList<>(orderResponse.getSignalLegs());

        for (StrategyLeg dto : strategyLegs) {
            dto.setLegIdentifier("QO_" + orderResponse.getId() + "_" + dto.getId());
        }
        strategyLegRepository.saveAllAndFlush(strategyLegs);
        logger.info("Order response: " + orderResponse);
        boolean isDiyStrategy = strategy1.getStrategyTag() != null
                && strategy1.getStrategyTag().equalsIgnoreCase(StrategyCategoryType.DIY.getKey());
        String deploymentLogs = crateDiyStrategySignalLog(strategy1, strategyLegs, isDiyStrategy);
        if (isDiyStrategy) {
            deploymentLogs = deploymentLogs
                    + (strategy1.getStrategyCategory().getId() ==1?
                        System.lineSeparator()
                        +createLegWiseTargetProfitAndMaxLossLog(strategyLegs) :"")
                    + System.lineSeparator()
                    + (strategy1.getPositionType().equalsIgnoreCase(StrategyType.POSITIONAL.getKey())?
                        exitDateAppend(strategy1):"");
        }

        deploymentErrorService.saveStrategyUpdateLogs(strategy1,deploymentLogs);
        return orderResponse;
    }

    private String exitDateAppend(Strategy strategy1) {
        Instant exitTime = strategy1.getExitDetails().getExitStrategyDate();
        if (exitTime == null) {
            return "";
        }
        LocalDate exitDate = exitTime.atZone(ZoneId.systemDefault()).toLocalDate();
        return " strategy exit date is " + exitDate;
    }

    private void setPositionalExitStrategyDate(Strategy strategy) {
        if (strategy == null || strategy.getPositionType() == null
                || !strategy.getPositionType().equalsIgnoreCase(StrategyType.POSITIONAL.getKey()) ||
                strategy.getStrategyCategory() == null || strategy.getStrategyCategory().getId() == null ||
                strategy.getStrategyCategory().getId() != 1) {
            return;
        }

        ExitDetails exitDetails = strategy.getExitDetails();
        if (exitDetails == null || exitDetails.getExitAfterEntryDays() == null) {
            return;
        }

        Integer exitAfterDays = exitDetails.getExitAfterEntryDays();
        if (exitAfterDays < 0) {
            return;
        }

        ZoneId zoneId = ZoneId.systemDefault();
        ZonedDateTime now = ZonedDateTime.now(zoneId);
        LocalDate cursorDate = now.toLocalDate();
        LocalTime exitTime = now.toLocalTime().truncatedTo(ChronoUnit.SECONDS);
        int daysToAdd = exitAfterDays;

        while (daysToAdd > 0) {
            cursorDate = cursorDate.plusDays(1);
            if (holidayService.isBusinessDay(cursorDate)) {
                daysToAdd--;
            }
        }

        Instant computedExitDate = ZonedDateTime.of(cursorDate, exitTime, zoneId).toInstant();
        exitDetails.setExitStrategyDate(computedExitDate);
        exitDetailsRepository.save(exitDetails);
        logger.info("Set exit_strategy_date for strategy {} to {} using exit_after_entry_days={}",
                strategy.getId(), computedExitDate, exitAfterDays);
    }

    private String crateDiyStrategySignalLog(Strategy strategy1, List<StrategyLeg> strategyLegs, boolean isDiyStrategy) {
        String legNames = strategyLegs.stream()
                .filter(leg -> leg.getName() != null && leg.getBuySellFlag() != null)
                .map(leg -> leg.getBuySellFlag()+" " +(leg.getQuantity()/ leg.getLotSize()) + " lots of " + leg.getName())
                .collect(Collectors.joining(", "));
        StringBuilder deploymentLog = new StringBuilder("Strategy triggered")
                .append(System.lineSeparator())
                .append(" | Placing orders: ")
                .append(legNames);

        if (isDiyStrategy && strategy1.getExitDetails() != null) {
            Long minProfit = strategy1.getExitDetails().getProfitMtmUnitValue();
            Long maxLoss = strategy1.getExitDetails().getStoplossMtmUnitValue();
            deploymentLog.append(System.lineSeparator())
                    .append("| MinProfit = ")
                    .append(minProfit)
                    .append(" and maxLoss = ")
                    .append(maxLoss);
        }
        return deploymentLog.toString();
    }

    private String createLegWiseTargetProfitAndMaxLossLog(List<StrategyLeg> strategyLegs) {
        String legWiseRiskLog = strategyLegs.stream()
                .filter(leg -> leg.getName() != null)
                .map(leg -> " | " + leg.getName() +" at price :" +(leg.getPrice() /(double)AMOUNT_MULTIPLIER)+ " -> targetProfit = " + leg.getTargetFinalValue()
                        + " and maxLoss = " + leg.getStopLossFinalValue())
                .collect(Collectors.joining(System.lineSeparator()));

        return "Leg wise target profit and max loss:" + System.lineSeparator() + legWiseRiskLog;
    }

    public List<StrategyLeg> createStrategyLegs(List<SignalMapperDto> mapperSignal, Strategy strategy, Signal signal, String legType) {
        logger.info("Creating strategy legs for signal: " + signal.getId());
        if (mapperSignal == null || mapperSignal.isEmpty()) {
            throw new IllegalStateException("No leg data supplied while creating signal " + signal.getId());
        }
        double premiumCapital = 0.0;
        List<StrategyLeg> newLegs = new ArrayList<>();
        Long indexPrice = 0L;
        String underlying = strategy.getUnderlying().getName();
        IndexInstruments instrument = IndexInstruments.fromKey(underlying);
        if (strategy.getAtmType().equalsIgnoreCase(AtmType.SYNTHETIC_ATM.getKey())) {
            double syntheticPrice = marketDataFetch.getSyntheticPrice(strategy,strategy.getUnderlying().getName());
            indexPrice = (long) (syntheticPrice * AMOUNT_MULTIPLIER);
        }else {
            MarketData marketData = marketDataFetch.getInstrumentData(Long.valueOf(instrument.getLabel()));
            if (marketData == null) {
                throw new IllegalStateException("Invalid underlying market data for " + underlying + " while creating legs for signal " + signal.getId());
            }
            validatePositiveMarketPrice("underlying market data", underlying, marketData.getLTP());
            indexPrice = (long) (marketData.getLTP() * AMOUNT_MULTIPLIER);
        }

        for (SignalMapperDto dto : mapperSignal) {
            MarketData touchline = validateLegTouchline(dto, strategy, signal);
            LegOrderDto newDto = new LegOrderDto();
            newDto.setName(dto.getLegName());
            newDto.setExchangeInstrumentID((long) touchline.getExchangeInstrumentId());
            newDto.setPrice((long) (touchline.getLTP() * DEFAULT_AMOUNT_INTERVAL));
            newDto.setQuantity((long) dto.getLots());
            newDto.setStatus(legType);
            newDto.setDeleteIndicator("N");
            newDto.setLotSize((long) dto.getMasterData().getLotSize());
            newDto.setBuySellFlag(dto.getBuySellFlag());
            newDto.setNoOfLots((long) dto.getLots());
            newDto.setLegType(dto.getLegType());
//            if (strategy.getStopLoss() != null) newDto.setStopLossUnitValue(strategy.getStopLoss());
//            if (strategy.getTarget() != null) newDto.setTargetUnitValue(strategy.getTarget());
            if(TOGGLE_TRUE.equalsIgnoreCase(dto.getTargetUnitToggle())) {
                newDto.setTargetUnitType(dto.getTargetUnitType());
                newDto.setTargetUnitValue(dto.getTargetUnitValue());
                newDto.setTargetUnitToggle(dto.getTargetUnitToggle());
            }
            if(TOGGLE_TRUE.equalsIgnoreCase(dto.getStopLossUnitToggle())) {
                newDto.setStopLossUnitToggle(dto.getStopLossUnitToggle());
                newDto.setStopLossUnitType(dto.getStopLossUnitType());
                newDto.setStopLossUnitValue(dto.getStopLossUnitValue());
            }
            assignLegStopLossMinTarget(newDto, touchline.getLTP());
            if(TOGGLE_TRUE.equalsIgnoreCase(dto.getTrailingStopLossToggle())) {
                newDto.setTrailingStopLossToggle(dto.getTrailingStopLossToggle());
                newDto.setTrailingStopLossType(dto.getTrailingStopLossType());
                newDto.setTrailingStopLossValue(dto.getTrailingStopLossValue());
                newDto.setTrailingDistance(dto.getTrailingDistance());
            }
            newDto.setOptionType(dto.getOptionType());
            newDto.setMultiOrdersFlag("y");
            newDto.setSegment(strategyUtils.getSegment(strategy.getUnderlying().getName()));
            newDto.setName(dto.getLegName());
            StrategyLeg leg = mapLegOrderDtoToStrategyLeg(newDto);
            leg.setDerivativeType(dto.getDerivativeType());
            leg.setAppUser(strategy.getAppUser());
            leg.setUserAdmin(strategy.getUserAdmin());
            leg.setSignal(signal);
            leg.setTargetFinalValue(newDto.getTargetFinalValue());
            leg.setStopLossFinalValue(newDto.getStopLossFinalValue());
            leg.setExchangeStatus(LegExchangeStatus.CREATED.getKey());
            leg.setClosingPrice((long) (touchline.getLTP() * AMOUNT_MULTIPLIER));
            leg.setConstantIV((long) (touchline.getIV() * GREEK_MULTIPLIER));
            leg.setConstantDelta((long) (touchline.getDelta() * GREEK_MULTIPLIER));
            leg.setLegType(LegStatus.TYPE_OPEN.getKey());
            leg.setLatestUpdatedQuantity((long) dto.getQuantity());
            leg.setQuantity((long) dto.getQuantity());
            leg.setDerivativeType(dto.getDerivativeType());
            leg.setLatestIndexPrice(indexPrice);
            leg.setBaseIndexPrice(indexPrice);

            if (strategy.getStrategyTag().equalsIgnoreCase(StrategyCategoryType.DIY.getKey())) {
                if (TOGGLE_TRUE.equalsIgnoreCase(leg.getTrailingStopLossToggle())) {
                    double traillingpoints = touchline.getLTP() - dto.getTrailingDistance();
                    leg.setTrailingStopLossPoints((long) (traillingpoints * AMOUNT_MULTIPLIER));
                }
            }
            if (strategy.getExecutionType().equalsIgnoreCase(ExecutionTypeMenu.PAPER_TRADING.getKey())){
                leg.setTradedPrice((long)touchline.getLTP());
                leg.setFilledQuantity((long) dto.getQuantity());
                leg.setStatus(SignalStatus.LIVE.getKey());
                leg.setExecutedTime(Instant.now());
            }else {
                leg.setStatus(LegStatus.EXCHANGE.getKey());
                leg.setFilledQuantity(0L);
            }

            // Debug print
            logger.debug("Mapped Leg: " + leg);
            leg.setExecutedPrice(newDto.getPrice());
            newLegs.add(leg);
            premiumCapital = premiumCapital + (touchline.getLTP()* dto.getLots() * dto.getMasterData().getLotSize());
        };
        assignStopLossMinCapital(strategy);
        return newLegs;
    }

    private void assignLegStopLossMinTarget(LegOrderDto legOrderDto, double legEntryPrice) {
        if (TOGGLE_TRUE.equalsIgnoreCase(legOrderDto.getTargetUnitToggle())
                && legOrderDto.getTargetUnitValue() != null
                && legOrderDto.getTargetUnitType() != null) {
            if (TgtMenu.PERCENT_OF_ENTRY_PRICE.getKey().equalsIgnoreCase(legOrderDto.getTargetUnitType())) {
                legOrderDto.setTargetFinalValue(Math.round(legEntryPrice * legOrderDto.getTargetUnitValue() / 100) * (legOrderDto.getQuantity() * legOrderDto.getLotSize()));
            } else {
                legOrderDto.setTargetFinalValue(legOrderDto.getTargetUnitValue());
            }
        }

        if (TOGGLE_TRUE.equalsIgnoreCase(legOrderDto.getStopLossUnitToggle())
                && legOrderDto.getStopLossUnitValue() != null
                && legOrderDto.getStopLossUnitType() != null) {
            if (TgtMenu.PERCENT_OF_ENTRY_PRICE.getKey().equalsIgnoreCase(legOrderDto.getStopLossUnitType())) {
                legOrderDto.setStopLossFinalValue(Math.round(legEntryPrice * legOrderDto.getStopLossUnitValue() / 100) * (legOrderDto.getQuantity() * legOrderDto.getLotSize()));
            } else {
                legOrderDto.setStopLossFinalValue(legOrderDto.getStopLossUnitValue());
            }
        }
    }

    private StrategyLeg mapLegOrderDtoToStrategyLeg(LegOrderDto newDto) {
        if (!legOrderDtoMappingInitialized) {
            synchronized (this) {
                if (!legOrderDtoMappingInitialized) {
                    TypeMap<LegOrderDto, StrategyLeg> typeMap = modelMapper.getTypeMap(LegOrderDto.class, StrategyLeg.class);
                    if (typeMap == null) {
                        typeMap = modelMapper.createTypeMap(LegOrderDto.class, StrategyLeg.class);
                    }
                    typeMap.addMappings(mapper -> mapper.skip(StrategyLeg::setValue));
                    legOrderDtoMappingInitialized = true;
                }
            }
        }
        return modelMapper.map(newDto, StrategyLeg.class);
    }

    private MarketData validateLegTouchline(SignalMapperDto dto, Strategy strategy, Signal signal) {
        if (dto.getMasterData() == null) {
            throw new IllegalStateException("Missing data while creating signal " + signal.getId() + " for strategy " + strategy.getId());
        }
        MarketData touchline = dto.getTouchlineBinaryResponse();
        if (touchline == null) {
            throw new IllegalStateException("Missing data for leg " + dto.getLegName() + " while creating signal " + signal.getId() + " for strategy " + strategy.getId());
        }
        if (!Double.isFinite(touchline.getLTP()) || touchline.getLTP() <= 0) {
            throw new IllegalStateException("Invalid LTP for leg " + dto.getLegName() + " while creating signal " + signal.getId() + " for strategy " + strategy.getId() + ": " + touchline.getLTP());
        }
        return touchline;
    }

    private void validatePositiveMarketPrice(String source, String instrument, double price) {
        if (!Double.isFinite(price) || price <= 0) {
            throw new IllegalStateException("Invalid " + source + " for " + instrument + ": " + price);
        }
    }

    public List<StrategyLeg> createExitStrategyLegs(List<SignalMapperDto> mapperSignal, Strategy strategy, Signal signal) {
        logger.info("Creating exit strategy legs for signal ID: " + signal.getId() + " and strategy ID: " + strategy.getId());
        try{
            List<StrategyLeg> newLegs = mapperSignal.stream().map(dto -> {
                MarketData touchline = validateLegTouchline(dto, strategy, signal);
                LegOrderDto newDto = new LegOrderDto();
                newDto.setName(dto.getName());
                newDto.setExchangeInstrumentID((long) touchline.getExchangeInstrumentId());
                newDto.setPrice((long) (touchline.getLTP() * DEFAULT_AMOUNT_INTERVAL));
                newDto.setQuantity((long) dto.getQuantity());
                newDto.setCreatedAt(Instant.now());
                newDto.setLotSize((long) dto.getMasterData().getLotSize());
                newDto.setStatus(LegStatus.EXCHANGE.getKey());
                newDto.setDeleteIndicator("N");
                newDto.setBuySellFlag(dto.getBuySellFlag());
                newDto.setNoOfLots((long) dto.getLots());
                newDto.setLegType(dto.getLegType());
//            if (strategy.getStopLoss() != null) newDto.setStopLossUnitValue(strategy.getStopLoss());
//            if (strategy.getTarget() != null) newDto.setTargetUnitValue(strategy.getTarget());
                newDto.setTargetUnitType(dto.getTargetUnitType());
                newDto.setTargetUnitValue(dto.getTargetUnitValue());
                newDto.setStopLossUnitType(dto.getStopLossUnitType());
                newDto.setStopLossUnitValue(dto.getStopLossUnitValue());
                if(TOGGLE_TRUE.equalsIgnoreCase(dto.getTrailingStopLossToggle())) {
                    newDto.setTrailingStopLossToggle(dto.getTrailingStopLossToggle());
                    newDto.setTrailingStopLossType(dto.getTrailingStopLossType());
                    newDto.setTrailingStopLossValue(dto.getTrailingStopLossValue());
                    newDto.setTrailingDistance(dto.getTrailingDistance());
                }
                newDto.setOptionType(dto.getPositionType());
                newDto.setMultiOrdersFlag("y");
                // has to change has to set in props level
                newDto.setSegment(strategyUtils.getSegment(strategy.getUnderlying().getName()));
                StrategyLeg leg = mapLegOrderDtoToStrategyLeg(newDto);
                leg.setAppUser(strategy.getAppUser());
                leg.setDerivativeType(dto.getDerivativeType());
                leg.setUserAdmin(strategy.getUserAdmin());
                leg.setLatestUpdatedQuantity(newDto.getQuantity());
                leg.setOptionType(dto.getOptionType());
                leg.setSignal(signal);
                leg.setStrategy(strategy);
                leg.setLegType(LegStatus.EXIT.getKey());

                if (!dto.getExchangeStatus().equalsIgnoreCase(LegExchangeStatus.MANUALLY_TRADED.getKey())) {
                    leg.setExchangeStatus(LegExchangeStatus.CREATED.getKey());
                } else {
                    leg.setExchangeStatus(LegExchangeStatus.MANUALLY_TRADED.getKey());
                }
                leg.setExecutedPrice((long) (touchline.getLTP() * AMOUNT_MULTIPLIER));
                leg.setSignal(signal);
                leg.setClosingPrice((long) (touchline.getLTP() * AMOUNT_MULTIPLIER));
                leg.setConstantIV((long) (touchline.getIV() * GREEK_MULTIPLIER));
                leg.setConstantDelta((long) (touchline.getDelta() * GREEK_MULTIPLIER));
                StrategyLeg finalLeg = new StrategyLeg();
                finalLeg.setFields(leg);

                logger.info("Mapped Leg: " + finalLeg);
                return finalLeg;
            }).collect(Collectors.toList());
            return newLegs;
        } catch (Exception e) {
            logger.error("Failed to create exit strategy legs for signal ID: " + signal.getId(), e);

            if (strategy.getUserAdmin() != null) {
                String errorDetails = "Failed to create exit strategy legs: " + e.getMessage();
//                String emailBody = emailService.getEmailErrorTemplate(errorDetails);
//                String toEmail = strategy.getUserAdmin().getEmail();
//                emailService.sendEmail(toEmail, "Exit Leg Creation Error", emailBody);
            }
            grpcErrorService.placingOrderLogs(ERROR_SIGNAL_PLACING_EXIT, signal, Status.ERROR.getKey());
            throw new RuntimeException("Error while creating exit strategy legs", e);
        }
    }


    public Signal createDiySignal(Strategy strategy) {
        try {
            logger.info("Creating signal for strategy: " + strategy.getName());
            //  Hibernate.initialize(strategy.getStrategyLeg());
            List<StrategyLeg> legs = strategyLegRepository.findByStrategyIdAndLegType(strategy.getId(), StrategyCategoryType.DIY.getKey());

            logger.info("legs size is : " + legs.size());
            MarketLiveDto marketLive = marketDataFetch.getMarketData(strategy);

            List<SignalMapperDto> signalMapperDto = new ArrayList<>();
            legs.forEach(leg -> {
                if (leg.getDerivativeType() != null && leg.getDerivativeType().equalsIgnoreCase(OptionType.FUTURE.getKey())) {

                    String futurekey = strategy.getUnderlying().getName().toUpperCase(Locale.ROOT) +
                            commonUtils.getExpiryShotDateByIndex(leg.getLegExpName(), strategy.getUnderlying().getName(), leg.getDerivativeType()) +
                            OptionType.FUTURE.getKey();
                    MasterResponseFO master = marketDataFetch.getMasterResponse(futurekey);
                    MarketData futurelivedata = touchLineService.getTouchLine(String.valueOf(master.getExchangeInstrumentID()));
                    SignalMapperDto signalMapperDto1 = diyStrategyCommonUtil.getSignalMapperDto(strategy, leg, master, futurelivedata, futurekey);
                    signalMapperDto1.setMarketLiveDto(marketLive);
                    signalMapperDto.add(signalMapperDto1);

                } else {
                    if (isPremiumOrDeltaSelection(leg.getSktSelection())) {
                        SignalMapperDto premiumDeltaDto = diyStrategyCommonUtil.handlePremiumDeltaStrikeSelection(strategy, leg);
                        if (premiumDeltaDto != null) {
                            premiumDeltaDto.setMarketLiveDto(marketLive);
                            signalMapperDto.add(premiumDeltaDto);
                        }
                    } else {
//                        Integer strike = diyStrategyCommonUtil.getDiyStrike(leg.getSktType(), strategy.getUnderlying().getName(), leg.getOptionType());
                        Integer strike = diyStrategyCommonUtil.getDiyStrike(strategy, leg);

                        String newKey = strategy.getUnderlying().getName().toUpperCase(Locale.ROOT) +
                                commonUtils.getExpiryShotDateByIndex(leg.getLegExpName(), strategy.getUnderlying().getName(), leg.getDerivativeType()) +
                                "-" + strike + (leg.getOptionType().equalsIgnoreCase(SegmentType.CE.getKey()) ? "CE" : "PE");

                        logger.info("keys generated: "+newKey);
                        System.out.println("keys generated: "+newKey);
                        MasterResponseFO master = marketDataFetch.getMasterResponse(newKey);
                        MarketData touchline = touchLineService.getTouchLine(String.valueOf(master.getExchangeInstrumentID()));
                        SignalMapperDto signalMapperDto1 = diyStrategyCommonUtil.getSignalMapperDto(strategy, leg, master, touchline, newKey);
                        signalMapperDto1.setMarketLiveDto(marketLive);
                        signalMapperDto.add(signalMapperDto1);
                    }
                }

            });
            Signal signal = createSignal(strategy, signalMapperDto);


            return signal;
        } catch (Exception e) {
            logger.error("unable to createDiy signal for strategy "+strategy.getId(), e);
            errorCreatingSignal(strategy, e);
//            e.printStackTrace();
//            throw new RuntimeException(e.getMessage());
        }
        return null;
    }


    private boolean isPremiumOrDeltaSelection(String sktSelection) {
        return sktSelection != null &&
                (sktSelection.equalsIgnoreCase(StrikeSelectionMenu.PREMIUM_NEAREST.getKey()) ||
                        sktSelection.equalsIgnoreCase(StrikeSelectionMenu.PREMIUM_GREATERTHAN.getKey()) ||
                        sktSelection.equalsIgnoreCase(StrikeSelectionMenu.PREMIUM_LESSTHAN.getKey()) ||
                        sktSelection.equalsIgnoreCase(StrikeSelectionMenu.DELTA_NEAREST.getKey()) ||
                        sktSelection.equalsIgnoreCase(StrikeSelectionMenu.DELTA_GREATERTHAN.getKey()) ||
                        sktSelection.equalsIgnoreCase(StrikeSelectionMenu.DELTA_LESSTHAN.getKey()));
    }

    @Transactional
    public void errorCreatingSignal(Strategy strategy, Exception e) {
        try {
            //needs to add DeploymentError for the strategy
            strategy.setStatus(Status.ERROR.getKey());
            strategyRepository.save(strategy);
            strategyRepository.flush();
            DeploymentErrors deploymentErrors = new DeploymentErrors();
            deploymentErrors.setStrategy(strategy);
            deploymentErrors.setAppUser(strategy.getAppUser());
            deploymentErrors.setDeployedOn(Instant.now());
            deploymentErrors.setStatus(RUN_TIME_EXCEPTION);
            String errorMessage = e.getMessage();
            if (errorMessage != null && errorMessage.length() > 2000) {
                errorMessage = errorMessage.substring(0, 2000);
            }
            if (errorMessage == null || errorMessage.isBlank()) {
                errorMessage = "Failed to create a new signal due to missing data";
            }
            deploymentErrors.setDescription(new ArrayList<>(List.of("Error: " + errorMessage)));
            deploymentErrors.setErrorCode(errorMessage);
            deploymentErrorsRepository.save(deploymentErrors);
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }
    }


    @Transactional(
            propagation = Propagation.REQUIRED,
            rollbackFor = Exception.class,
            isolation = Isolation.READ_COMMITTED
    )
    public Signal createExit(Strategy strategy, Double exitPriceRupee) {

        Signal signal = signalRepository
                .findFirstByStatusAndStrategy_idOrderByCreatedAtDesc(
                        SignalStatus.LIVE.getKey(),
                        strategy.getId()
                )
                .orElse(null);

        if (signal == null) return null;

        try {
            // ---------- Market prices (compute ONCE) ----------
            String underlying = strategy.getUnderlying().getName();
            IndexInstruments instrument = IndexInstruments.fromKey(underlying);
            MarketData marketData =
                    marketDataFetch.getInstrumentData(Long.parseLong(String.valueOf(instrument.getLabel())));

            double syntheticPrice =
                    marketDataFetch.getSyntheticPrice(strategy, underlying);

            long indexPrice = strategy.getAtmType()
                    .equalsIgnoreCase(AtmType.SYNTHETIC_ATM.getKey())
                    ? (long) (syntheticPrice * AMOUNT_MULTIPLIER)
                    : (long) (marketData.getLTP() * AMOUNT_MULTIPLIER);

            signal.setBaseIndexPrice(indexPrice);

            // ---------- Filter OPEN legs only ----------
            List<StrategyLeg> openLegs = signal.getSignalLegs().stream()
                    .filter(leg ->
                            LegStatus.OPEN.getKey().equalsIgnoreCase(leg.getStatus()) &&
                                    !LegStatus.EXIT.getKey().equalsIgnoreCase(leg.getStatus())
                    )
                    .toList();

            // ---------- Build exit DTOs ----------
            List<SignalMapperDto> mapperDtos = openLegs.stream().map(leg -> {
                SignalMapperDto dto = new SignalMapperDto();
                dto.setName(leg.getName());
                dto.setLots(leg.getNoOfLots());
                dto.setBuySellFlag(leg.getBuySellFlag());
                dto.setOptionType(leg.getOptionType());
                dto.setLegType(leg.getLegType());
                dto.setDerivativeType(leg.getDerivativeType());
                dto.setQuantity(Math.toIntExact(leg.getFilledQuantity()));
                dto.setExchangeStatus(leg.getExchangeStatus());

                dto.setMasterData(marketDataFetch.getMasterResponse(leg.getName()));
                MarketData tl = touchLineService.getTouchLine(String.valueOf(leg.getExchangeInstrumentId()));

                // If custom exit price is provided, override the LTP
                if (exitPriceRupee != null) {
                    tl.setLTP(exitPriceRupee);
                }
                dto.setTouchlineBinaryResponse(tl);

                return dto;
            }).toList();

            // ---------- Create EXIT legs ----------
            List<StrategyLeg> exitLegs =
                    createExitStrategyLegs(mapperDtos, strategy, signal);

            // ---------- Mark old legs as EXIT ----------
            signal.getSignalLegs().forEach(leg -> {
                if (Status.LIVE.getKey().equalsIgnoreCase(leg.getStatus())) {
                    leg.setStatus(LegStatus.EXIT.getKey());
                    leg.setLatestIndexPrice((long) (syntheticPrice * AMOUNT_MULTIPLIER));
                }
            });

            // Pass defensive snapshots to avoid ConcurrentModificationException if the original lists are modified concurrently
            saveLegsToDB.saveStrategyLegsWithRetry(new ArrayList<>(signal.getSignalLegs()));
            saveLegsToDB.saveStrategyLegsWithRetry(new ArrayList<>(exitLegs));

            // ---------- Set identifiers ----------
            exitLegs.forEach(leg -> {
                leg.setLegIdentifier("QO_" + signal.getId() + "_" + leg.getId());
                logger.info("Exit leg created: {} - {}", leg.getLegIdentifier(), leg.getName());
            });

            // ---------- Lock & update signal ----------
            Signal lockedSignal =
                    signalRepository.findByIdForUpdate(signal.getId())
                            .orElseThrow(() -> new IllegalStateException("Signal missing"));

            if (ExecutionTypeMenu.LIVE_TRADING.getKey()
                    .equalsIgnoreCase(strategy.getExecutionType())) {
                strategy.setStatus(Status.EXIT_PENDING.getKey());
                lockedSignal.setStatus(Status.EXIT_PENDING.getKey());
            } else {
                strategy.setStatus(SignalStatus.EXIT.getKey());
                lockedSignal.setStatus(SignalStatus.EXIT.getKey());
            }

            lockedSignal.getSignalLegs().addAll(exitLegs);

            Signal savedSignal = signalRepository.saveAndFlush(lockedSignal);
            strategyRepository.save(strategy);

            // ---------- Logs & PNL ----------
            String legNames = savedSignal.getSignalLegs().stream()
                    .filter(l -> LegType.OPEN.getKey().equalsIgnoreCase(l.getLegType()))
                    .map(leg -> ((leg.getQuantity()/ leg.getLotSize())) + " lots of " + leg.getName())
                    .collect(Collectors.joining(", "));

            deploymentErrorService.saveStrategyUpdateLogs(
                    strategy,
                    "Exit signal triggered; placing exit order for " + legNames
            );

            if (!ExecutionTypeMenu.LIVE_TRADING.getKey()
                    .equalsIgnoreCase(strategy.getExecutionType())) {
                exitPNL.setFinalPNL(savedSignal);
            }

            return savedSignal;

        } catch (Exception e) {
            logger.error("Exit creation failed", e);
            logExitOrderError(strategy, signal);
            throw e;
        }
    }
    @Transactional(
            propagation = Propagation.REQUIRED,
            rollbackFor = Exception.class,
            isolation = Isolation.READ_COMMITTED
    )
    public Signal createExit(Strategy strategy) {
        return createExit(strategy, null);
    }


    @Transactional
    public void createSingleExit(Strategy strategy, StrategyLeg strategyLeg) {
        Hibernate.initialize(strategyLeg.getSignal());
        Signal signal = signalRepository.findByIdForUpdate(strategyLeg.getSignal().getId()).orElse(null);
        if (signal == null) {
            logger.error("Signal not found for ID: " + strategyLeg.getSignal().getId());
            return;
        }
        List<StrategyLeg> oldLegs = new CopyOnWriteArrayList<>(signal.getSignalLegs());
        StrategyLeg changeLeg = oldLegs.stream().filter(dto -> Objects.equals(dto.getId(), strategyLeg.getId())).findFirst().orElse(null);
        List<SignalMapperDto> signalMapperDto = Stream.of(changeLeg).map(dto -> {
            SignalMapperDto newDto = new SignalMapperDto();
            newDto.setLots(dto.getNoOfLots());
            newDto.setName(dto.getName());
            newDto.setBuySellFlag(dto.getBuySellFlag());
            newDto.setLegType(dto.getLegType());
            newDto.setDerivativeType(dto.getDerivativeType());
            newDto.setQuantity(Math.toIntExact(strategyLeg.getFilledQuantity()));
            newDto.setTargetUnitType(strategyLeg.getTargetUnitType());
            newDto.setTargetUnitValue(strategyLeg.getTargetUnitValue());
            newDto.setStopLossUnitType(strategyLeg.getStopLossUnitType());
            newDto.setStopLossUnitValue(strategyLeg.getStopLossUnitValue());
            newDto.setTrailingStopLossToggle(strategyLeg.getTrailingStopLossToggle());
            newDto.setTrailingStopLossType(strategyLeg.getTrailingStopLossType());
            newDto.setTrailingStopLossValue(strategyLeg.getTrailingStopLossValue());
            newDto.setTrailingDistance(strategyLeg.getTrailingDistance());
            newDto.setPositionType(strategyLeg.getSignal().getPositionType());
            newDto.setOptionType(strategyLeg.getOptionType());
            newDto.setMasterData(marketDataFetch.getMasterResponse(dto.getName()));
            MarketData touchline = touchLineService.getTouchLine(String.valueOf(dto.getExchangeInstrumentId()));
            newDto.setTouchlineBinaryResponse(touchline);
            newDto.setExchangeStatus(dto.getExchangeStatus());
            return newDto;
        }).toList();
        List<StrategyLeg> legs = createExitStrategyLegs(signalMapperDto, strategy, signal);
        Hibernate.initialize(signal.getSignalAdditions());
        double syntheticPrice = marketDataFetch.getSyntheticPrice(strategy, strategy.getUnderlying().getName());

        changeLeg.setLatestIndexPrice((long) syntheticPrice * AMOUNT_MULTIPLIER);
        changeLeg.setStatus(LegStatus.EXIT.getKey());
        strategyLegRepository.save(changeLeg);
        List<StrategyLeg> savedNewLegs = strategyLegRepository.saveAll(legs);
        for (StrategyLeg leg : savedNewLegs) {
            leg.setLegIdentifier("QO_" + signal.getId() + "_" + leg.getId());
        }
        savedNewLegs = strategyLegRepository.saveAll(legs);
        // Refetch the signal for safe update (prevents CME)
        Optional<Signal> signalOpt = signalRepository.findByIdForUpdate(signal.getId());
        if (signalOpt.isEmpty()) {
            logger.error("Signal not found for ID: " + signal.getId());
            return;
        }

        Signal updatedSignal = signalOpt.get();
        Hibernate.initialize(updatedSignal.getSignalLegs());

        List<StrategyLeg> mergedLegs = new ArrayList<>(updatedSignal.getSignalLegs());
        mergedLegs.addAll(savedNewLegs);
        updatedSignal.setSignalLegs(mergedLegs);

        // Save and return updated signal
        signalRepository.save(updatedSignal);
        if (strategy.getExecutionType().equalsIgnoreCase(ExecutionTypeMenu.PAPER_TRADING.getKey())){
            for (StrategyLeg leg: savedNewLegs) {
                exitPNL.saveSingleLegPnl(leg);
            }
        }
    }


    @Transactional
    public Signal createSingleExitWithList(Strategy strategy, List<StrategyLeg> strategyLegs , Signal signal) {

        if (signal == null) {
            logger.error("Signal not found for strategy ID: {} " ,strategy.getId());
            return null;
        }

        List<StrategyLeg> changeLegs = new ArrayList<>(strategyLegs.stream().filter(dto-> dto.getStatus().equalsIgnoreCase(LegStatus.OPEN.getKey())).collect(Collectors.toList()));
        List<SignalMapperDto> signalMapperDto = changeLegs.stream().map(dto -> {
            SignalMapperDto newDto = new SignalMapperDto();
            newDto.setName(dto.getName());
            newDto.setLots(dto.getNoOfLots());
            newDto.setBuySellFlag(dto.getBuySellFlag());
            newDto.setLegType(dto.getLegType());
            newDto.setDerivativeType(dto.getDerivativeType());
            newDto.setQuantity(Math.toIntExact(dto.getFilledQuantity()));
            newDto.setTargetUnitType(dto.getTargetUnitType());
            newDto.setTargetUnitValue(dto.getTargetUnitValue());
            newDto.setStopLossUnitType(dto.getStopLossUnitType());
            newDto.setStopLossUnitValue(dto.getStopLossUnitValue());
            newDto.setTrailingStopLossToggle(dto.getTrailingStopLossToggle());
            newDto.setTrailingStopLossType(dto.getTrailingStopLossType());
            newDto.setTrailingStopLossValue(dto.getTrailingStopLossValue());
            newDto.setTrailingDistance(dto.getTrailingDistance());
            newDto.setPositionType(dto.getSignal().getPositionType());
            newDto.setOptionType(dto.getOptionType());
            newDto.setMasterData(marketDataFetch.getMasterResponse(dto.getName()));
            MarketData touchline = touchLineService.getTouchLine(String.valueOf(dto.getExchangeInstrumentId()));
            newDto.setTouchlineBinaryResponse(touchline);
            newDto.setExchangeStatus(dto.getExchangeStatus());
            return newDto;
        }).toList();
        List<StrategyLeg> legs = createExitStrategyLegs(signalMapperDto, strategy, signal);
        Hibernate.initialize(signal.getSignalAdditions());
        double syntheticPrice = marketDataFetch.getSyntheticPrice(strategy, strategy.getUnderlying().getName());

        List<StrategyLeg> newChangeLeg = new ArrayList<>( changeLegs.stream().map(dto -> {
            dto.setLatestIndexPrice((long) syntheticPrice * AMOUNT_MULTIPLIER);
            dto.setStatus(LegStatus.EXIT.getKey());
            return dto;
        }).collect(Collectors.toList()));


        strategyLegRepository.saveAll(newChangeLeg);
        List<StrategyLeg> savedNewLegs = strategyLegRepository.saveAll(legs);
        for (StrategyLeg leg : savedNewLegs) {
            leg.setLegIdentifier("QO_" + signal.getId() + "_" + leg.getId());
            logger.info("Exit leg created: {} - {}", leg.getLegIdentifier(), leg.getName());
        }
        savedNewLegs = strategyLegRepository.saveAll(savedNewLegs);
        Optional<Signal> signalOpt = signalRepository.findByIdForUpdate(signal.getId());
        if (signalOpt.isEmpty()) {
            logger.error("Signal not found for ID: " + signal.getId());
            return null;
        }

        Signal updatedSignal = signalOpt.get();
        Hibernate.initialize(updatedSignal.getSignalLegs());

        List<StrategyLeg> mergedLegs = new ArrayList<>(updatedSignal.getSignalLegs());
        mergedLegs.addAll(savedNewLegs);
        updatedSignal.setSignalLegs(mergedLegs);
        // Save and return updated signal
        Signal savedSignal = signalRepository.save(updatedSignal);

        if (strategy.getExecutionType().equalsIgnoreCase(ExecutionTypeMenu.PAPER_TRADING.getKey())){
            for (StrategyLeg leg: savedNewLegs) {
                exitPNL.saveSingleLegPnl(leg);
            }
        }

        return savedSignal;
    }

    @Transactional
    public List<Signal> getActiveSignals(Long strategyId, String status) {
        return signalRepository.findByStrategyIdAndStatus(strategyId, status);
    }

    void logExitOrderError(Strategy strategy, Signal signal) {
        Integer count = strategyExitErrorCounter.get(strategy.getId());
        if (count == null) count = 0;
        if (count > 3) {
            grpcErrorService.placingOrderLogs(ERROR_PLACING_EXIT_ORDER_DESCRIPTION, signal, Status.ERROR.getKey());
        }
        strategyExitErrorCounter.put(strategy.getId(), count + 1);
    }


    private void assignStopLossMinCapital(Strategy strategy) {
        try {
            Long profitMtmUnitValue = null;
            Long stopLossMtmValue = null;
            Long minCapitalMtmValue = strategy.getMinCapital() / AMOUNT_MULTIPLIER;
            ExitDetails exitDetails = strategy.getExitDetails();

            if (exitDetails.getTargetUnitToggle().equalsIgnoreCase(TOGGLE_TRUE)
                    && exitDetails.getTargetUnitType() != null) {

                profitMtmUnitValue = exitDetails.getTargetUnitValue() * strategy.getMultiplier();
                if(exitDetails.getTargetUnitType().equalsIgnoreCase(MtmMenu.PERCENT_OF_CAPITAL.getKey())) {
                    profitMtmUnitValue = Math.round((minCapitalMtmValue * exitDetails.getTargetUnitValue()) / 100.0);
                }
                exitDetails.setProfitMtmUnitValue(profitMtmUnitValue * strategy.getMultiplier());
            }
            if (exitDetails.getStopLossUnitToggle().equalsIgnoreCase(TOGGLE_TRUE) && exitDetails.getStopLossUnitType() != null) {

                stopLossMtmValue = exitDetails.getStopLossUnitValue() * strategy.getMultiplier();
                if(exitDetails.getStopLossUnitType().equalsIgnoreCase(MtmMenu.PERCENT_OF_CAPITAL.getKey())) {
                    stopLossMtmValue = Math.round((minCapitalMtmValue * exitDetails.getStopLossUnitValue()) / 100.0);
                }
                exitDetails.setStoplossMtmUnitValue(stopLossMtmValue * strategy.getMultiplier());
            }
            exitDetails.setPremiumCapital(minCapitalMtmValue);

        }catch (Exception e){
            logger.error("Error while assignStopLossMinCapital strategy id: {}, exception = {}", strategy.getId(), e.getMessage());
        }
    }
    /**
     * Add new legs to an existing LIVE signal (e.g., hedge legs added dynamically).
     * Creates the legs, saves them, and returns the updated signal.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW, isolation = Isolation.READ_COMMITTED)
    public Signal addLegToSignal(Strategy strategy, Signal signal, List<SignalMapperDto> signalMapperDto) {
        logger.info("Adding legs to existing signal | SignalId={} | StrategyId={} | NewLegs={}",
                signal.getId(), strategy.getId(), signalMapperDto.size());

        Strategy strategy1 = strategyRepository.findById(strategy.getId()).orElse(null);
        if (strategy1 == null) return null;

        Signal existingSignal = signalRepository.findById(signal.getId()).orElse(null);
        if (existingSignal == null) return null;

        Hibernate.initialize(existingSignal.getSignalLegs());

        List<StrategyLeg> newLegs = createStrategyLegs(signalMapperDto, strategy1, existingSignal, LegStatus.OPEN.getKey());
        newLegs.forEach(leg -> leg.setStrategy(strategy1));
        strategyLegRepository.saveAllAndFlush(newLegs);

        for (StrategyLeg leg : newLegs) {
            leg.setLegIdentifier("QO_" + existingSignal.getId() + "_" + leg.getId());
        }
        strategyLegRepository.saveAllAndFlush(newLegs);

        existingSignal.getSignalLegs().addAll(newLegs);
        Signal saved = signalRepository.saveAndFlush(existingSignal);

        String legNames = newLegs.stream()
                .filter(leg -> leg.getName() != null && leg.getBuySellFlag() != null)
                .map(leg -> leg.getBuySellFlag() + " " + (leg.getQuantity() / leg.getLotSize()) + " lots of " + leg.getName())
                .collect(Collectors.joining(", "));
        deploymentErrorService.saveStrategyUpdateLogs(strategy1, "Hedge legs added: " + legNames);

        logger.info("Hedge legs added successfully | SignalId={} | NewLegCount={}", saved.getId(), newLegs.size());
        return saved;
    }
    @Transactional(propagation = Propagation.REQUIRES_NEW, isolation = Isolation.READ_COMMITTED)
    public Signal createEquitySignal(Strategy strategy, List<SignalMapperDto> mapperList) {

        logger.info("[EQUITY] Creating signal for stock screener, strategy {}", strategy.getId());

        Strategy dbStrategy = strategyRepository.findById(strategy.getId()).orElse(null);
        if (dbStrategy == null) return null;

        // ----- Prevent Duplicate LIVE Signal -----
        Optional<Signal> existingLive = signalRepository
                .findFirstByStrategyIdAndStatusOrderByCreatedAtDesc(
                        strategy.getId(),
                        SignalStatus.LIVE.getKey()
                );

        if (existingLive.isPresent()) {
            logger.error("[EQUITY] Strategy {} already has LIVE signal", strategy.getId());
            return null;
        }

        // ----- Create new signal -----
        Signal signal = new Signal();
        signal.setStrategy(dbStrategy);
        signal.setStatus(SignalStatus.LIVE.getKey());
        signal.setExecutionType(dbStrategy.getExecutionType());
        signal.setCapital(dbStrategy.getMinCapital());
        signal.setMultiplier(dbStrategy.getMultiplier());
        signal.setAppUser(dbStrategy.getAppUser());
        signal.setPositionType(dbStrategy.getPositionType());
        signal.setDeployedOn(Instant.now().toString());

        // ----- Convert mapperList → StrategyLegs safely -----
        List<StrategyLeg> legs = createEquityLegs(mapperList, dbStrategy, signal);

        // Assign legs to signal BEFORE save (cascade = ALL on leg.signal)
        signal.setSignalLegs(legs);

        // ----- Update strategy state -----
        dbStrategy.setStatus(Status.LIVE.getKey());
        dbStrategy.setLastDeployedOn(LocalDate.now().format(DateTimeFormatter.ofPattern("dd MMM yyyy")));
        strategyRepository.saveAndFlush(dbStrategy);

        // ----- Save signal and all legs in one atomic step -----
        signal = signalRepository.saveAndFlush(signal);

        // ----- After DB generated leg IDs → assign unique leg identifiers -----
        List<StrategyLeg> legsToUpdate = new ArrayList<>(signal.getSignalLegs());
        for (StrategyLeg leg : legsToUpdate) {
            leg.setLegIdentifier("EQ_" + signal.getId() + "_" + leg.getId());
        }
        strategyLegRepository.saveAll(legsToUpdate);

        // ----- Initialize Redis state for each leg (for stoploss/trailing SL tracking) -----
        initializeEquityLegsRedisState(dbStrategy.getId(), legsToUpdate, mapperList);

        return signal;
    }
    private List<StrategyLeg> createEquityLegs(
            List<SignalMapperDto> mapperList,
            Strategy strategy,
            Signal signal
    ) {

        List<StrategyLeg> legs = new ArrayList<>();

        for (SignalMapperDto dto : mapperList) {

            StrategyLeg leg = new StrategyLeg();

            leg.setSignal(signal);
            leg.setStrategy(strategy);

            // --- Safe defaults ---
            String legName = (dto.getLegName() != null) ? dto.getLegName() : "EQ";
            String buySellFlag = (dto.getBuySellFlag() != null) ? dto.getBuySellFlag() : "BUY";

            long quantity = dto.getQuantity() > 0 ? dto.getQuantity() : 1L;

            long lots = (dto.getLots() != null && dto.getLots() > 0)
                    ? dto.getLots()
                    : 1L;

            MarketData tl = dto.getTouchlineBinaryResponse();
            long exchangeInstrumentId = tl.getExchangeInstrumentId();

            double ltp = tl.getLTP();
            long price1000 = (long) (ltp * AMOUNT_MULTIPLIER);

            // --- Core fields ---
            leg.setName(legName);
            leg.setSegment("NSECM");
            leg.setBuySellFlag(buySellFlag);
            leg.setDerivativeType("EQUITY");
            leg.setLegType(LegStatus.TYPE_OPEN.getKey());

            leg.setNoOfLots(lots);
            leg.setLotSize(1L);
            leg.setQuantity(quantity);
            leg.setLatestUpdatedQuantity(quantity);

            // pricing/touchline
            leg.setExchangeInstrumentId(exchangeInstrumentId);
            leg.setClosingPrice(price1000);
            leg.setExecutedPrice(price1000);

            leg.setConstantDelta(0L);
            leg.setConstantIV(0L);
            leg.setBaseIndexPrice(0L);
            leg.setLatestIndexPrice(0L);

            boolean isPaper = strategy.getExecutionType()
                    .equalsIgnoreCase(ExecutionTypeMenu.PAPER_TRADING.getKey());

            leg.setExchangeStatus(LegExchangeStatus.CREATED.getKey());
            leg.setStatus(isPaper ? SignalStatus.LIVE.getKey() : LegStatus.EXCHANGE.getKey());

            if (isPaper) {
                leg.setFilledQuantity(quantity);
                leg.setExecutedTime(Instant.now());
            } else {
                leg.setFilledQuantity(0L);
            }

            // REQUIRED non-null field
            leg.setMultiOrdersFlag("N");

            // optional fields (defaults)
            leg.setProfitLoss(0L);
            leg.setTrailingStopLossPoints(0L);
            leg.setTrailingDistance(0L);
            leg.setTrailingTradePoint(0L);
            leg.setTrailingStopLossToggle("N");
            leg.setTrailingStopLossValue(0L);
            leg.setTrailingStopLossMtmValue(0L);

            leg.setLtp(price1000);
            leg.setMtm(0L);
            leg.setCurrentDelta(0L);
            leg.setCurrentIV(0L);
            leg.setValue(0L);
            leg.setStopLossUnitToggle(dto.getStopLossUnitToggle());
            leg.setStopLossUnitType(dto.getStopLossUnitType());
            leg.setStopLossUnitValue(dto.getStopLossUnitValue());

            legs.add(leg);
        }

        return legs;
    }
    /**
     * Initialize Redis state for equity legs to enable day's low SL and trailing SL.
     */
    private void initializeEquityLegsRedisState(Long strategyId, List<StrategyLeg> legs, List<SignalMapperDto> mapperList) {
        try {
            // Build a map of legName -> MarketData for quick lookup
            Map<String, MarketData> legNameToMarketData = new HashMap<>();
            for (SignalMapperDto dto : mapperList) {
                if (dto.getLegName() != null && dto.getTouchlineBinaryResponse() != null) {
                    legNameToMarketData.put(dto.getLegName(), dto.getTouchlineBinaryResponse());
                }
            }

            for (StrategyLeg leg : legs) {
                MarketData tl = legNameToMarketData.get(leg.getName());
                if (tl == null) {
                    logger.warn("[EQUITY] No touchline data for leg {} - skipping Redis init", leg.getName());
                    continue;
                }

                double entryPrice = leg.getExecutedPrice() / (double) AMOUNT_MULTIPLIER;
                double dayLow = tl.getLow();

                // Initialize with default TSL parameters (0.5% activation, 0.5% trailing distance)
                redisLegService.initEquityLegState(
                        strategyId,
                        leg.getId(),
                        leg.getName(),
                        entryPrice,
                        dayLow,
                        0.5,  // TSL activation %
                        0.5   // TSL trailing distance %
                );

                logger.debug("[EQUITY] Redis state initialized for leg {} | Entry={} | DayLow={}",
                        leg.getName(), entryPrice, dayLow);
            }
        } catch (Exception e) {
            logger.error("[EQUITY] Error initializing Redis state for legs: {}", e.getMessage(), e);
        }
    }
    /**
     * Creates an exit for a single equity leg (individual leg stoploss).
     * Does NOT exit the entire signal - only the specific leg that hit stoploss.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW, isolation = Isolation.READ_COMMITTED)
    public void createEquitySingleLegExit(Strategy strategyInput, StrategyLeg openLeg) {

        Strategy strategy = strategyRepository.findById(strategyInput.getId()).orElse(null);
        if (strategy == null) {
            logger.warn("[EQUITY-SL] Strategy not found: {}", strategyInput.getId());
            return;
        }

        // Re-fetch openLeg in this transaction to avoid detached entity issues
        Long openLegId = openLeg.getId();
        openLeg = strategyLegRepository.findByIdForUpdate(openLegId)
                .orElse(null);
        if (openLeg == null) {
            logger.warn("[EQUITY-SL] Open leg not found: {}", openLegId);
            return;
        }
        if (LegStatus.EXIT.getKey().equalsIgnoreCase(openLeg.getStatus())) {
            logger.warn("[EQUITY-SL] Already exited: {}", openLegId);
            return;
        }
        if (openLeg.getFilledQuantity() == null || openLeg.getFilledQuantity() <= 0) {
            logger.warn("[EQUITY-SL] Invalid filled quantity for leg: {}", openLegId);
            return;
        }
        Signal signal = openLeg.getSignal();
        if (signal == null) {
            logger.warn("[EQUITY-SL] Signal not found for leg: {}", openLegId);
            return;
        }

        MarketData tl = touchLineService.getTouchLine(String.valueOf(openLeg.getExchangeInstrumentId()));
        if (tl == null) {
            logger.warn("[EQUITY-SL] Touchline not found for instrument: {}", openLeg.getExchangeInstrumentId());
            return;
        }

        long exitPrice = (long) (tl.getLTP() * AMOUNT_MULTIPLIER);
        boolean isPaper = strategy.getExecutionType().equalsIgnoreCase(ExecutionTypeMenu.PAPER_TRADING.getKey());

        // Create EXIT leg
        StrategyLeg exitLeg = new StrategyLeg();
        exitLeg.setSignal(signal);
        exitLeg.setStrategy(strategy);
        exitLeg.setAppUser(strategy.getAppUser());
        exitLeg.setName(openLeg.getName());
        exitLeg.setSegment("NSECM");
        exitLeg.setDerivativeType("EQUITY");
        exitLeg.setLegType(LegType.EXIT.getKey());
        exitLeg.setBuySellFlag(openLeg.getBuySellFlag());
        exitLeg.setNoOfLots(openLeg.getNoOfLots());
        exitLeg.setLotSize(1L);
        exitLeg.setQuantity(openLeg.getFilledQuantity());
        exitLeg.setLatestUpdatedQuantity(openLeg.getFilledQuantity());
        exitLeg.setExchangeInstrumentId(openLeg.getExchangeInstrumentId());
        exitLeg.setExecutedPrice(exitPrice);
        exitLeg.setClosingPrice(exitPrice);
        exitLeg.setLtp(exitPrice);
        exitLeg.setExchangeStatus(LegExchangeStatus.CREATED.getKey());

        if (isPaper) {
            exitLeg.setStatus(SignalStatus.EXIT.getKey());
            exitLeg.setFilledQuantity(openLeg.getFilledQuantity());
            exitLeg.setExecutedTime(Instant.now());
        } else {
            exitLeg.setStatus(LegStatus.EXCHANGE.getKey());
            exitLeg.setFilledQuantity(0L);
        }

        // Required defaults
        exitLeg.setMultiOrdersFlag("N");
        exitLeg.setProfitLoss(0L);
        exitLeg.setMtm(0L);
        exitLeg.setValue(0L);
        exitLeg.setOptionType(null);
        exitLeg.setConstantDelta(0L);
        exitLeg.setConstantIV(0L);
        exitLeg.setCurrentDelta(0L);
        exitLeg.setCurrentIV(0L);
        exitLeg.setBaseIndexPrice(0L);
        exitLeg.setLatestIndexPrice(0L);

        // Mark open leg as exited
        openLeg.setStatus(LegStatus.EXIT.getKey());

        // Tag the exit leg with its source open-leg id so the UI mapper can pair them
        // back when multiple legs share an instrument (EMA-BB-Sell tranches). Other
        // code paths use legIdentifier="EQ_<signalId>_<legId>", so the "EQ_EXIT_" prefix
        // is unambiguous and only consumed by the tranche-aware branch.
        exitLeg.setLegIdentifier("EQ_EXIT_" + openLegId);

        // Lock realized P&L on the open leg so the closed-tranche row stops moving with
        // LTP. Both prices are already in paise (× AMOUNT_MULTIPLIER), so the rupee
        // round-trip cancels out: pnl_paise = (exit - entry) × qty × sign.
        double sign = LegSide.SELL.getKey().equalsIgnoreCase(openLeg.getBuySellFlag()) ? -1.0 : 1.0;
        openLeg.setProfitLoss((long) ((exitPrice - openLeg.getExecutedPrice()) * openLeg.getFilledQuantity() * sign));

        strategyLegRepository.save(exitLeg);
        strategyLegRepository.save(openLeg);

        logger.info("[EQUITY-SL] Single leg exit created | Strategy={} Leg={} Symbol={} ExitPrice={}",
                strategy.getId(), openLeg.getId(), openLeg.getName(), exitPrice / (double) AMOUNT_MULTIPLIER);

        // Calculate PnL for this leg (paper trading only)
        if (isPaper) {
            exitPNL.saveSingleLegPnl(exitLeg);
        }
    }
    @Transactional(
            propagation = Propagation.REQUIRED,
            rollbackFor = Exception.class,
            isolation = Isolation.READ_COMMITTED
    )
    public Signal createEquityExit(Strategy strategyInput) {

        Strategy strategy = strategyRepository
                .findById(strategyInput.getId())
                .orElse(null);
        if (strategy == null) return null;

        Optional<Signal> liveSignalOpt =
                signalRepository.findFirstByStrategyIdAndStatusOrderByCreatedAtDesc(
                        strategy.getId(),
                        SignalStatus.LIVE.getKey()
                );

        if (liveSignalOpt.isEmpty()) return null;

        Signal liveSignal =
                signalRepository.findByIdForUpdate(liveSignalOpt.get().getId())
                        .orElse(null);
        if (liveSignal == null) return null;

        Hibernate.initialize(liveSignal.getSignalLegs());

        boolean isPaper = strategy.getExecutionType()
                .equalsIgnoreCase(ExecutionTypeMenu.PAPER_TRADING.getKey());

        List<StrategyLeg> exitLegs = new ArrayList<>();

        List<StrategyLeg> openLegs = liveSignal.getSignalLegs().stream()
                .filter(leg -> LegType.OPEN.getKey().equalsIgnoreCase(leg.getLegType()))
                .toList();

        for (StrategyLeg openLeg : openLegs) {

            MarketData tl = touchLineService.getTouchLine(
                    String.valueOf(openLeg.getExchangeInstrumentId())
            );
            if (tl == null) continue;

            long exitPrice = (long) (tl.getLTP() * AMOUNT_MULTIPLIER);

            StrategyLeg exitLeg = new StrategyLeg();

            // ---- Core linkage ----
            exitLeg.setSignal(liveSignal);
            exitLeg.setStrategy(strategy);
            exitLeg.setAppUser(strategy.getAppUser());

            // ---- Identity ----
            exitLeg.setName(openLeg.getName());
            exitLeg.setSegment("NSECM");
            exitLeg.setDerivativeType("EQUITY");

            // ---- EXIT specifics ----
            exitLeg.setLegType(LegType.EXIT.getKey());
//            exitLeg.setBuySellFlag(
//                    LegSide.BUY.getKey().equalsIgnoreCase(openLeg.getBuySellFlag())
//                            ? LegSide.SELL.getKey()
//                            : LegSide.BUY.getKey()
//            );
            exitLeg.setBuySellFlag(openLeg.getBuySellFlag());

            // ---- Quantity ----
            exitLeg.setNoOfLots(openLeg.getNoOfLots());
            exitLeg.setLotSize(1L);
            exitLeg.setQuantity(openLeg.getFilledQuantity());
            exitLeg.setLatestUpdatedQuantity(openLeg.getFilledQuantity());

            // ---- Pricing ----
            exitLeg.setExchangeInstrumentId(openLeg.getExchangeInstrumentId());
            exitLeg.setExecutedPrice(exitPrice);
            exitLeg.setClosingPrice(exitPrice);
            exitLeg.setLtp(exitPrice);
            exitLeg.setExchangeStatus(LegExchangeStatus.CREATED.getKey());

            // ---- Execution state ----
            if (isPaper) {
                exitLeg.setStatus(SignalStatus.EXIT.getKey());
                exitLeg.setFilledQuantity(openLeg.getFilledQuantity());
                exitLeg.setExecutedTime(Instant.now());
            } else {
                exitLeg.setStatus(LegStatus.EXCHANGE.getKey());
                exitLeg.setExchangeStatus(LegExchangeStatus.CREATED.getKey());
                exitLeg.setFilledQuantity(0L);
            }

            // ---- Required defaults (to avoid NPE / DB break) ----
            exitLeg.setMultiOrdersFlag("N");
            exitLeg.setProfitLoss(0L);
            exitLeg.setMtm(0L);
            exitLeg.setValue(0L);

            // ---- Explicitly null / zero option-only fields ----
            exitLeg.setOptionType(null);
            exitLeg.setConstantDelta(0L);
            exitLeg.setConstantIV(0L);
            exitLeg.setCurrentDelta(0L);
            exitLeg.setCurrentIV(0L);
            exitLeg.setBaseIndexPrice(0L);
            exitLeg.setLatestIndexPrice(0L);

            // Tag the exit leg with its source open-leg id so the UI mapper can pair them
            // back when multiple legs share an instrument (EMA-BB-Sell tranches).
            exitLeg.setLegIdentifier("EQ_EXIT_" + openLeg.getId());

            // Lock realized P&L on the open leg so the closed-tranche row stops moving with
            // LTP. Both prices are already in paise (× AMOUNT_MULTIPLIER), so the rupee
            // round-trip cancels out: pnl_paise = (exit - entry) × qty × sign.
            double sign = LegSide.SELL.getKey().equalsIgnoreCase(openLeg.getBuySellFlag()) ? -1.0 : 1.0;
            openLeg.setProfitLoss((long) ((exitPrice - openLeg.getExecutedPrice()) * openLeg.getFilledQuantity() * sign));
            openLeg.setStatus(LegStatus.EXIT.getKey());

            exitLegs.add(exitLeg);
        }

        // ---- Persist legs ----
        if (!exitLegs.isEmpty()) {
            strategyLegRepository.saveAll(exitLegs);
        }
        // No need for strategyLegRepository.saveAll(liveSignal.getSignalLegs())
        // Dirty checking + final signalRepository.saveAndFlush will handle updates to existing legs.

        // ---- Update signal & strategy ----
        if (isPaper) {
            liveSignal.setStatus(SignalStatus.EXIT.getKey());
            strategy.setStatus(Status.EXIT.getKey());
        } else {
            liveSignal.setStatus(Status.EXIT_PENDING.getKey());
            strategy.setStatus(Status.EXIT_PENDING.getKey());
        }

        // Safely add exit legs to the signal's collection if not already added by setSignal()
        for (StrategyLeg exitLeg : exitLegs) {
            if (!liveSignal.getSignalLegs().contains(exitLeg)) {
                liveSignal.getSignalLegs().add(exitLeg);
            }
        }

        strategyRepository.save(strategy);
        Signal result = signalRepository.saveAndFlush(liveSignal);
        if (!strategy.getExecutionType().equalsIgnoreCase(ExecutionTypeMenu.LIVE_TRADING.getKey()))
            exitPNL.setFinalPNL(result);

        return liveSignal;
    }


}
