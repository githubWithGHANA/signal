package com.quantlab.client.dto;



import lombok.Data;

import java.io.Serializable;
import java.time.Instant;
import java.util.List;

@Data
public class StrategyDto implements Serializable {

     private Long id;
     private String name;
     private String description;
     private EntryDetailsDto entryDetails;
     private ExitDetailsDto exitDetails;
     private String atmType;
     private Long multiplier;
     private Long minCapital;
     private String underlying;
     private String typeOfStrategy;
     private String positionType;
     private String executionType;
     private Instant createdAt;
     private String strategyTag;
     private String status;
     private String category;
     private Double drawDown;
     private String subscription;
     private List<StrategyLegDto> strategyLegs;
//     private Integer reSignalCount;
//     private Long deltaSlippage;

}
