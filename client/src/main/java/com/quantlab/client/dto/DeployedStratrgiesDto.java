package com.quantlab.client.dto;

import lombok.Data;

import java.util.List;

@Data
public class DeployedStratrgiesDto {

    private List<ActiveStrategiesResponseDto> activeStrategiesResponse;

    private ActiveStrategiesDropdownDto activeStrategiesDropdown;

}
