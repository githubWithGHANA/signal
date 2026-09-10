package com.quantlab.client.controllers;
import com.quantlab.client.dto.OrderRequestDto;
import com.quantlab.client.dto.OrderTableResponseDto;
import com.quantlab.client.service.OrderService;
import com.quantlab.common.common.ApiResponse;
import com.quantlab.common.exception.ErrorDetail;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/order")
public class OrderController {
    private static final Logger logger = LogManager.getLogger(OrderController.class);

    private OrderService orderService;

    public OrderController(OrderService orderService){
        this.orderService = orderService;
    }

    @GetMapping("/today")
    public ResponseEntity<ApiResponse<List<OrderTableResponseDto>>> getOrders (@RequestHeader("clientId") String clientId) {

        try {
            List<OrderTableResponseDto> res = orderService.getTodayOrders(clientId);
                return ResponseEntity.ok(new ApiResponse<>(
                        "Today's Orders retrieved successfully",
                        res
                ));

        } catch (Exception e) {
            logger.error("Error while retrieving Todays Orders : ", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(new ApiResponse<>(
                    "Failed to fetch Todays Orders",
                    null,
                    List.of(new ErrorDetail("ERROR_CODE", e.getMessage(), null)) // Return error details
            ));
        }
    }

    @GetMapping("/all")
    public ResponseEntity<ApiResponse<List<OrderTableResponseDto>>> getAllOrders (@RequestHeader("clientId") String clientId) {
        try {
            List<OrderTableResponseDto> res = orderService.getAllOrders(clientId);
            return ResponseEntity.ok(new ApiResponse<>(
                    "All Orders retrieved successfully",
                    res
            ));
        } catch (Exception e) {
            logger.error("Error while retrieving Orders : ", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(new ApiResponse<>(
                    "Failed to fetch Orders",
                    null,
                    List.of(new ErrorDetail("ERROR_CODE", e.getMessage(), null)) // Return error details
            ));
        }
    }

    @PostMapping("/custom-orders")
    public ResponseEntity<ApiResponse<List<OrderTableResponseDto>>> getOrdersByCustomDay(@RequestHeader("clientId") String clientId, @RequestBody OrderRequestDto requestDto) {

        try {
            List<OrderTableResponseDto> res = orderService.getOrdersByCustomDay(clientId, requestDto);
            return ResponseEntity.ok(new ApiResponse<>(
                    "Orders retrieved successfully",
                    res
            ));

        } catch (Exception e) {
            logger.error("Error while retrieving Orders : ", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(new ApiResponse<>(
                    "Failed to fetch Orders",
                    null,
                    List.of(new ErrorDetail("ERROR_CODE", e.getMessage(), null)) // Return error details
            ));
        }
    }

}
