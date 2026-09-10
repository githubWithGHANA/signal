package com.quantlab.signal.dto;

import lombok.Data;

@Data
public class XtsOrdersDto {
    private String exchangeSegment;
    private int exchangeInstrumentId;
    private String orderType;
    private String clientID;
    private String userID;
    private String orderSide;
    // has to know
    private String timeInForce;
    private int disclosedQuantity;
    private int orderQuantity;
    private double limitPrice;
    private double stopPrice;
    private String orderUniqueIdentifier;
    private String productType;
    private int noLots;
    private int lotSize;
    private int multiply;
    private String algoID;
    private String algoCategory;
}

