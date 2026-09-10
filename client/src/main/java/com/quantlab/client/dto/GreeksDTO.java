package com.quantlab.client.dto;

import lombok.Data;

@Data
public class GreeksDTO {
    double currentLegLTP = 0;
    double currentIV = 0;
    double currentDelta = 0;
    double constantIV = 0;
    double constantDelta = 0;
}
