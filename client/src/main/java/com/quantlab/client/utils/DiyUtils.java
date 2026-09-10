package com.quantlab.client.utils;

import com.quantlab.client.dto.DiyLegDTO;
import com.quantlab.client.dto.DiyReqDto;
import com.quantlab.common.entity.*;
import com.quantlab.common.exception.custom.InvalidExpiryLabelException;
import com.quantlab.common.exception.custom.UnderlyingNotFoundException;
import com.quantlab.common.repository.*;
import com.quantlab.common.utils.staticstore.DescriptionInfo;
import com.quantlab.common.utils.staticstore.dropdownutils.ExecutionTypeMenu;
import com.quantlab.common.utils.staticstore.dropdownutils.ManualExit;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.*;

import static com.quantlab.common.utils.staticstore.AppConstants.*;

@Component
public class DiyUtils {

    private static final Logger logger = LogManager.getLogger(DiyUtils.class);

    @Autowired
    EntryDaysRespository entryDaysRespository;

    @Autowired
    EntryDetailsRepository entryDetailsRepository;

    @Autowired
    ExitDetailsRepository exitDetailsRepository;

    @Autowired
    UnderlyingRespository underlyingRespository;

    @Autowired
    StrategyCategoryRepository strategyCategoryRepository;

    public Strategy updateStrategy(DiyReqDto diyReqDto, Strategy strategy) throws UnderlyingNotFoundException {
        strategy.setName(diyReqDto.getStrategyName());
        strategy.setMinCapital(diyReqDto.getCapital());
        strategy.setPositionType(diyReqDto.getStrategyType());

        // update entryDetails entry
        EntryDetails entryDetails = new EntryDetails();
        // get list of days from given dto
        List<EntryDays> entryDaysList = entryDaysRespository.findByIdIn(diyReqDto.getEntryOnDays());
        entryDetails.setEntryDays(entryDaysList);
        entryDetails.setStrategy(strategy);
        strategy.setEntryDetails(entryDetails);
        // save underlying
        Optional<Underlying> underlying = underlyingRespository.findById(diyReqDto.getIndex());
        // throw error if it doesn't exist
        if(underlying.isEmpty()){
            throw new UnderlyingNotFoundException("Underlying not found for the id : " + diyReqDto.getIndex());
        }
        strategy.setUnderlying(underlying.get());
        Long profitMtmUnitValue = diyReqDto.getTargetMtmValue(), stoplossMtmValue = diyReqDto.getStopLossMtmValue();
        // if type is in percent convert it to amount
        if (diyReqDto.getTargetMtmType().equalsIgnoreCase(PERCENTOFCAPITAL)) {
            double res = (double) diyReqDto.getCapital() * diyReqDto.getTargetMtmValue() / 100;
            profitMtmUnitValue = Math.round(res);
        }
        if (diyReqDto.getStopLossMtmType().equalsIgnoreCase(PERCENTOFCAPITAL)) {
            double res = (double) diyReqDto.getCapital() * diyReqDto.getStopLossMtmValue() / 100;
            stoplossMtmValue = Math.round(res);
        }

        // map exit details
        ExitDetails exitDetails = new ExitDetails();
        exitDetails.setStrategy(strategy);
        exitDetails.setExitOnExpiryFlag(diyReqDto.getExitOnExpiry());
        exitDetails.setExitAfterEntryDays(diyReqDto.getExitAfterEntryDays());
        exitDetails.setTargetUnitToggle(diyReqDto.getTargetMtmToggle());
        exitDetails.setTargetUnitType(diyReqDto.getTargetMtmType());
        exitDetails.setTargetUnitValue(diyReqDto.getTargetMtmValue());
        exitDetails.setStopLossUnitToggle(diyReqDto.getStopLossMtmToggle());
        exitDetails.setStopLossUnitType(diyReqDto.getStopLossMtmType());
        exitDetails.setStopLossUnitValue(diyReqDto.getStopLossMtmValue());
        exitDetails.setProfitMtmUnitValue(profitMtmUnitValue);
        exitDetails.setStoplossMtmUnitValue(stoplossMtmValue);
        exitDetails.setExitHourTime(diyReqDto.getExitHours());
        exitDetails.setExitMinsTime(diyReqDto.getExitMinutes());
        exitDetails.setExitTime(Instant.now().plus(diyReqDto.getExitHours(), ChronoUnit.HOURS).plus(diyReqDto.getExitMinutes(), ChronoUnit.MINUTES));
        exitDetails.setStrategy(strategy);
        strategy.setExitDetails(exitDetails);
        return strategy;
    }

