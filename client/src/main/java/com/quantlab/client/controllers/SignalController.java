package com.quantlab.client.controllers;

import com.quantlab.client.dto.OpenLegsResDto;
import com.quantlab.client.service.UserSignalService;
import com.quantlab.common.common.ApiResponse;
import com.quantlab.common.exception.custom.ResourceNotFoundException;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;


@RestController
@RequestMapping("/signal")
public class SignalController {

    private static final Logger logger = LogManager.getLogger(SignalController.class);

    private final UserSignalService signalService;

    public SignalController(UserSignalService signalService){
        this.signalService = signalService;
    }

    @GetMapping("/openLegs")
    public ResponseEntity<ApiResponse<List<OpenLegsResDto>>> getActiveStrategies(@RequestHeader("clientId") String clientId, @RequestParam Long signalId) {
        logger.info("getting open legs list for signal id : "+signalId.toString());
        try{
        List<OpenLegsResDto> res = signalService.getOpenLegs(clientId, signalId);
        return ResponseEntity.ok(new ApiResponse<>(
                "Open legs retrieved successfully.",
                res));
        }catch(ResourceNotFoundException e){
            logger.error("Signal not found for ID: " + signalId, e);
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(new ApiResponse<>("Signal not found", null));
        } catch (Exception e) {
            logger.error("Error retrieving open legs for signal ID: " + signalId, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ApiResponse<>("An error occurred", null));
        }
    }
}
