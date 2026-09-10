package com.quantlab.common.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;


@Getter
@Setter
@Entity
@Table(name = "position")
public class Position extends AuditingEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE,generator = "positions_seq")
    @SequenceGenerator(name = "positions_seq",initialValue = 1000,allocationSize = 1)
    @Column(name = "id", nullable = false, updatable = false)
    private Long id;

    @Column(name = "postion_id")
    private String postionId;

    @ManyToOne
    @JoinColumn(name = "user_id", nullable = false)
    private UserAdmin userAdmin;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "app_user_id", nullable = false)
    private AppUser appUser;

    @Column(name = "execution_type",nullable = false)
    private String executionType;

    @Column(name = "deployed_on")
    private Instant deployedOn;

    @Column(name = "quantity")
    private Long quantity;

    @Column(name = "average_price")
    private Long averagePrice;

    @Column(name = "exchange_instrument_id",nullable = false)
    private String exchangeInstrumentId;

    @Column(name = "exchange_segment",nullable = false)
    private String exchangeSegment;

    @Column(name = "underlying")
    private String underlying;

    @Column(name = "instrument_name")
    private String instrumentName;

    @Column(name = "broker_id")
    private String brokerId;

    @Column(name = "total_open_quantity")
    private String totalOpenQuantity;

    @Column(name = "total_mtm")
    private String totalMtm;

    @Column(name = "status")
    private String status;

    @ManyToOne
    @JoinColumn(name = "strategy_id")
    private Strategy strategy;

    // New columns from gRPC object
    @Column(name = "product_type")
    private String productType;

    @Column(name = "long_position")
    private Double longPosition;

    @Column(name = "short_position")
    private Double shortPosition;

    @Column(name = "net_position")
    private Double netPosition;

    @Column(name = "buy_average_price")
    private String buyAveragePrice;

    @Column(name = "sell_average_price")
    private String averageSellPrice;

    @Column(name = "buy_value")
    private String buyValue;

    @Column(name = "sell_value")
    private String sellValue;

    @Column(name = "net_value")
    private String netValue;

    @Column(name = "unrealized_mtm")
    private String unrealizedMTM;

    @Column(name = "realized_mtm")
    private String realizedMTM;

    @Column(name = "mtm")
    private String mtm;

    @Column(name = "bep")
    private String bep;

    @Column(name = "sum_of_traded_quantity_and_price_buy")
    private String sumOfTradedQuantityAndPriceBuy;

    @Column(name = "sum_of_traded_quantity_and_price_sell")
    private String sumOfTradedQuantityAndPriceSell;

    @Column(name = "unique_key")
    private String uniqueKey;

    @Column(name = "message_code")
    private Integer messageCode;

    @Column(name = "message_version")
    private Integer messageVersion;

    @Column(name = "token_id")
    private Integer tokenID;

    @Column(name = "application_type")
    private Integer applicationType;

    @Column(name = "contract_expiration")
    private String contractExpiration;

    @Column(name = "strike_price")
    private Integer strikePrice;

}
