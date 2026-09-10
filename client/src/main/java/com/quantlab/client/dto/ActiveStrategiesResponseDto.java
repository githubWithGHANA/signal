package com.quantlab.client.dto;

import com.quantlab.common.entity.Signal;
import com.quantlab.common.entity.Strategy;
import com.quantlab.common.entity.StrategyLeg;
import com.quantlab.signal.dto.HoldingsDTO;
import com.quantlab.signal.dto.LegHoldingDTO;
import com.quantlab.signal.dto.StrategyLegTableDTO;
import com.quantlab.signal.dto.redisDto.MarketData;
import lombok.Data;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import static com.quantlab.common.utils.staticstore.AppConstants.*;
import static com.quantlab.common.utils.staticstore.AppConstants.AMOUNT_MULTIPLIER;

@Data
public class ActiveStrategiesResponseDto {

    private String name;

    private Long sId;

    private String deployedOn;

    private String execution;

    private String status;

    private Long capital;

    private String multiplier;

    private Integer counter;

    private String category;

    private Long requiredCapital;

    private String positionType;

    private Double strategyMtm;


    private StrategyLegTableDTO strategyLegTableDTO = new StrategyLegTableDTO();


}
