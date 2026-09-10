package com.quantlab.client.controllers;


import com.quantlab.client.dto.*;
import com.quantlab.client.service.UserSignalService;
import com.quantlab.client.service.UserStrategyService;
import com.quantlab.common.common.ApiResponse;
import com.quantlab.common.entity.StrategyLeg;
import com.quantlab.common.exception.ErrorDetail;
import com.quantlab.common.utils.staticstore.dropdownutils.ApiStatus;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/strategy/v2")
public class StrategyControllerV2 {

    private static final Logger logger = LogManager.getLogger(StrategyControllerV2.class);

    private final UserSignalService signalService;

    private final UserStrategyService strategyService;

    public StrategyControllerV2(UserSignalService signalService, UserStrategyService strategyService) {
        this.signalService = signalService;
        this.strategyService = strategyService;
    }


    @GetMapping("/active")
    public ResponseEntity<ApiResponse<DeployedStratrgiesDto>> getV2ActiveStrategies(@RequestHeader("clientId") String clientId) {
        try {
            DeployedStratrgiesDto activeStrategies = new DeployedStratrgiesDto();
            if (clientId != null)
                activeStrategies = signalService.getActiveStrategiesV2(clientId);

            if (activeStrategies != null) {
                return ResponseEntity.ok(new ApiResponse<>(
                        "Active strategies retrieved successfully",
                        activeStrategies
                ));
            } else {
                // No active strategies, but a valid request
                return ResponseEntity.ok(new ApiResponse<>(
                        "No active strategies available.",
                        null // null if no strategies are found
                ));
            }
        } catch (Exception e) {
            logger.error("Error while retrieving active strategies: {} , ",e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(new ApiResponse<>(
                    "Failed to fetch active strategies",
                    null,
                    List.of(new ErrorDetail("ERROR_CODE", e.getMessage(), null)) // Return error details
            ));
        }
    }

    @GetMapping("/details")
    public ResponseEntity<ApiResponse<DetailedStrategyDto>> getV2ActiveStrategyDetails(@RequestHeader("clientId") String clientId , @RequestParam Long strategyId) {
        try {
            DetailedStrategyDto activeStrategies = new DetailedStrategyDto();
            if (clientId != null)
                activeStrategies = signalService.activeStrategyDetailsV2(clientId , strategyId);

            if (activeStrategies != null) {
                return ResponseEntity.ok(new ApiResponse<>(
                        "Details retrieved successfully",
                        activeStrategies
                ));
            } else {
                // No active strategies, but a valid request
                return ResponseEntity.ok(new ApiResponse<>(
                        "No  detailed view for the strategies available.",
                        null // Return an empty list if no strategies are found
                ));
            }
        } catch (Exception e) {
            logger.error("Error while retrieving details strategies: {} , ",e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(new ApiResponse<>(
                    "Failed to fetch details strategies",
                    null,
                    List.of(new ErrorDetail("ERROR_CODE", e.getMessage(), null)) // Return error details
            ));
        }
    }

    @PostMapping("/oneclickdeploy")
    public ResponseEntity<ApiResponse<StrategyDto>> oneClickDeployV2(@RequestHeader("clientId") String clientId, @Validated @RequestBody OneClickDeployDto OneClickDeployDto) {
        logger.info("Executing V2-one-click deploy for strategy with id : {}", OneClickDeployDto.getStrategyId());

        try {
            StrategyDto strategyDto = strategyService.updateOneClickDeployV2(clientId, OneClickDeployDto);
            ApiResponse<StrategyDto> res = new ApiResponse<>(
                    "Strategy Deployed Successfully",
                    strategyDto );
            return ResponseEntity.ok(res);
        } catch (Exception e) {
            logger.error("unable to Deploy Strategy: ", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(new ApiResponse<>(
                    ApiStatus.ERROR.getKey(),
                    "Unable to Deploy "+e.getMessage(),
                    List.of(new ErrorDetail("ERROR_CODE", e.getMessage(), null))
            ));
        }
    }

    @PostMapping("/undeploy")
    public ResponseEntity<ApiResponse<StrategyDto>> unDeploySingleStrategyV2(@RequestHeader("clientId") String clientId, @Validated @RequestParam Long strategyId) {
        logger.info("Executing V2-UnDeploy for strategy with id : {}",strategyId);
        try {
            Boolean check = strategyService.unsubscribeSingleStrategy(clientId, strategyId);
            StrategyDto changedStrategy = strategyService.updateOneClickDeploy(strategyId);
            if (check) {
                ApiResponse<StrategyDto> res = new ApiResponse<>(
                        "Strategy Un-Deployed Successfully",
                        changedStrategy
                );
                return ResponseEntity.ok(res);
            }else {
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(new ApiResponse<>(
                        ApiStatus.ERROR.getKey(),
                        "Cannot unsubscribe Live strategy ",
                        List.of(new ErrorDetail("ERROR_CODE", "Cannot unsubscribe Live strategy", null))
                ));
            }
        } catch (Exception e) {
            logger.error("unable to Un-Deploy Strategy: ", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(new ApiResponse<>(
                    ApiStatus.ERROR.getKey(),
                    "Unable to Un-Deploy "+e.getMessage(),
                    List.of(new ErrorDetail("ERROR_CODE", e.getMessage(), null))
            ));
        }
    }

    @PostMapping("/unsubscribe")
    public ResponseEntity<ApiResponse<Boolean>> unsubscribeStrategyV2(@RequestHeader("clientId") String clientId, @Validated @RequestParam Long strategyId) {
        try {

            Boolean check = strategyService.unsubscribeSingleStrategy(clientId, strategyId);
//            Boolean activeStrategies = signalService.getActiveStrategies(clientId);
            if (check) {
                ApiResponse<Boolean> res = new ApiResponse<>(
                        "Strategy unsubscribed Successfully",
                        true
                );
                return ResponseEntity.ok(res);
            }else {
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(new ApiResponse<>(
                        ApiStatus.ERROR.getKey(),
                        "Cannot unsubscribe Live strategy, Exit Strategy first",
                        List.of(new ErrorDetail("ERROR_CODE", "Cannot unsubscribe Live strategy", null))
                ));
            }
        } catch (Exception e) {
            logger.error("Error un-subscribing strategy : ", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(new ApiResponse<>(
                    ApiStatus.ERROR.getKey(),
                    "Unable to unsubscribe "+e.getMessage(),
                    List.of(new ErrorDetail("ERROR_CODE", e.getMessage(), null))
            ));
        }
    }

    @PostMapping("/deploy")
    public ResponseEntity<ApiResponse<StrategyDto>> deployV2(@RequestHeader("clientId") String clientId, @Validated @RequestBody DeployReqDto deployReqDto) {
        logger.info("Received deploy-V2 request for strategy with ID: {}", deployReqDto.getStrategyId());

        try {
            StrategyDto allStrategiesResDto = strategyService.deploySaveStrategyV2(clientId, deployReqDto);

            // Return success response
            return ResponseEntity.ok(new ApiResponse<>(
                    "Strategy deployed successfully",
                    allStrategiesResDto
            ));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(new ApiResponse<>(
                    ApiStatus.ERROR.getKey(),
                    "Unable to deploy strategy, please try again",
                    List.of(new ErrorDetail("ERROR_CODE", e.getMessage(), null))
            ));
        }
    }

    @GetMapping("/readytodeploy")
    public ResponseEntity<ApiResponse<AllStrategiesResDto>> getAllStrategies(@RequestHeader("clientId") String clientId, @Validated @RequestParam String category) {
        logger.info("Received /readytodeploy request for clientId='{}', category='{}'", clientId, category);
        try {
            // Basic input validation
            if (clientId == null || clientId.isBlank()) {
                logger.warn("Missing or empty clientId in /readytodeploy request");
                return ResponseEntity.badRequest().body(new ApiResponse<>(
                        ApiStatus.ERROR.getKey(),
                        "Missing or invalid clientId",
                        null
                ));
            }

            if (category == null || category.isBlank()) {
                logger.warn("Missing or empty category in /readytodeploy request for clientId='{}'", clientId);
                return ResponseEntity.badRequest().body(new ApiResponse<>(
                        ApiStatus.ERROR.getKey(),
                        "Missing or invalid category",
                        null
                ));
            }

            // Normalize category input
            String normalizedCategory = category.trim();

            AllStrategiesResDto allStrategies = strategyService.getUserStrategiesByCategory(clientId, true, normalizedCategory);

            // If service returns null -> treat as no content
            if (allStrategies == null) {
                logger.debug("No strategies found for clientId='{}', category='{}'", clientId, normalizedCategory);
                return ResponseEntity.status(HttpStatus.NO_CONTENT).body(new ApiResponse<>(
                        "No strategies found for the specified category.",
                        null
                ));
            }

            return ResponseEntity.ok(new ApiResponse<>(
                    "All strategies retrieved successfully",
                    allStrategies
            ));
        } catch (IllegalArgumentException iae) {
            logger.warn("Invalid argument while fetching strategies for clientId='{}', category='{}' : {}", clientId, category, iae.getMessage());
            return ResponseEntity.badRequest().body(new ApiResponse<>(
                    ApiStatus.ERROR.getKey(),
                    iae.getMessage(),
                    null
            ));
        } catch (Exception e) {
            logger.error("Error while retrieving all strategies for clientId='{}', category='{}': ", clientId, category, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(new ApiResponse<>(
                    "Failed to fetch strategies",
                    null,
                    List.of(new ErrorDetail("ERROR_CODE", e.getMessage(), null))
            ));
        }
    }

    @PostMapping("/standby")
    public ResponseEntity<ApiResponse<ActiveStrategiesResponseDto>> standBy(@RequestHeader("clientId") String clientId, @RequestParam Long strategyId) throws Exception {
        try {
            logger.info("Received V2/StandBy request for strategy ID: {}", strategyId);

            ActiveStrategiesResponseDto strategy = strategyService.standBy(clientId, strategyId);
            if (strategy != null) {
                ApiResponse<ActiveStrategiesResponseDto> res = new ApiResponse<>(
                        "Status changed successfully",
                        strategy
                );
                return ResponseEntity.ok(res);
            }else {
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(new ApiResponse<>(
                        ApiStatus.ERROR.getKey(),
                        "Unable to pause as Strategy is Live",
                        List.of(new ErrorDetail("ERROR_CODE","Unable to change Strategy status.", null))
                ));
            }

        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(new ApiResponse<>(
                    ApiStatus.ERROR.getKey(),
                    "Unable to change strategy status, please try again",
                    List.of(new ErrorDetail("ERROR_CODE", e.getMessage(), null))
            ));
        }
    }

    @PostMapping("/exitstrategy")
    public ResponseEntity<ApiResponse<ActiveStrategiesResponseDto>> exitSingleStrategy(@RequestHeader("clientId") String clientId, @RequestBody ExitSingleStrategyDto exitSingleStrategyDto) throws Exception {
        logger.info("Received V2 request to exit strategy : "+exitSingleStrategyDto);

        try {
            List<StrategyLeg> exitStatus = strategyService.exitSingleStrategy(clientId, exitSingleStrategyDto);
            ActiveStrategiesResponseDto activeStrategy = signalService.convertToActiveStrategiesResponseDto(exitSingleStrategyDto);

            if (exitStatus != null){
                if (activeStrategy == null) {
                    return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(
                            new ApiResponse<>(
                                    ApiStatus.ERROR.getKey(),
                                    "Strategy exited but failed to retrieve updated details.",
                                    List.of(new ErrorDetail("ERROR_CODE", "Failed to retrieve updated strategy details", null))
                            )
                    );
                }
                return ResponseEntity.ok(
                        new ApiResponse<>(
                                "Strategy exited successfully",
                                activeStrategy
                        )
                );
            }else {
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(new ApiResponse<>(
                        ApiStatus.ERROR.getKey(),
                        "Strategy is already Exit",
                        List.of(new ErrorDetail("ERROR_CODE", "selected strategy is already Exit", null))
                ));
            }
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(new ApiResponse<>(
                    ApiStatus.ERROR.getKey(),
                    "Unable to exit selected strategy, please try again",
                    List.of(new ErrorDetail("ERROR_CODE", e.getMessage(), null))
            ));
        }
    }


    @GetMapping("/editstrategy")
    public ResponseEntity<ApiResponse<AllStrategiesResDto>> editStrategyV2(@RequestHeader("clientId") String clientId, @Validated @RequestParam Long strategyId) {
        logger.info("Executing V2-editstrategy for strategy with id : {}",strategyId);
        try {
            AllStrategiesResDto changedStrategy = strategyService.editStrategyDetails(strategyId, clientId);
                ApiResponse<AllStrategiesResDto> res = new ApiResponse<>(
                        "Strategy data fetched",
                        changedStrategy
                );
                return ResponseEntity.ok(res);
        } catch (Exception e) {
            logger.error("unable to fetch Strategy data: ", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(new ApiResponse<>(
                    ApiStatus.ERROR.getKey(),
                    "Unable to fetch Strategy data "+e.getMessage(),
                    List.of(new ErrorDetail("ERROR_CODE", e.getMessage(), null))
            ));
        }
    }


    @PostMapping("/toForwardTest")
    public ResponseEntity<ApiResponse<ActiveStrategiesResponseDto>> toForwardTest(@RequestHeader("clientId") String clientId, @RequestParam Long strategyId) throws Exception {
        try {
            ActiveStrategiesResponseDto strategyDto = strategyService.changeToPaperTradingV2(clientId, strategyId);

            ApiResponse<ActiveStrategiesResponseDto> res = new ApiResponse<>(
                    "ExecutionType set to Forward Test successfully ",
                    strategyDto
            );

            return ResponseEntity.ok(res);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(new ApiResponse<>(
                    ApiStatus.ERROR.getKey(),
                    "Unable to change to Forward Test please try again",
                    List.of(new ErrorDetail("ERROR_CODE", e.getMessage(), null))
            ));
        }
    }

    @PostMapping("/changemultiplier")
    public ResponseEntity<ApiResponse<ActiveStrategiesResponseDto>> changeMultiplier(@RequestHeader("clientId") String clientId, @Validated @RequestBody OneClickDeployDto OneClickDeployDto) {
        logger.info("changing multiplier for strategy with id : " + OneClickDeployDto.getStrategyId());
        try {
            ActiveStrategiesResponseDto strategyDto = strategyService.changeStrategyMultiplierV2(clientId, OneClickDeployDto);

            ApiResponse<ActiveStrategiesResponseDto> res = new ApiResponse<>(
                    "Multiplier is updated successfully",
                    strategyDto
            );
            return ResponseEntity.ok(res);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(new ApiResponse<>(
                    ApiStatus.ERROR.getKey(),
                    "Unable to change Multiplier, please try again",
                    List.of(new ErrorDetail("ERROR_CODE", e.getMessage(), null))
            ));
        }
    }

    @PostMapping("/toLiveTrading")
    public ResponseEntity<ApiResponse<ActiveStrategiesResponseDto>> toLiveTrading(@RequestHeader("clientId") String clientId, @RequestParam Long strategyId) throws Exception {
        try {
            ActiveStrategiesResponseDto strategyDto = strategyService.changeToLiveTradingV2(clientId, strategyId);

            ApiResponse<ActiveStrategiesResponseDto> res = new ApiResponse<>(
                    "ExecutionType set to LiveTrading successfully ",
                    strategyDto
            );

            return ResponseEntity.ok(res);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(new ApiResponse<>(
                    ApiStatus.ERROR.getKey(),
                    "Unable to change to LiveTrading please try again",
                    List.of(new ErrorDetail("ERROR_CODE", e.getMessage(), null))
            ));
        }
    }

    @PostMapping("/deleteStrategy")
    public ResponseEntity<ApiResponse<Boolean>> deleteStrategy(@RequestHeader("clientId") String clientId, @RequestParam Long strategyId) throws Exception {
        try {
            Boolean strategyDto = strategyService.deleteStrategyService(clientId, strategyId);

            ApiResponse<Boolean> res = new ApiResponse<>(
                    "delete strategy successfully ",
                    strategyDto
            );

            return ResponseEntity.ok(res);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(new ApiResponse<>(
                    ApiStatus.ERROR.getKey(),
                    "Unable to Delete strategy please try again",
                    List.of(new ErrorDetail("ERROR_CODE", e.getMessage(), null))
            ));
        }
    }
}
