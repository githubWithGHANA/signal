package com.quantlab.signal.dto;

import com.quantlab.common.dto.SelectionMenuLongDto;
import com.quantlab.common.dto.SelectionMenuStringDto;
import lombok.Data;

import java.util.List;

@Data
public class ActiveStrategiesDropdownDto {

    private List<SelectionMenuLongDto> Multiplier;

    private List<SelectionMenuStringDto> executionType;

}
