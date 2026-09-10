package com.quantlab.client.controllers;

import com.quantlab.client.dto.ActiveStrategiesResponseDto;
import com.quantlab.client.dto.AllStrategiesResDto;
import com.quantlab.client.dto.DeployedStratrgiesDto;
import com.quantlab.client.service.ErrorManagementService;
import com.quantlab.client.service.UserSignalService;
import com.quantlab.client.service.UserStrategyService;
import com.quantlab.common.common.ApiResponse;
import com.quantlab.common.exception.ErrorDetail;
import com.quantlab.common.utils.staticstore.dropdownutils.ApiStatus;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;


@RestController
@RequestMapping("/errormanagement/v2")
public class ErrorManagementControllerV2 {

    private static final Logger logger = LogManager.getLogger(ErrorManagementController.class);

    private final ErrorManagementService errorManagementService;

    private final UserSignalService signalService;

    private final UserStrategyService userStrategyService;

    public ErrorManagementControllerV2(ErrorManagementService errorManagementService, UserSignalService signalService, UserStrategyService strategyService){
        this.errorManagementService = errorManagementService;
        this.signalService = signalService;
        this.userStrategyService = strategyService;
    }

    @PostMapping("/manuallytraded")
    public ResponseEntity<ApiResponse<ActiveStrategiesResponseDto>> manuallyTraded(@RequestHeader("clientId") String clientId, @Validated @RequestParam Long strategyId) {
        try {
            Map<String, String> check = errorManagementService.manuallyTraded(clientId, strategyId);

            if (!check.containsKey("fail")) {
                ActiveStrategiesResponseDto activeStrategy = userStrategyService.convertToActiveStrategiesResponseDtoViaStrategyID(strategyId);
                ApiResponse<ActiveStrategiesResponseDto> res = new ApiResponse<>(
                        "Strategy set to Manually Traded Successfully",
                        activeStrategy
                );
                return ResponseEntity.ok(res);
            }else {
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(new ApiResponse<>(
                        ApiStatus.ERROR.getKey(),
                        check.get("fail"),
                        List.of(new ErrorDetail("ERROR_CODE", "Failed to set strategy to Manually Traded because, no legs found for signal", null))
                ));
            }
        } catch (Exception e) {
            logger.error("unable to Un-Deploy Strategy: ", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(new ApiResponse<>(
                    ApiStatus.ERROR.getKey(),
                    "Error trying to set strategy to Manually Traded ",
                    List.of(new ErrorDetail("ERROR_CODE", "Internal system error", null))
            ));
        }
    }

    @PostMapping("/cancelled")
    public ResponseEntity<ApiResponse<ActiveStrategiesResponseDto>> cancelled(@RequestHeader("clientId") String clientId, @Validated @RequestParam Long strategyId) {
        try {
            Map<String, String> check = errorManagementService.orderCancelled(clientId, strategyId);
            if (!check.containsKey("fail")) {
                ActiveStrategiesResponseDto activeStrategy = userStrategyService.convertToActiveStrategiesResponseDtoViaStrategyID(strategyId);
                ApiResponse<ActiveStrategiesResponseDto> res = new ApiResponse<>(
                        "Strategy cancelled successfully",
                        activeStrategy
                );
                return ResponseEntity.ok(res);
            }else{
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(new ApiResponse<>(
                        ApiStatus.ERROR.getKey(),
                        check.get("fail"),
                        List.of(new ErrorDetail("ERROR_CODE", check.get("fail"), null))
                ));
            }
        } catch (Exception e) {
            logger.error("unable to cancel Strategy: ", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(new ApiResponse<>(
                    ApiStatus.ERROR.getKey(),
                    "Error trying to cancel strategy ",
                    List.of(new ErrorDetail("ERROR_CODE", "Internal system error", null))
            ));
        }
    }

    @PostMapping("/retry")
    public ResponseEntity<ApiResponse<ActiveStrategiesResponseDto>> retry(@RequestHeader("clientId") String clientId, @Validated @RequestParam Long strategyId) {
        try {
            Map<String, String> check = errorManagementService.retrySignal(clientId, strategyId);

            if (!check.containsKey("fail")) {
                ActiveStrategiesResponseDto activeStrategy = userStrategyService.convertToActiveStrategiesResponseDtoViaStrategyID(strategyId);
                ApiResponse<ActiveStrategiesResponseDto> res = new ApiResponse<>(
                        "Retrying strategy",
                        activeStrategy
                );
                return ResponseEntity.ok(res);
            }else {
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(new ApiResponse<>(
                        ApiStatus.ERROR.getKey(),
                        check.get("fail"),
                        List.of(new ErrorDetail("ERROR_CODE", "Failed to retry strategy", null))
                ));
            }
        } catch (Exception e) {
            logger.error("Error retrying Strategy: ", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(new ApiResponse<>(
                    ApiStatus.ERROR.getKey(),
                    "Error retrying Strategy ",
                    List.of(new ErrorDetail("ERROR_CODE", "Internal system error", null))
            ));
        }
    }
}
