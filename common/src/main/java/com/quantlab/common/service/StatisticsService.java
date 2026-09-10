package com.quantlab.common.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.quantlab.common.dto.StatisticsResponseDto;
import com.quantlab.common.entity.AppUser;
import com.quantlab.common.entity.DailyStatisticsEntity;
import com.quantlab.common.entity.Strategy;
import com.quantlab.common.exception.custom.StrategyNotFoundException;
import com.quantlab.common.repository.AppUserRepository;
import com.quantlab.common.repository.DailyStatisticsRepository;
import com.quantlab.common.repository.StrategyRepository;

import com.quantlab.common.utils.staticstore.dropdownutils.SubscriptionStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Optional;

import static com.quantlab.common.utils.staticstore.AppConstants.AMOUNT_MULTIPLIER;

@Service
public class StatisticsService {

    private static final Logger log = LoggerFactory.getLogger(StatisticsService.class);


    @Autowired
    private StrategyRepository strategyRepository;

    @Autowired
    private AppUserRepository appUserRepository;

    @Autowired
    DailyStatisticsRepository dailyStatisticsRepository;

    @Autowired
    StatisticsProcessingService statisticsProcessingService;

    @Transactional
    public StatisticsResponseDto getStatistics(Long strategyId) {
        log.info("Fetching statistics for strategyId: {}", strategyId);

        try{
            Strategy strategy = strategyRepository.findById(strategyId).orElseThrow(() -> new StrategyNotFoundException("Strategy not found for StrategyId: " + strategyId));
            DailyStatisticsEntity existingRecord = dailyStatisticsRepository.findByStrategyId(strategyId);
            if (existingRecord != null && existingRecord.getExecutionType() != null && existingRecord.getExecutionType().equalsIgnoreCase(strategy.getExecutionType())) {
                    return jsonToStatisticsDTO(existingRecord.getStatsJson());
                }
            StatisticsResponseDto statistics = statisticsProcessingService.calculateNewStatistics(strategy);

            storeStatisticsInDB(strategy.getId(), statistics, strategy.getExecutionType());

        return statistics;
        }catch (Exception e) {
            log.error("Error fetching statistics for strategyId: {}", strategyId, e);
            throw new RuntimeException("Failed to fetch statistics");
        }
    }


    public Strategy findProcessingStrategy(Long strategyId) {
        Optional<Strategy> strategyOpt = strategyRepository.findById(strategyId);
        if (strategyOpt.isEmpty()) {
            log.warn("Strategy with ID {} not found", strategyId);
            throw new StrategyNotFoundException("Strategy not found for ID: " + strategyId);
        }
        Strategy strategy = strategyOpt.get();
        if (!SubscriptionStatus.START.getKey().equalsIgnoreCase(strategy.getSubscription()) && strategy.getSourceId() != null) {
            log.warn("Strategy with ID {} is not subscribed or and has sourceId", strategy.getId());
            AppUser firstUser = findFirstUser();
            Optional<Strategy> sourceStrategyOpt = strategyRepository.findByNameAndAppUser(strategy.getName(),firstUser);
            if (sourceStrategyOpt.isPresent()) {
                strategy = sourceStrategyOpt.get();
            } else {
                log.warn("No source strategy found for strategyId: {}", strategy.getSourceId());
            }
        }
        return strategy;
    }

