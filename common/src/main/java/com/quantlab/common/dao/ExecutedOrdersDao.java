package com.quantlab.common.dao;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@AllArgsConstructor
@NoArgsConstructor
@Data
public class ExecutedOrdersDao {
    private String orderId;
    private String instrumentName;
    private String orderSide;
    private Long cumulativeQuantity;
    private String averageTradedPrice;
    private String clientID;
    private String userName;
    private String strategyName;
    private String exchangeTimeStamp;

}
