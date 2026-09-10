package com.quantlab.signal.dto;

import com.quantlab.common.dto.StrategyLegPNLDTO;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class StrategyLegTableDTO {
   private Long strategyId;
   private String strategyStatus;
   private Double strategyMTM;
   private Double indexBasePrice;
   private Double indexCurrentPrice;
   private long totalOrders;
   private long openOrders;
   private long closedOrders;
   private String executionType;
   private String positionType;
   private ArrayList<LegHoldingDTO> data;
}
