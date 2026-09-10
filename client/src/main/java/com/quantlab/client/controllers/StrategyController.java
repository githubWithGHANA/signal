package com.quantlab.client.controllers;

import com.quantlab.client.dto.*;
import com.quantlab.client.dto.DetailedStrategyDto;
import com.quantlab.client.service.DataLayerService;
import com.quantlab.client.service.UserSignalService;
import com.quantlab.client.service.UserStrategyService;
import com.quantlab.client.utils.StrategyLegExcelExporter;
import com.quantlab.common.common.ApiResponse;
import com.quantlab.common.entity.StrategyLeg;
import com.quantlab.common.exception.ErrorDetail;
import com.quantlab.common.utils.staticstore.dropdownutils.ApiStatus;
import com.quantlab.signal.dto.DeployedErrorDTO;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.io.ByteArrayInputStream;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@RestController
@RequestMapping("/strategy")
public class  StrategyController {

    private static final Logger logger = LogManager.getLogger(StrategyController.class);

    private final UserSignalService signalService;

    private final UserStrategyService strategyService;

    public StrategyController(UserSignalService signalService,UserStrategyService strategyService){
        this.signalService = signalService;
        this.strategyService = strategyService;
    }

    @GetMapping("/active")
    public ResponseEntity<ApiResponse<DeployedStratrgiesDto>> getActiveStrategies(@RequestHeader("clientId") String clientId) {
        try {
            DeployedStratrgiesDto activeStrategies = new DeployedStratrgiesDto();
            if (clientId != null)
                activeStrategies = signalService.getActiveStrategies(clientId);

            if (activeStrategies != null) {
                return ResponseEntity.ok(new ApiResponse<>(
                        "Active strategies retrieved successfully",
                        activeStrategies
                ));
            } else {
                // No active strategies, but a valid request
                return ResponseEntity.ok(new ApiResponse<>(
                        "No active strategies available.",
                        null // Return an empty list if no strategies are found
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


    @GetMapping("/all")
    public ResponseEntity<ApiResponse<AllStrategiesResDto>> getAllStrategies(@RequestHeader("clientId") String clientId) {
        try {
            AllStrategiesResDto allStrategies = strategyService.getAllStrategies(clientId, true);

            // Check if result is null or empty
            if (allStrategies != null) {
                return ResponseEntity.ok(new ApiResponse<>(
                        "All strategies retrieved successfully",
                        allStrategies
                ));
            } else {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new ApiResponse<>(
                        ApiStatus.ERROR.getKey(),
                        "No strategies found.",
                        null
                ));
            }
        } catch (Exception e) {
            logger.error("Error while retrieving all strategies: ", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(new ApiResponse<>(
                    "Failed to fetch strategies",
                    null,
                    List.of(new ErrorDetail("ERROR_CODE", e.getMessage(), null)) // You can create a detailed error message here
            ));
        }
    }


    @PostMapping("/deploy")
    public ResponseEntity<ApiResponse<AllStrategiesResDto>> createDeploy(@RequestHeader("clientId") String clientId, @Validated @RequestBody DeployReqDto deployReqDto) {
        logger.info("Received deployment request for strategy with ID: {}", deployReqDto.getStrategyId());

        try {
            AllStrategiesResDto allStrategiesResDto = strategyService.deploySaveStrategy(clientId, deployReqDto);

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


    @PostMapping("/oneclickdeploy")
    public ResponseEntity<ApiResponse<AllStrategiesResDto>> oneClickDeploy(@RequestHeader("clientId") String clientId, @Validated @RequestBody OneClickDeployDto OneClickDeployDto) {
        logger.info("Executing one click deploy for strategy with id : " + OneClickDeployDto.getStrategyId());

            AllStrategiesResDto allStrategiesResDto = strategyService.oneClickDeploy(clientId, OneClickDeployDto);
            return ResponseEntity.ok(new ApiResponse<>(
                    "strategy deployed successfully",
                    allStrategiesResDto
            ));
    }

    @PostMapping("/exitall")
    public ResponseEntity<ApiResponse<Void>> exitAll(@RequestHeader("clientId") String clientId) throws Exception {

        try {
            logger.info("Received request to exit all strategies for client id  : " + clientId);

            boolean exitstatus = strategyService.exitAll(clientId);
            long startTime = System.currentTimeMillis();
            ApiResponse<Void> res = new ApiResponse<>(
                    exitstatus? ApiStatus.SUCCESS.getKey():ApiStatus.ERROR.getKey(),
                    exitstatus?"All strategies exited successfully":"Unable to exit all strategies, please try again",
                    null
            );
            return ResponseEntity.ok(res);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(new ApiResponse<>(
                    ApiStatus.ERROR.getKey(),
                    "Error,Unable to exit all strategies, please try again",
                    List.of(new ErrorDetail("ERROR_CODE", e.getMessage(), null))
            ));
        }

    }

    @PostMapping("/exitstrategy")
    public ResponseEntity<ApiResponse<DeployedStratrgiesDto>> exitSingleStrategy(@RequestHeader("clientId") String clientId, @RequestBody ExitSingleStrategyDto exitSingleStrategyDto) throws Exception {
        logger.info("Received request to exitstrategy : "+exitSingleStrategyDto);

        try {
            List<StrategyLeg> exitstatus = strategyService.exitSingleStrategy(clientId, exitSingleStrategyDto);
//            long startTime = System.currentTimeMillis();
            DeployedStratrgiesDto activeStrategies = signalService.getActiveStrategies(clientId);

            if (exitstatus != null){
                ApiResponse<DeployedStratrgiesDto> res = new ApiResponse<>(
                    "Strategy exited successfully",
                    activeStrategies
                );
                return ResponseEntity.ok(res);
            }else {
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(new ApiResponse<>(
                        ApiStatus.ERROR.getKey(),
                        "Strategy is already Exit",
                        List.of(new ErrorDetail("ERROR_CODE", "selected strategy is already Exit", null))
                ));
            }

            //     List<LegorderDto> stratagy= strategyService.deploySingleStrategy();

            //     logger.info("strategy deploy time: " + (System.currentTimeMillis() - startTime));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(new ApiResponse<>(
                    ApiStatus.ERROR.getKey(),
                    "Unable to exit selected strategies, please try again",
                    List.of(new ErrorDetail("ERROR_CODE", e.getMessage(), null))
            ));
        }
    }

    @PostMapping("/standby")
    public ResponseEntity<ApiResponse<DeployedStratrgiesDto>> standBy(@RequestHeader("clientId") String clientId, @RequestParam Long strategyId) throws Exception {

        try {
            ActiveStrategiesResponseDto status = strategyService.standBy(clientId, strategyId);
            if (status != null) {
                DeployedStratrgiesDto activeStrategies = signalService.getActiveStrategies(clientId);
                ApiResponse<DeployedStratrgiesDto> res = new ApiResponse<>(
                        "Status changed successfully",
                        activeStrategies
                );
                return ResponseEntity.ok(res);
            }else {
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(new ApiResponse<>(
                        ApiStatus.ERROR.getKey(),
                        "Unable to change Strategy status.",
                        List.of(new ErrorDetail("ERROR_CODE","Unable to pause as Strategy is Live", null))
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

    @PostMapping("/pause")
    public ResponseEntity<ApiResponse<DeployedStratrgiesDto>> pauseALl(@RequestHeader("clientId") String clientId) throws Exception {

        try {
            strategyService.pauseByUser(clientId);
            DeployedStratrgiesDto activeStrategies = signalService.getActiveStrategies(clientId);

            ApiResponse<DeployedStratrgiesDto> res = new ApiResponse<>(
                    "Status changed successfully",
                    activeStrategies
            );

            return ResponseEntity.ok(res);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(new ApiResponse<>(
                    ApiStatus.ERROR.getKey(),
                    "Unable to pause all strategies, please try again",
                    List.of(new ErrorDetail("ERROR_CODE", e.getMessage(), null))
            ));
        }
    }

    @PostMapping("/unsubscribe")
    public ResponseEntity<ApiResponse<DeployedStratrgiesDto>> unsubscribeSingleStrategy(@RequestHeader("clientId") String clientId, @Validated @RequestParam Long strategyId) {
        try {

            Boolean check = strategyService.unsubscribeSingleStrategy(clientId, strategyId);
            DeployedStratrgiesDto activeStrategies = signalService.getActiveStrategies(clientId);
            if (check) {
                ApiResponse<DeployedStratrgiesDto> res = new ApiResponse<>(
                        "Strategy unsubscribed Successfully",
                        activeStrategies
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

    @GetMapping("/logs/today")
    public ResponseEntity<ApiResponse<DeployedErrorDTO>> todayError(@RequestHeader("clientId") String clientId, @RequestParam Long strategyId) throws Exception {

        try {
            DeployedErrorDTO deployedErrorDTO = strategyService.fetchTodayErrorForStrategy(clientId, strategyId);

            return ResponseEntity.ok(new ApiResponse<>(
                    "all errors today, of strategy retrieved ",
                    deployedErrorDTO
            ));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(new ApiResponse<>(
                    ApiStatus.ERROR.getKey(),
                    "Unable to fetch errors of selected strategy, please try again",
                    List.of(new ErrorDetail("ERROR_CODE", e.getMessage(), null))
            ));
        }
    }

    @PostMapping("/toLiveTrading")
    public ResponseEntity<ApiResponse<DeployedStratrgiesDto>> toLiveTrading(@RequestHeader("clientId") String clientId, @RequestParam Long strategyId) throws Exception {
        try {
            Boolean status = strategyService.changeToLiveTrading(clientId, strategyId);
            DeployedStratrgiesDto activeStrategies = signalService.getActiveStrategies(clientId);
            ApiResponse<DeployedStratrgiesDto> res = new ApiResponse<>(
                    "ExecutionType set to Live Trading successfully ",
                    activeStrategies
            );

            return ResponseEntity.ok(res);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(new ApiResponse<>(
                    ApiStatus.ERROR.getKey(),
                    "Unable to change strategy to Live Trading, please try again",
                    List.of(new ErrorDetail("ERROR_CODE", e.getMessage(), null))
            ));
        }
    }


    @PostMapping("/toForwardTest")
    public ResponseEntity<ApiResponse<DeployedStratrgiesDto>> toForwardTest(@RequestHeader("clientId") String clientId, @RequestParam Long strategyId) throws Exception {
        try {
            Boolean status = strategyService.changeToPaperTrading(clientId, strategyId);
            DeployedStratrgiesDto activeStrategies = signalService.getActiveStrategies(clientId);
            ApiResponse<DeployedStratrgiesDto> res = new ApiResponse<>(
                    "ExecutionType set to Forward Test successfully ",
                    activeStrategies
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
    public ResponseEntity<ApiResponse<DeployedStratrgiesDto>> changeMultiplier(@RequestHeader("clientId") String clientId, @Validated @RequestBody OneClickDeployDto OneClickDeployDto) {
        logger.info("changing multiplier for strategy with id : " + OneClickDeployDto.getStrategyId());
        try {
            Boolean changed = strategyService.changeStrategyMultiplier(clientId, OneClickDeployDto);
            DeployedStratrgiesDto activeStrategies = signalService.getActiveStrategies(clientId);
            ApiResponse<DeployedStratrgiesDto> res = new ApiResponse<>(
                    "Multiplier is updated successfully",
                    activeStrategies
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

    @PostMapping("/undeploy")
    public ResponseEntity<ApiResponse<AllStrategiesResDto>> unDeploySingleStrategy(@RequestHeader("clientId") String clientId, @Validated @RequestParam Long strategyId) {
        try {
            Boolean check = strategyService.unsubscribeSingleStrategy(clientId, strategyId);
            AllStrategiesResDto allStrategies = strategyService.getAllStrategies(clientId, true);
            if (check) {
                ApiResponse<AllStrategiesResDto> res = new ApiResponse<>(
                        "Strategy Un-Deployed Successfully",
                        allStrategies
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

    @PostMapping("/manualentry")
    public ResponseEntity<ApiResponse<DeployedStratrgiesDto>> manualEntry(@RequestHeader("clientId") String clientId, @RequestParam Long strategyId) throws Exception {
        try {
            DeployedStratrgiesDto activeStrategies = strategyService.processManualEntry(clientId, strategyId);
            ApiResponse<DeployedStratrgiesDto> res = new ApiResponse<>(
                    "Strategy status updated successfully ",
                    activeStrategies
            );

            return ResponseEntity.ok(res);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(new ApiResponse<>(
                    ApiStatus.ERROR.getKey(),
                    "Error occurred during manual entry",
                    List.of(new ErrorDetail("ERROR_CODE", e.getMessage(), null))
            ));
        }
    }


    @GetMapping("/strategydownload")
    public ResponseEntity<ApiResponse<DeployedStratrgiesDto>> downloadStrategyData(@RequestHeader("clientId") String clientId, @RequestParam Long strategyId) throws Exception {
        try {
            DeployedStratrgiesDto activeStrategies = signalService.strategyDataDownload(clientId, strategyId);
            ApiResponse<DeployedStratrgiesDto> res = new ApiResponse<>(
                    "Fetched entire strategy data successfully ",
                    activeStrategies
            );

            return ResponseEntity.ok(res);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(new ApiResponse<>(
                    ApiStatus.ERROR.getKey(),
                    "Unable to fetch strategy data, please try again",
                    List.of(new ErrorDetail("ERROR_CODE", e.getMessage(), null))
            ));
        }
    }

    @GetMapping("/export")
    public ResponseEntity<?> downloadStrategy(@RequestHeader("clientId") String clientId, @RequestParam Long strategyId) throws Exception {
        try {
            DeployedStratrgiesDto activeStrategies = signalService.strategyDataDownload(clientId, strategyId);
            ByteArrayInputStream in = StrategyLegExcelExporter.exportToExcel(activeStrategies);
            InputStreamResource file = new InputStreamResource(in);

            String strategyName = activeStrategies.getActiveStrategiesResponse().get(0).getName().replaceAll("[^a-zA-Z0-9 ]", "").trim();
            String date = java.time.LocalDate.now().toString();
            String fileName = strategyName + "-" + date + ".xlsx";

            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION,
                            "attachment; filename=\"" + fileName + "\"; filename*=UTF-8''" + URLEncoder.encode(fileName, "UTF-8"))
                    .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                    .body(file);

        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(new ApiResponse<>(
                    ApiStatus.ERROR.getKey(),
                    "Unable to fetch strategy data, please try again",
                    List.of(new ErrorDetail("ERROR_CODE", e.getMessage(), null))
            ));
        }
    }
}
