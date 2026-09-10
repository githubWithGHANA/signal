package com.quantlab.common.exception;


import com.quantlab.common.common.ApiResponse;
import com.quantlab.common.exception.custom.*;
import com.quantlab.common.utils.staticstore.dropdownutils.ApiStatus;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;

import java.util.List;
import java.util.stream.Collectors;


@ControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger logger = LogManager.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(StrategyNotFoundException.class)
    public ResponseEntity<ApiResponse<Object>> handleStrategyNotFound(StrategyNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new ApiResponse<>(
                ApiStatus.ERROR.getKey(),
                ex.getMessage(),
                null
        ));
    }

    @ExceptionHandler(SignalNotFoundException.class)
    public ResponseEntity<ApiResponse<Object>> handleSignalNotFound(SignalNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new ApiResponse<>(
                ApiStatus.ERROR.getKey(),
                ex.getMessage(),
                null
        ));
    }

    @ExceptionHandler(UnderlyingNotFoundException.class)
    public ResponseEntity<ApiResponse<Object>> handleUnderlyingNotFound(UnderlyingNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new ApiResponse<>(
                ApiStatus.ERROR.getKey(),
                ex.getMessage(),
                null
        ));
    }

    @ExceptionHandler(StrategyAlreadyLiveException.class)
    public ResponseEntity<ApiResponse<Object>> handleStrategyAlreadyLive(StrategyAlreadyLiveException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(new ApiResponse<>(
                ApiStatus.ERROR.getKey(),
                ex.getMessage(),
                null
        ));
    }

    @ExceptionHandler(StrategyAlreadyExistsException.class)
    public ResponseEntity<ApiResponse<Object>> handleStrategyAlreadyExists(StrategyAlreadyExistsException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(new ApiResponse<>(
                ApiStatus.ERROR.getKey(),
                ex.getMessage(),
                null
        ));
    }

    @ExceptionHandler(UserNotFoundException.class)
    public ResponseEntity<ApiResponse<Object>> handleUserNotFound (UserNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new ApiResponse<>(
                ApiStatus.ERROR.getKey(),
                ex.getMessage(),
                null
        ));
    }

//    @ExceptionHandler(Exception.class)  // Catch-all for all exceptions
    public ResponseEntity<ApiResponse<Void>> handleAllExceptions(Exception ex) {

        logger.error(ex.getMessage());
        // Create a generic ApiResponse with the error details
        ApiResponse<Void> response = new ApiResponse<>(
                "error",   // status
                "An unexpected error occurred",  // generic message
                List.of(new ErrorDetail("UNKNOWN_ERROR", "An unexpected error occurred. Please try again later.", null))  // error details
        );

        // Return response with Internal Server Error (500) status
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
    }

    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public ResponseEntity<ApiResponse<Void>> handleInternalServerExceptions(Exception ex) {
        logger.error(ex.getMessage());
        // Create a generic ApiResponse with the error details
        ApiResponse<Void> response = new ApiResponse<>(
                "error",   // status
                "An unexpected error occurred",  // generic message
                List.of(new ErrorDetail("INTERNAL_SERVER_ERROR", "Internal Server Error occurred. Please try again later.", null))  // error details
        );

        // Return response with Internal Server Error (500) status
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
    }

    @ExceptionHandler(AdminNotFoundException.class)
    public ResponseEntity<ApiResponse<Object>> handleAdminNotFound (AdminNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new ApiResponse<>(
                ApiStatus.ERROR.getKey(),
                ex.getMessage(),
                null
        ));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Object>> handleValidationExceptions(MethodArgumentNotValidException ex) {
        List<ErrorDetail> errors = ex.getBindingResult().getFieldErrors().stream()
                .map(error -> new ErrorDetail("VALIDATION_ERROR", error.getDefaultMessage(), error.getField()))
                .collect(Collectors.toList());

        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(new ApiResponse<>(
                ApiStatus.ERROR.getKey(),
                "Validation failed. Please check the provided inputs.",
                errors
        ));
    }

    @ExceptionHandler(InvalidExpiryLabelException.class)
    public ResponseEntity<ApiResponse<Void>> handleInvalidExpiryLabelException(InvalidExpiryLabelException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(
                new ApiResponse<>(
                        "error",
                        ex.getMessage(),
                        List.of(new ErrorDetail("INVALID_EXPIRY_LABEL", ex.getMessage(), "expiry"))
                )
        );
    }

}