    public Strategy getStrategyFromDIY(DiyReqDto diyReqDto, Strategy strategy) throws Exception {
        // === Base strategy details ===
        strategy.setName(diyReqDto.getStrategyName());
        strategy.setDescription("Saved from DIY form");
        strategy.setMinCapital(diyReqDto.getCapital() * AMOUNT_MULTIPLIER);
        strategy.setPositionType(diyReqDto.getStrategyType());
        strategy.setManualExitType(ManualExit.DISABLED.getKey());

        // === Execution type ===
        if (diyReqDto.getExecutionTypeId() != null) {
            strategy.setExecutionType(diyReqDto.getExecutionTypeId());
        } else {
            strategy.setExecutionType(ExecutionTypeMenu.LIVE_TRADING.getKey());
        }

        // === Expiry selection logic (mandatory, no default) ===
        String expiryLabel = diyReqDto.getLegs() != null ? diyReqDto.getLegs().stream()
                .map(DiyLegDTO::getExpiry)
                .filter(Objects::nonNull)
                .filter(exp -> !exp.isBlank())
                .findFirst()
                .orElse(null) : null;

        if (expiryLabel == null) {
            throw new InvalidExpiryLabelException("Expiry is mandatory but not provided in any leg.");
        }

        strategy.setExpiry(expiryLabel);

        // === Multiplier handling ===
        if (strategy.getMultiplier() != null && strategy.getMultiplier() == 0) {
            strategy.setMultiplier(DEFAULT_MULTIPLIER);
        }

        // === Strategy category ===
        Optional<StrategyCategory> strategyCategoryOptional = strategyCategoryRepository.findById(1L);
        if (strategyCategoryOptional.isEmpty()) {
            throw new Exception("Strategy category not found");
        }
        strategy.setStrategyCategory(strategyCategoryOptional.get());
        strategy.setStrategyTag(com.quantlab.common.utils.staticstore.dropdownutils.StrategyCategoryType.DIY.getKey());
        strategy.setStatus(com.quantlab.common.utils.staticstore.dropdownutils.Status.INACTIVE.getKey());

        // === Entry Details ===
        List<EntryDays> entryDaysList = entryDaysRespository.findByIdIn(diyReqDto.getEntryOnDays());
        LocalDateTime localDateTime = LocalDateTime.now()
                .withHour(diyReqDto.getEntryHours())
                .withMinute(diyReqDto.getEntryMinutes())
                .withSecond(0)
                .withNano(0);
        ZonedDateTime zonedDateTime = localDateTime.atZone(ZoneId.systemDefault());

        if (strategy.getEntryDetails() == null) {
            EntryDetails entryDetails = new EntryDetails();
            entryDetails.setEntryDays(entryDaysList);
            entryDetails.setEntryHourTime(diyReqDto.getEntryHours());
            entryDetails.setEntryMinsTime(diyReqDto.getEntryMinutes());
            entryDetails.setEntryTime(zonedDateTime.toInstant());
            entryDetails.setStrategy(strategy);
            strategy.setEntryDetails(entryDetails);
        } else {
            strategy.getEntryDetails().getEntryDays().clear();
            strategy.getEntryDetails().setEntryDays(entryDaysList);
            strategy.getEntryDetails().setEntryHourTime(diyReqDto.getEntryHours());
            strategy.getEntryDetails().setEntryMinsTime(diyReqDto.getEntryMinutes());
            strategy.getEntryDetails().setEntryTime(zonedDateTime.toInstant());
            entryDetailsRepository.save(strategy.getEntryDetails());
        }

        // === Underlying ===
        Optional<Underlying> underlying = underlyingRespository.findById(diyReqDto.getIndex());
        if (underlying.isEmpty()) {
            throw new UnderlyingNotFoundException("Underlying not found for the id : " + diyReqDto.getIndex());
        }
        strategy.setUnderlying(underlying.get());

        // === MTM Calculations ===
        Long profitMtmUnitValue = diyReqDto.getTargetMtmValue();
        Long stoplossMtmValue = diyReqDto.getStopLossMtmValue();

        if (TOGGLE_TRUE.equalsIgnoreCase(diyReqDto.getTargetMtmToggle())
                && PERCENTOFCAPITAL.equalsIgnoreCase(diyReqDto.getTargetMtmType())) {
            profitMtmUnitValue = Math.round((double) diyReqDto.getCapital() * diyReqDto.getTargetMtmValue() / 100);
        }

        if (TOGGLE_TRUE.equalsIgnoreCase(diyReqDto.getStopLossMtmToggle())
                && PERCENTOFCAPITAL.equalsIgnoreCase(diyReqDto.getStopLossMtmType())) {
            stoplossMtmValue = Math.round((double) diyReqDto.getCapital() * diyReqDto.getStopLossMtmValue() / 100);
        }

        // === Exit Details ===
        ExitDetails exitDetails;
        if (strategy.getExitDetails() == null) {
            exitDetails = new ExitDetails();
            exitDetails.setStrategy(strategy);
        } else {
            exitDetails = strategy.getExitDetails();
        }

        exitDetails.setExitOnExpiryFlag(diyReqDto.getExitOnExpiry());
        exitDetails.setExitAfterEntryDays(diyReqDto.getExitAfterEntryDays());
        exitDetails.setTargetUnitToggle(diyReqDto.getTargetMtmToggle());
        exitDetails.setTargetUnitType(diyReqDto.getTargetMtmType());
        exitDetails.setTargetUnitValue(diyReqDto.getTargetMtmValue());
        exitDetails.setStopLossUnitToggle(diyReqDto.getStopLossMtmToggle());
        exitDetails.setStopLossUnitType(diyReqDto.getStopLossMtmType());
        exitDetails.setStopLossUnitValue(diyReqDto.getStopLossMtmValue());
        exitDetails.setProfitMtmUnitValue(profitMtmUnitValue);
        exitDetails.setStoplossMtmUnitValue(stoplossMtmValue);
        exitDetails.setExitHourTime(diyReqDto.getExitHours());
        exitDetails.setExitMinsTime(diyReqDto.getExitMinutes());
        exitDetails.setExitTime(
                Instant.now()
                        .plus(diyReqDto.getExitHours(), ChronoUnit.HOURS)
                        .plus(diyReqDto.getExitMinutes(), ChronoUnit.MINUTES)
        );
        exitDetails.setStatus("");
        exitDetails.setStrategy(strategy);
        strategy.setExitDetails(exitDetails);

        return strategy;
    }


