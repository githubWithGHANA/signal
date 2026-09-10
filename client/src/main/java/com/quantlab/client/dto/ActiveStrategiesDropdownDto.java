package com.quantlab.client.dto;

import lombok.Data;

import java.util.List;

@Data
public class ActiveStrategiesDropdownDto {

    private List<SelectionMenuLongDto> Multiplier;

    private List<SelectionMenuStringDto> executionType;

}
