package com.quantlab.common.dao;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class StrategyIdAndSourceIdDAO {
    private Long Id;
    private Long sourceId;
}
