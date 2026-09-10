package com.quantlab.signal.dto;

import lombok.Data;

import java.time.Instant;

@Data
public class EntryTimeDto {
    private Instant entryTime;
    private Long id;
}

