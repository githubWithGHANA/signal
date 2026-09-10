package com.quantlab.signal.dto.redisDto;

import lombok.Data;

import java.io.Serializable;

@Data
public class RedisLegState implements Serializable {
    private Long strategyId;
    private Long legId;
    private Double executedPrice;
    private Long entryTimestamp; // epoch millis
    private Double targetPct;
    private Double stopLossPct;
    private Boolean tslActive;
    private Double tslPoints;
    private Double tslAnchor;
    private Double trailingDistance;
    private Double lastLtp;
    private String status; // LIVE / EXIT / CLOSED
    private Long updatedAt;

}