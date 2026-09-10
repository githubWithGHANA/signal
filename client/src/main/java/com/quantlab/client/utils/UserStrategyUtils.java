package com.quantlab.client.utils;

import com.quantlab.client.dto.EntryDetailsDto;
import com.quantlab.client.dto.ExitDetailsDto;
import com.quantlab.client.dto.StrategyDto;
import com.quantlab.client.dto.StrategyLegDto;
import com.quantlab.common.entity.Strategy;
import com.quantlab.common.entity.StrategyLeg;
import com.quantlab.common.utils.staticstore.dropdownutils.StrategyCategoryType;
import org.modelmapper.ModelMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import static com.quantlab.common.utils.staticstore.AppConstants.*;

@Component
public class UserStrategyUtils {
    private static final Logger log = LoggerFactory.getLogger(UserStrategyUtils.class);

    private final ModelMapper modelMapper;

    @Autowired  // Optional if only one constructor, Spring will auto-wire
    public UserStrategyUtils(ModelMapper modelMapper) {
        this.modelMapper = modelMapper;
    }

    public StrategyLegDto convertToStrategyLegDto(StrategyLeg strategyLeg) {
        StrategyLegDto strategyLegDto = new StrategyLegDto();
        strategyLegDto.setId(strategyLeg.getId());
        strategyLegDto.setPositions(strategyLeg.getBuySellFlag());
        strategyLegDto.setOptionType(strategyLeg.getOptionType());
        strategyLegDto.setLots(strategyLeg.getNoOfLots());
        strategyLegDto.setExpiry(strategyLeg.getLegExpName());
        strategyLegDto.setStrikeType(strategyLeg.getSktType());
        strategyLegDto.setStrikeSelection(strategyLeg.getSktSelection());
        if (strategyLeg.getSktSelectionValue() != null) strategyLegDto.setStrikeSelectionValue(strategyLeg.getSktSelectionValue());
        strategyLegDto.setTgtToggle(strategyLeg.getTargetUnitToggle());
        strategyLegDto.setTgtType(strategyLeg.getTargetUnitType());
        strategyLegDto.setTgtValue(strategyLeg.getTargetUnitValue() != null ? strategyLeg.getTargetUnitValue().toString() : null);
        strategyLegDto.setStopLossToggle(strategyLeg.getStopLossUnitToggle());
        strategyLegDto.setStopLossType(strategyLeg.getStopLossUnitType());
        strategyLegDto.setStopLossValue(strategyLeg.getStopLossUnitValue() != null ? strategyLeg.getStopLossUnitValue().toString() : null);
        strategyLegDto.setTslToggle(strategyLeg.getTrailingStopLossToggle());
        strategyLegDto.setTslType(strategyLeg.getTrailingStopLossType());
        strategyLegDto.setTslValue(strategyLeg.getTrailingStopLossValue());
        strategyLegDto.setTdValue(strategyLeg.getTrailingDistance());
        strategyLegDto.setDerivativeType(strategyLeg.getDerivativeType());
        return strategyLegDto;
    }

    // Map EntryDetails and ExitDetails to DTOs
    public void mapEntryAndExitDetails(Strategy strategy, StrategyDto strategyDto) {
        if (strategy.getEntryDetails() != null) {
            strategyDto.setEntryDetails(modelMapper.map(strategy.getEntryDetails(), EntryDetailsDto.class));
            if(strategy.getEntryDetails().getEntryDays() != null && !strategy.getEntryDetails().getEntryDays().isEmpty()){
                List<Long> entryDaysList = new ArrayList<>();
                strategy.getEntryDetails().getEntryDays().forEach(day -> {
                    entryDaysList.add(day.getId());
                });
                strategyDto.getEntryDetails().setEntryDaysList(entryDaysList);
            }
        } else {
            log.warn("Entry Details not found for strategy ID: {}", strategy.getId());
        }

        if (strategy.getExitDetails() != null) {
            strategyDto.setExitDetails(modelMapper.map(strategy.getExitDetails(), ExitDetailsDto.class));
        } else {
            log.warn("Exit Details not found for strategy ID: {}", strategy.getId());
        }
    }

    // Map StrategyLeg
    public void mapStrategyLeg(List<StrategyLeg> defaultLegs, StrategyDto strategyDto) {
        if (defaultLegs != null && !defaultLegs.isEmpty()) {
            List<StrategyLegDto> strategyLegDtos = defaultLegs.stream()
                    .map(this::convertToStrategyLegDto)
                    .collect(Collectors.toList());
            strategyDto.setStrategyLegs(strategyLegDtos);
        }
    }

    // Categorize Strategy
    public void categorizeStrategy(Strategy strategy, StrategyDto strategyDto,
                                   List<StrategyDto> diyList, List<StrategyDto> inHouseList, List<StrategyDto> prebuiltList, List<StrategyDto> popularList) {
        if (strategy.getStrategyCategory().getId() == 1L) {
            diyList.add(strategyDto);
        } else if (strategy.getStrategyCategory().getId() == 2L) {
            inHouseList.add(strategyDto);
        } else if (strategy.getStrategyCategory().getId() == 3L) {
            prebuiltList.add(strategyDto);
        } else if (strategy.getStrategyCategory().getId() == 4L) {
            popularList.add(strategyDto);
        } else {
            log.warn("Unknown strategy category for strategy ID: {}", strategy.getId());
        }
    }

