package com.quantlab.signal.dto;

import lombok.Data;

@Data
public class TrOrdersDto {
    public String productAlias;
    public String userId;
    public String accountId;
    public String tradingSymbol;
    public String exchange;
    public String transactionType;
    public String retention;
    public String priceType;
    public int quantity;
    public int disclosedQuantity;
    public String marketProtection;
    public double price;
    public double triggerPrice;
    public String productCode;
    public String dateDays;
    public String afterMarketOrder;
    public String positionSquareOffFlag;
    public int minimumQuantity;
    public int brokerClient;
    public String naicCode;
    public String orderSource;
    public String userTag;
    public String exchangeAlgoId;
    public String exchangeAlgoCategory;
    public String remarks;
    public String criteriaAttribute;
    public String uniqueKey;
    public String channel;
    public String ipAddress;
    public String userAgent;
    public String appInstallId;
    public String auctionNumber;
    public String ctclId;
    public int noLots;
    public int lotSize;
    public int multiply;
    public String segment;
    public String orderUniqueIdentifier;
    public String tokenNo;
}