    public StrategyLeg updateStrategyLegFromDIY(DiyLegDTO diyLegDTO, StrategyLeg strategyLeg) throws Exception {
        strategyLeg.setBuySellFlag(diyLegDTO.getPosition());
        strategyLeg.setNoOfLots(diyLegDTO.getLots());
        strategyLeg.setLegExpName(diyLegDTO.getExpiry());
        strategyLeg.setSktSelection(diyLegDTO.getStrikeSelection());
        strategyLeg.setSktType(diyLegDTO.getStrikeType());
        strategyLeg.setTargetUnitToggle(diyLegDTO.getTgtToogle().toString());
        strategyLeg.setTargetUnitType(diyLegDTO.getTgtType());
        strategyLeg.setTargetUnitValue(diyLegDTO.getTgtValue());
        strategyLeg.setStopLossUnitToggle(diyLegDTO.getStopLossToggle().toString());
        strategyLeg.setStopLossUnitType(diyLegDTO.getStopLossType());
        strategyLeg.setStopLossUnitValue(diyLegDTO.getStopLossValue());
        strategyLeg.setLatestUpdatedQuantity(diyLegDTO.getLots());
        strategyLeg.setQuantity(diyLegDTO.getLots());
        strategyLeg.setOptionType(diyLegDTO.getOptionType());
        strategyLeg.setTrailingStopLossToggle(diyLegDTO.getTslToggle().toString());
        strategyLeg.setTrailingStopLossType(diyLegDTO.getTslType());
        strategyLeg.setTrailingStopLossValue(diyLegDTO.getTslValue());
        strategyLeg.setTrailingDistance(diyLegDTO.getTdValue());
        return strategyLeg;
    }

