package com.quantlab.signal.dto;

import lombok.Data;

import java.io.Serializable;
import java.time.Instant;

@Data
public class StrategyDto  implements Serializable {
    private Long id;
    private Long strategyCategoryId;
    private EntryTimeDto entryDetails;
    private Long strategyAdditionsId;
    private ExitTimeDto exitDetails;
    private String name;
    private String description;
    private String typeOfStrategy;
    private UnderlyingDto underlying;
    private String positionType;
    private String status;
    private String atmType;
    private String expiry;
    private String expiryType;
    private String category;
    private Long minCapital;
    private Long multiplier;
    private Long stopLoss;
    private Long deltaSlippage;
    private Long target;
    private String executionType;
    private Integer reSignalCount;
    private String createdBy;
    private Instant createdAt;
    private String updatedBy;
    private Instant updatedAt;
    private String deleteIndicator;
    private String strategyTag;

}