    public StrategyDto converToStrategyDto(Strategy strategy, List<StrategyLeg> defaultLegs) {
        if (strategy == null) {
            return null;
        }

        StrategyDto strategyDto = new StrategyDto();

        strategyDto.setId(strategy.getId());
        strategyDto.setName(strategy.getName());
        strategyDto.setDescription(strategy.getDescription());
        strategyDto.setAtmType(strategy.getAtmType());
        strategyDto.setMultiplier(strategy.getMultiplier());
        strategyDto.setMinCapital(strategy.getMinCapital()/AMOUNT_MULTIPLIER);
        if (strategy.getUnderlying() != null) {
            strategyDto.setUnderlying(strategy.getUnderlying().getId().toString());
        }
        strategyDto.setTypeOfStrategy(strategy.getTypeOfStrategy());
        strategyDto.setPositionType(strategy.getPositionType());
        strategyDto.setExecutionType(strategy.getExecutionType());
//        strategyDto.setReSignalCount(strategy.getReSignalCount());
        strategyDto.setCreatedAt(strategy.getCreatedAt());
        strategyDto.setStrategyTag(strategy.getStrategyTag());
        strategyDto.setStatus(strategy.getStatus());
        strategyDto.setSubscription(strategy.getSubscription());
        String category;
        if (strategy.getCategory().equalsIgnoreCase(StrategyCategoryType.INHOUSE.getKey()))
            category =  "inHouse";
        else if (strategy.getCategory().equalsIgnoreCase(StrategyCategoryType.DIY.getKey()))
                category = "diy";
            else
                category = strategy.getCategory();

        strategyDto.setCategory(category);
        if (strategy.getDrawDown() != null)
//            strategyDto.setDrawDown(strategy.getDrawDown() / (double)AMOUNT_MULTIPLIER);

        // Map additions
//        if(strategy.getStrategyAdditions() != null){
//            strategyDto.setDeltaSlippage(strategy.getStrategyAdditions().getDeltaSlippage());
//        }

        // Map EntryDetails
        if (strategy.getEntryDetails() != null) {
            EntryDetailsDto entryDetailsDto = new EntryDetailsDto();
            entryDetailsDto.setEntryTime(strategy.getEntryDetails().getEntryTime());
            entryDetailsDto.setExpiry(strategy.getExpiry());
            // has to change
            entryDetailsDto.setEntryMinsTime(strategy.getEntryDetails().getEntryMinsTime());
            entryDetailsDto.setEntryHourTime(strategy.getEntryDetails().getEntryHourTime());
            if(strategy.getEntryDetails().getEntryDays() != null && !strategy.getEntryDetails().getEntryDays().isEmpty()){
                List<Long> entryDaysList = new ArrayList<>();
                strategy.getEntryDetails().getEntryDays().forEach(day -> {
                    entryDaysList.add(day.getId());
                });
                entryDetailsDto.setEntryDaysList(entryDaysList);
            }
            strategyDto.setEntryDetails(entryDetailsDto);
        } else {
            log.warn("Entry Details not found for strategy ID: {}", strategy.getId());
        }

        // map exit details
        if (strategy.getExitDetails() != null) {
            strategyDto.setExitDetails(modelMapper.map(strategy.getExitDetails(), ExitDetailsDto.class));
        } else {
            log.warn("Exit Details not found for strategy ID: {}", strategy.getId());
        }

        mapStrategyLeg(defaultLegs, strategyDto);

        return strategyDto;
    }

