package com.quantlab.client.dto;

import lombok.Data;

@Data
public class OrdersDto {
    private String exchangeSegment;
    private int exchangeInstrumentId;
    private String orderType;
    private String orderSide;
    private String timeInForce;
    private int disclosedQuantity;
    private int orderQuantity;
    private double limitPrice;
    private double stopPrice;
    private String orderUniqueIdentifier;
    private String productType;
}
