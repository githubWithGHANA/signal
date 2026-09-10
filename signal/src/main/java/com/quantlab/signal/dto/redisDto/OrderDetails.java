package com.quantlab.signal.dto.redisDto;

import lombok.Data;

@Data
public class OrderDetails {
    private String SignalID;
    private String TenantID;
    private String LoginID;
    private String ClientID;
    private Number AppOrderID;
    private String OrderReferenceID;
    private String GeneratedBy;
    private String ExchangeOrderID;
    private String OrderCategoryType;
    private String ExchangeSegment;
    private Number ExchangeInstrumentID;
    private String OrderSide;
    private String OrderType;
    private String ProductType;
    private String TimeInForce;
    private Number OrderPrice;
    private Number OrderQuantity;
    private Number OrderLimitPrice;
    private Number OrderStopPrice;
    private String OrderStatus;
    private String OrderAverageTradedPrice;
    private Number LeavesQuantity;
    private Number CumulativeQuantity;
    private Number OrderDisclosedQuantity;
    private String OrderGeneratedDateTime;
    private String ExchangeTransactTime;
    private String LastUpdateDateTime;
    private String OrderExpiryDate;
    private String CancelRejectReason;
    private String OrderUniqueIdentifier;
    private String OrderLegStatus;
    private Number MessageCode;
    private Number MessageVersion;
    private Number TokenID;
    private Number ApplicationType;
    private Number RetryCount;
    private String SellOrdersStatus;
    private String PlaceOrderStatus;
}