    public StrategyLeg getStrategyLegFromDIY(DiyLegDTO diyLegDTO) throws Exception {
        StrategyLeg strategyLeg = new StrategyLeg();
        strategyLeg.setBuySellFlag(diyLegDTO.getPosition());
        strategyLeg.setNoOfLots(diyLegDTO.getLots());
        strategyLeg.setLegExpName(diyLegDTO.getExpiry());
        strategyLeg.setSktSelection(diyLegDTO.getStrikeSelection());
        strategyLeg.setSktType(diyLegDTO.getStrikeType());
        strategyLeg.setSktSelectionValue(diyLegDTO.getStrikeSelectionValue());
        strategyLeg.setTargetUnitToggle(diyLegDTO.getTgtToogle().toString());
        strategyLeg.setTargetUnitType(diyLegDTO.getTgtType());
        strategyLeg.setTargetUnitValue(diyLegDTO.getTgtValue());
        strategyLeg.setStopLossUnitToggle(diyLegDTO.getStopLossToggle().toString());
        strategyLeg.setStopLossUnitType(diyLegDTO.getStopLossType());
        strategyLeg.setStopLossUnitValue(diyLegDTO.getStopLossValue());
        strategyLeg.setLatestUpdatedQuantity(diyLegDTO.getLots());
        strategyLeg.setQuantity(diyLegDTO.getLots());
        strategyLeg.setOptionType(diyLegDTO.getOptionType());
        if (diyLegDTO.getTslToggle() != null)
            strategyLeg.setTrailingStopLossToggle(diyLegDTO.getTslToggle().toString());
        strategyLeg.setTrailingStopLossType(diyLegDTO.getTslType());
        strategyLeg.setTrailingStopLossValue(diyLegDTO.getTslValue());
        strategyLeg.setTrailingDistance(diyLegDTO.getTdValue());
        strategyLeg.setLegType(com.quantlab.common.utils.staticstore.dropdownutils.StrategyCategoryType.DIY.getKey());
        strategyLeg.setSegment("NSEFO");
        strategyLeg.setMultiOrdersFlag("y");
        strategyLeg.setStatus("");
        strategyLeg.setDerivativeType(diyLegDTO.getDerivativeType());

        return strategyLeg;
    }

    public DiyLegDTO mapStrategyLegToDiyReqDto(DiyLegDTO diyLegDTO, StrategyLeg strategyLeg) {
        diyLegDTO.setPosition(strategyLeg.getBuySellFlag());
        diyLegDTO.setLots(strategyLeg.getNoOfLots());
        diyLegDTO.setExpiry(strategyLeg.getLegExpName());
        diyLegDTO.setStrikeType(strategyLeg.getSktType());
        diyLegDTO.setTgtType(strategyLeg.getTargetUnitType());
        diyLegDTO.setTgtValue(strategyLeg.getTargetUnitValue());
        diyLegDTO.setStopLossType(strategyLeg.getStopLossUnitType());
        diyLegDTO.setStopLossValue(strategyLeg.getStopLossUnitValue());
        return diyLegDTO;
    }

