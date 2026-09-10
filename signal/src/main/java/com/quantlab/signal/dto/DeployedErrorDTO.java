package com.quantlab.signal.dto;

import jakarta.persistence.Column;
import lombok.Data;
import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Getter
@Setter
public class DeployedErrorDTO {

    private Long strategyId;

    private String userId;

    private String deployedOn;

    List<StrategyErrorDetails> statusList = new ArrayList<>();
}
