package com.quantlab.common.dto;

import lombok.Data;

import java.util.List;

@Data
public class DeployDropdownDto {

    private List<SelectionMenuStringDto> atmType;

    private List<SelectionMenuLongDto> Multiplier;

    private List<SelectionMenuLongDto> underlying;

    private List<SelectionMenuStringDto> order;

    private List<SelectionMenuStringDto> executionType;

    private List<SelectionMenuStringDto> expiry;

    private List<SelectionMenuLongDto> entryDays;

    private List<SelectionMenuStringDto> mtmType;


}
