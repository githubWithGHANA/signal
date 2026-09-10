package com.quantlab.signal.dto;

import lombok.Data;

import java.time.Instant;

@Data
public class ExitTimeDto {
    private Instant exitTime;
    private  Long id;
}

