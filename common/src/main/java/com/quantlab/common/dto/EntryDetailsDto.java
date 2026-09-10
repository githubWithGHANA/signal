package com.quantlab.common.dto;

import lombok.Data;

import java.time.Instant;
import java.util.List;

@Data
public class EntryDetailsDto {
    private Instant entryTime;
    private String expiry;
    private List<Long> entryDaysList;
    private Integer entryHourTime;
    private Integer entryMinsTime;
}
