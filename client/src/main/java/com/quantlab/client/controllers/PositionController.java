package com.quantlab.client.controllers;

import com.quantlab.client.dto.OrderTableResponseDto;
import com.quantlab.client.dto.PositionTableResponseDto;
import com.quantlab.client.service.PositionService;
import com.quantlab.common.common.ApiResponse;
import com.quantlab.common.exception.ErrorDetail;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/position")
public class PositionController {
    private static final Logger logger = LogManager.getLogger(PositionController.class);

    @Autowired
    PositionService positionService;

    @GetMapping("/today")
    public ResponseEntity<ApiResponse<List<PositionTableResponseDto>>> getPosition (@RequestHeader("clientId") String clientId) {
        try {
            List<PositionTableResponseDto> res = positionService.getPosition(clientId);
            return ResponseEntity.ok(new ApiResponse<>("Positions retrieved successfully.", res));
        }catch (Exception e){
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(new ApiResponse<>(
                    "Failed to fetch today positions",
                    null,
                    List.of(new ErrorDetail("ERROR_CODE", e.getMessage(), null)) // Return error details
            ));
        }
    }
}
