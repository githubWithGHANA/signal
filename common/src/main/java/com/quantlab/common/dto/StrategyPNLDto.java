package com.quantlab.common.dto;

import com.quantlab.common.entity.Underlying;
import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class StrategyPNLDto {
    private Long id;
    private String category;
    private String expiry;
    private String underlyingName;
    private String status;
    private Long underlyingId;
    private String atmType;
    private String executionType;
    private Long signalId;

    public StrategyPNLDto(Object[] o) {
        this.id = ((Number) o[0]).longValue();
        this.category = (String) o[1];
        this.expiry = (String) o[2];
        this.underlyingName = (String) o[3];
        this.status = (String) o[4];
        this.underlyingId = ((Number) o[5]).longValue();
        this.atmType = (String) o[6];
        this.executionType = (String) o[7];
        this.signalId = ((Number) o[8]).longValue();

    }
}