    private AppUser findFirstUser() {

        return appUserRepository.findByUserRole_id(4L)
                .orElseThrow(() -> {
                    log.warn("No user found with role ID 4");
                    return new RuntimeException("No data found");
                });
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void storeStatisticsInDB(Long strategyId, StatisticsResponseDto<?> statistics, String  executionType) {
        try {
            DailyStatisticsEntity entity = new DailyStatisticsEntity();
            Optional<Strategy> strategyOpt = strategyRepository.findById(strategyId);
            if (strategyOpt.isEmpty()) {
                log.warn("Strategy with ID {} not found. Cannot store statistics.", strategyId);
                return;
            }
            DailyStatisticsEntity existingRecord = dailyStatisticsRepository.findByStrategyId(strategyId);
            if (existingRecord != null) {
                entity = existingRecord;
            }
            Strategy strategy = strategyOpt.get();

            entity.setStrategy(strategy);
            entity.setUserId(strategy.getAppUser().getUserId());
            entity.setDate(LocalDate.now());

            entity.setTotalProfit(statistics.getProfitStatistics().getTotalProfit() * AMOUNT_MULTIPLIER);
            entity.setTotalRoi(multiplyByAmount(statistics.getProfitStatistics().getTotalROI()));
            entity.setMaxDrawDown(statistics.getStatisticsIndexes().getMaxDrawDown());
            entity.setMaxDrawDownPercent(statistics.getStatisticsIndexes().getMaxDrawDownPercent());
            entity.setWinRate(statistics.getStatisticsIndexes().getWinRate());
            entity.setLossRate(statistics.getStatisticsIndexes().getLossRate());
            entity.setCapitalRequired(statistics.getStatisticsIndexes().getCapitalRequired());
            entity.setTotalTradingDays(statistics.getStatisticsIndexes().getTotalTradingDays());
            entity.setTotalTrades(statistics.getStatisticsIndexes().getTotalTrades());
            entity.setAvgTradesPerDay(statistics.getStatisticsIndexes().getAvgTradesPerDay());
            entity.setAvgDailyProfit(statistics.getStatisticsIndexes().getAvgDailyProfit());
            entity.setAvgProfitOnWinDays(statistics.getStatisticsIndexes().getAvgProfitOnWinDays());
            entity.setAvgLossOnLossDays(statistics.getStatisticsIndexes().getAvgLossOnLossDays());
            entity.setLastUpdated(LocalDateTime.now());
            String fullJson = toJson(statistics);
            entity.setStatsJson(fullJson);// full JSON object stored in column
            entity.setExecutionType(executionType);
            saveStatisticsEntity(entity);
            log.info("Daily statistics saved for strategyId: {}", strategyId);

        } catch (Exception e) {
            log.error("Error storing statistics for strategyId: {}", strategyId, e);
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void saveStatisticsEntity(DailyStatisticsEntity entity) {
        dailyStatisticsRepository.save(entity);
    }

    @Transactional
    public StatisticsResponseDto updateStatistics(Long strategyId) {
        log.info("processing statistics for strategyId: {}", strategyId);

        try{
            Strategy strategy = strategyRepository.findById(strategyId).orElseThrow(() -> new StrategyNotFoundException("Strategy not found for signalId: " + strategyId));
            StatisticsResponseDto statistics = statisticsProcessingService.calculateNewStatistics(strategy);
            storeStatisticsInDB(strategy.getId(), statistics, strategy.getExecutionType());
            return statistics;
        }catch (Exception e) {
            log.error("Error fetching statistics for strategyId: {}", strategyId, e);
            throw new RuntimeException("Failed to fetch statistics");
        }
    }

    public Long multiplyByAmount(Double a) {
        return (long) (a * AMOUNT_MULTIPLIER);
    }

    String toJson(StatisticsResponseDto<?> response ){

        ObjectMapper objectMapper = new ObjectMapper();
        try {
            String jsonString = objectMapper.writeValueAsString(response);
            return jsonString;
        } catch (Exception e) {
            e.printStackTrace();
        }
        return "";
    }

    private StatisticsResponseDto<?> jsonToStatisticsDTO(String json) throws JsonProcessingException {
        if (json != null && !json.isEmpty()) {
            ObjectMapper objectMapper = new ObjectMapper();
            StatisticsResponseDto<?> dto = objectMapper.readValue(
                    json, new TypeReference<StatisticsResponseDto<?>>() {}
            );
            return dto;
        }
        return null;
    }
}
