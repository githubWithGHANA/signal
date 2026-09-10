package com.quantlab.common.common;

import com.quantlab.common.exception.ErrorDetail;
import com.quantlab.common.utils.staticstore.dropdownutils.ApiStatus;
import lombok.Data;

import java.util.List;


@Data
public class ApiResponse<T> {

    private String status; // 'success' or 'error'
    private Boolean success;
    private String message; // Human-readable message
    private T data; // For success responses, holds the data
    private List<ErrorDetail> errors; // For error responses, holds the error details

    // Constructor for success response
    public ApiResponse( String message, T data) {
        this.status = ApiStatus.SUCCESS.getKey();
        this.message = message;
        this.data = data;
        this.success = true;
        this.errors = null;
    }

    // Constructor for error response
    public ApiResponse(String status, String message, List<ErrorDetail> errors) {
        this.status = status;
        this.message = message;
        this.data = null;
        this.success = false;
        this.errors = errors;
    }
}