    public static Map<String, String> mapToStrategyDescriptionInfo() {
        Map<String, String> descriptions = new HashMap<>();
        descriptions.put("strategyName", DescriptionInfo.STRATEGY_NAME);
        descriptions.put("index", DescriptionInfo.INDEX);
        descriptions.put("capital", DescriptionInfo.CAPITAL);
        descriptions.put("executionType", DescriptionInfo.EXECUTION_TYPE);
        descriptions.put("strategyType", DescriptionInfo.STRATEGY_TYPE);
        descriptions.put("entryTime", DescriptionInfo.ENTRY_TIME);
        descriptions.put("enterOnDays", DescriptionInfo.ENTER_ON_DAYS);
        descriptions.put("exitTime", DescriptionInfo.EXIT_TIME);
        descriptions.put("exitOnExpiry", DescriptionInfo.EXIT_ON_EXPIRY);
        descriptions.put("exitAfterEntryDays", DescriptionInfo.EXIT_AFTER_ENTRY_DAYS);
        descriptions.put("profitMtm", DescriptionInfo.PROFIT_MTM);
        descriptions.put("stopLossMtm", DescriptionInfo.STOPlOSS_MTM);
        descriptions.put("segment", DescriptionInfo.SEGMENT);
        descriptions.put("position", DescriptionInfo.POSITION);
        descriptions.put("optionType", DescriptionInfo.OPTION_TYPE);
        descriptions.put("lots", DescriptionInfo.LOTS);
        descriptions.put("expiry", DescriptionInfo.EXPIRY);
        descriptions.put("strikeSelection", DescriptionInfo.STRIKE_SELECTION);
        descriptions.put("strikeType", DescriptionInfo.STRIKE_TYPE);
        descriptions.put("target", DescriptionInfo.TARGET);
        descriptions.put("stopLoss", DescriptionInfo.STOPLOSS);
        descriptions.put("tsl", DescriptionInfo.TSL);
        descriptions.put("trailingDistance", DescriptionInfo.TRAILING_DISTANCE);
        return descriptions;
    }
    public DiyReqDto mapStrategyToDiyReqDto(DiyReqDto diyReqDto, Strategy strategy) {
        // Map Strategy fields
        diyReqDto.setStrategyName(strategy.getName());
        diyReqDto.setCapital(strategy.getMinCapital());
        diyReqDto.setStrategyType(strategy.getTypeOfStrategy());

        // Map EntryDetails
        if (strategy.getEntryDetails() != null) {
            EntryDetails entryDetails = strategy.getEntryDetails();
            diyReqDto.setEntryHours(entryDetails.getEntryHourTime());
            diyReqDto.setEntryMinutes(entryDetails.getEntryMinsTime());

            List<Long> entryDayIds = entryDetails.getEntryDays().stream()
                    .map(EntryDays::getId)
                    .toList();
            diyReqDto.setEntryOnDays(entryDayIds);
        }

        // Map Underlying
        if (strategy.getUnderlying() != null) {
            diyReqDto.setIndex(strategy.getUnderlying().getId());
        }

        // Map ExitDetails
        if (strategy.getExitDetails() != null) {
            ExitDetails exitDetails = strategy.getExitDetails();
            diyReqDto.setExitOnExpiry(exitDetails.getExitOnExpiryFlag());
            diyReqDto.setExitAfterEntryDays(exitDetails.getExitAfterEntryDays());
            diyReqDto.setTargetMtmToggle(exitDetails.getTargetUnitToggle());
            diyReqDto.setTargetMtmType(exitDetails.getTargetUnitType());
            diyReqDto.setTargetMtmValue(exitDetails.getTargetUnitValue());
            diyReqDto.setStopLossMtmToggle(exitDetails.getStopLossUnitToggle());
            diyReqDto.setStopLossMtmType(exitDetails.getStopLossUnitType());
            diyReqDto.setStopLossMtmValue(exitDetails.getStoplossMtmUnitValue());
            diyReqDto.setExitHours(exitDetails.getExitHourTime());
            diyReqDto.setExitMinutes(exitDetails.getExitMinsTime());
        }

        // Map Strategy Legs individually with correct expiry per leg
        if (strategy.getStrategyLeg() != null && !strategy.getStrategyLeg().isEmpty()) {
            List<DiyLegDTO> diyLegList = strategy.getStrategyLeg().stream()
                    .map(leg -> {
                        DiyLegDTO diyLegDTO = new DiyLegDTO();
                        diyLegDTO.setPosition(leg.getBuySellFlag());
                        diyLegDTO.setLots(leg.getNoOfLots());
                        diyLegDTO.setExpiry(leg.getLegExpName());
                        diyLegDTO.setStrikeSelection(leg.getSktSelection());
                        diyLegDTO.setStrikeSelectionValue(leg.getSktSelectionValue());
                        diyLegDTO.setStrikeType(leg.getSktType());
                        diyLegDTO.setOptionType(leg.getOptionType());
                        diyLegDTO.setTgtToogle("y".equalsIgnoreCase(leg.getTargetUnitToggle()));
                        diyLegDTO.setTgtType(leg.getTargetUnitType());
                        diyLegDTO.setTgtValue(leg.getTargetUnitValue());
                        diyLegDTO.setStopLossToggle("y".equalsIgnoreCase(leg.getStopLossUnitToggle()));
                        diyLegDTO.setStopLossType(leg.getStopLossUnitType());
                        diyLegDTO.setStopLossValue(leg.getStopLossUnitValue());
                        diyLegDTO.setTslToggle("y".equalsIgnoreCase(leg.getTrailingStopLossToggle()));
                        diyLegDTO.setTslType(leg.getTrailingStopLossType());
                        diyLegDTO.setTslValue(leg.getTrailingStopLossValue());
                        diyLegDTO.setTdValue(leg.getTrailingDistance());
                        diyLegDTO.setDerivativeType(leg.getDerivativeType());
                        return diyLegDTO;
                    })
                    .toList();

            diyReqDto.setLegs(diyLegList);
        }
        return diyReqDto;
    }
}