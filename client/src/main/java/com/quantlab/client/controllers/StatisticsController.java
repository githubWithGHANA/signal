package com.quantlab.client.controllers;

import com.quantlab.common.dto.StatisticsResponseDto;
import com.quantlab.common.entity.Strategy;
import com.quantlab.common.service.StatisticsService;
import com.quantlab.common.common.ApiResponse;
import com.quantlab.common.utils.staticstore.dropdownutils.ApiStatus;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;


@RestController
@RequestMapping("/statistics")
public class StatisticsController {

    private static final Logger logger = LogManager.getLogger(StatisticsController.class);

    @Autowired
    private StatisticsService statisticsService;

    @GetMapping("/{strategyId}")
    public ResponseEntity<ApiResponse<StatisticsResponseDto>> getStatistics(@PathVariable Long strategyId) {
        logger.info("Getting statistics for strategyId: " + strategyId);

        Strategy strategy = statisticsService.findProcessingStrategy(strategyId);
        StatisticsResponseDto statistics = statisticsService.getStatistics(strategy.getId());
        return ResponseEntity.ok(new ApiResponse<>(
                ApiStatus.SUCCESS.getKey(),
                statistics
        ));
    }
}
