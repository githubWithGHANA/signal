package com.quantlab.common.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.Data;
@Data
public class MinMaxDto {
    @NotBlank(message = "clientId is required.")
    String clientId;
    @NotNull(message = "maxLoss is required.")
    @Min(value = 0, message = "maxLoss must be 0 or a positive value")
    Double maxLoss;
    @NotNull(message = "minProfit is required.")
    @Min(value = 0, message = "minProfit must be 0 or a positive value")
    Double minProfit;
}