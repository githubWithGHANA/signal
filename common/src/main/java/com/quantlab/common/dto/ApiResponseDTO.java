package com.quantlab.common.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.quantlab.common.dto.ClientDetailsDTO;
import lombok.Data;

@Data
public class ApiResponseDTO {
    private Long code;
    private String message;
    private DataResponseDTO data;


    public com.quantlab.common.dto.ClientDetailsDTO processResponse(DataResponseDTO data){
        return data.processData();
    }
}