    public StrategyDto convertToStrategyDtoV2(Strategy strategy) {
        if (strategy == null) {
            return null;
        }

        StrategyDto strategyDto = new StrategyDto();

        strategyDto.setId(strategy.getId());
        strategyDto.setName(strategy.getName());
        strategyDto.setDescription(strategy.getDescription());
        strategyDto.setAtmType(strategy.getAtmType());
        strategyDto.setMultiplier(strategy.getMultiplier());
        strategyDto.setMinCapital(strategy.getMinCapital()/AMOUNT_MULTIPLIER);
        if (strategy.getUnderlying() != null) {
            strategyDto.setUnderlying(strategy.getUnderlying().getId().toString());
        }
        strategyDto.setTypeOfStrategy(strategy.getTypeOfStrategy());
        strategyDto.setPositionType(strategy.getPositionType());
        strategyDto.setExecutionType(strategy.getExecutionType());

        strategyDto.setCreatedAt(strategy.getCreatedAt());
        strategyDto.setStrategyTag(strategy.getStrategyTag());
        strategyDto.setStatus(strategy.getStatus());
        strategyDto.setSubscription(strategy.getSubscription());
        String category;
        if (strategy.getCategory().equalsIgnoreCase(StrategyCategoryType.INHOUSE.getKey()))
            category =  "In-House";
        else if (strategy.getCategory().equalsIgnoreCase(StrategyCategoryType.DIY.getKey()))
            category = "DIY";
        else
            category = strategy.getCategory();

        strategyDto.setCategory(category);
        if (strategy.getDrawDown() != null)
            strategyDto.setDrawDown(strategy.getDrawDown() / (double)AMOUNT_MULTIPLIER);

        if (strategy.getEntryDetails() != null) {
            EntryDetailsDto entryDetailsDto = new EntryDetailsDto();
            entryDetailsDto.setEntryTime(strategy.getEntryDetails().getEntryTime());
            entryDetailsDto.setExpiry(strategy.getExpiry());
            // has to change
            entryDetailsDto.setEntryMinsTime(strategy.getEntryDetails().getEntryMinsTime());
            entryDetailsDto.setEntryHourTime(strategy.getEntryDetails().getEntryHourTime());
            if(strategy.getEntryDetails().getEntryDays() != null && !strategy.getEntryDetails().getEntryDays().isEmpty()){
                List<Long> entryDaysList = new ArrayList<>();
                strategy.getEntryDetails().getEntryDays().forEach(day -> {
                    entryDaysList.add(day.getId());
                });
                entryDetailsDto.setEntryDaysList(entryDaysList);
            }
            strategyDto.setEntryDetails(entryDetailsDto);
        } else {
            log.warn("Entry Details not found for strategy ID: {}", strategy.getId());
        }

        // map exit details
        if (strategy.getExitDetails() != null) {
            strategyDto.setExitDetails(modelMapper.map(strategy.getExitDetails(), ExitDetailsDto.class));
        } else {
            log.warn("Exit Details not found for strategy ID: {}", strategy.getId());
        }
        return strategyDto;
    }

    /**
     * Map a lightweight StrategySummaryDao projection to StrategyDto to avoid fetching full Strategy entity.
     */
    public StrategyDto convertSummaryToStrategyDto(com.quantlab.common.dao.StrategySummaryDao summary, List<StrategyLeg> defaultLegs, com.quantlab.common.entity.EntryDetails entryDetails, com.quantlab.common.entity.ExitDetails exitDetails) {
        if (summary == null) return null;
        StrategyDto strategyDto = new StrategyDto();
        strategyDto.setId(summary.getId());
        strategyDto.setName(summary.getName());
        strategyDto.setDescription(summary.getDescription());
        strategyDto.setAtmType(summary.getAtmType());
        strategyDto.setMultiplier(summary.getMultiplier());
        if (summary.getMinCapital() != null) strategyDto.setMinCapital(summary.getMinCapital() / AMOUNT_MULTIPLIER);
        if (summary.getUnderlyingId() != null) strategyDto.setUnderlying(String.valueOf(summary.getUnderlyingId()));
        strategyDto.setPositionType(summary.getPositionType());
        strategyDto.setExecutionType(summary.getExecutionType());
        strategyDto.setCreatedAt(summary.getCreatedAt());
        strategyDto.setStrategyTag(summary.getStrategyTag());
        strategyDto.setStatus(summary.getStatus());
        strategyDto.setSubscription(summary.getSubscription());
        String category = summary.getCategory();
        if (category != null && category.equalsIgnoreCase("inhouse")) category = "inHouse";
        else if (category != null && category.equalsIgnoreCase("diy")) category = "diy";
        strategyDto.setCategory(category);
//        if (summary.getDrawDown() != null) strategyDto.setDrawDown(summary.getDrawDown() / (double) AMOUNT_MULTIPLIER);

        // Populate EntryDetailsDto from the EntryDetails entity if present
        if (entryDetails != null) {
            EntryDetailsDto entryDetailsDto = new EntryDetailsDto();
            entryDetailsDto.setEntryTime(entryDetails.getEntryTime());
            entryDetailsDto.setEntryHourTime(entryDetails.getEntryHourTime());
            entryDetailsDto.setEntryMinsTime(entryDetails.getEntryMinsTime());
            entryDetailsDto.setExpiry(summary.getExpiry());
            if (entryDetails.getEntryDays() != null && !entryDetails.getEntryDays().isEmpty()) {
                java.util.List<Long> entryDaysList = new java.util.ArrayList<>();
                entryDetails.getEntryDays().forEach(day -> entryDaysList.add(day.getId()));
                entryDetailsDto.setEntryDaysList(entryDaysList);
            }
            strategyDto.setEntryDetails(entryDetailsDto);
        } else {
            // fallback: set expiry if no EntryDetails entity
            EntryDetailsDto entryDetailsDto = new EntryDetailsDto();
            entryDetailsDto.setExpiry(summary.getExpiry());
            strategyDto.setEntryDetails(entryDetailsDto);
        }

        // Populate ExitDetailsDto from ExitDetails entity if present
        if (exitDetails != null) {
            ExitDetailsDto exitDetailsDto = modelMapper.map(exitDetails, ExitDetailsDto.class);
            strategyDto.setExitDetails(exitDetailsDto);
        }

        // map legs if present
        mapStrategyLeg(defaultLegs, strategyDto);

        return strategyDto;
    }

}
