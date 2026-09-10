package com.quantlab.signal.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class DeltaNeutralCheckDto {
    private String label;
    private int positive;
    private int negative;
}
