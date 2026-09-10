package com.quantlab.common.dto;


import lombok.Data;

import java.util.List;

@Data
public class DiyDropDownDto {

    private List<SelectionMenuStringDto> strategyType;

    private List<SelectionMenuLongDto> daysMenu;

    private List<SelectionMenuStringDto> segmentType;

    private List<SelectionMenuStringDto> profitMtm;

    private List<SelectionMenuStringDto> expiryType;

    private List<SelectionMenuStringDto> order;

    private List<SelectionMenuLongDto> lot;

    private List<SelectionMenuStringDto> strikeSelection;

    private List<SelectionMenuStringDto> strikeType;

    private List<SelectionMenuLongDto> underlyingMenu;

    private List<SelectionMenuIntegerDto> exitAfterEntry;

    private List<SelectionMenuStringDto> Tgt;

    private List<SelectionMenuStringDto> Trl;

    private List<SelectionMenuStringDto> ExitOnExpiry;

    private List<SelectionMenuStringDto> executionType;
}
