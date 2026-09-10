package com.quantlab.common.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

@Setter
@Getter
@Entity
@Table(name = "order")
public class Order extends AuditingEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "orders_seq")
    @SequenceGenerator(name = "orders_seq",initialValue = 1000,allocationSize = 1)
    @Column(name = "id")
    private Long id;

    @Column(name = "exchange_order_id")
    private String exchangeOrderId;

    @ManyToOne
    @JoinColumn(name = "user_id", nullable = false)
    private UserAdmin userAdmin;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "app_user_id", nullable = false)
    private AppUser appUser;

    @Column(name = "execution_type", nullable = false)
    private String executionType;

    // status of the order pending or completed or executed
    @Column(name = "status", nullable = false)
    private String status;

    // need to discuss
    @Column(name = "deployed_on", nullable = false)
    private Instant deployedOn;

    // total qunatity of the order
    @Column(name = "quantity", nullable = false)
    private Long quantity;

    // price of the instrument executed
    @Column(name = "price", nullable = false)
    private Long price;

    // exchange iD
    @Column(name = "exchange_instrument_id", nullable = false)
    private Long exchangeInstrumentId;

    @Column(name = "exchange_segment", nullable = false)
    private String exchangeSegment;

    // underling wether it is Nifty or bank nifty
    @Column(name = "underlying", nullable = false)
    private String underlying;

    // name of the instrument like display name or name  in touchline
    @Column(name = "instrument_name")
    private String instrumentName;

    @Column(name = "leg_exp_id")
    private Long legLineExpiry;

    @ManyToOne
    @JoinColumn(name = "signal_id", nullable = false)
    private Signal signal;

    @ManyToOne
    @JoinColumn(name = "strategy_id", nullable = false)
    private Strategy strategy;

    // New columns from gRPC object
    @Column(name = "login_id")
    private String loginID;

    @Column(name = "app_order_id")
    private String appOrderID;

    @Column(name = "reference_id")
    private String orderReferenceID;

    @Column(name = "generated_by")
    private String generatedBy;

    @Column(name = "category_type")
    private String orderCategoryType;

    @Column(name = "order_side")
    private String orderSide;

    @Column(name = "order_type")
    private String orderType;

    @Column(name = "product_type")
    private String productType;

    @Column(name = "time_in_force")
    private String timeInForce;

    @Column(name = "stop_price")
    private Long orderStopPrice;

    @Column(name = "average_traded_price")
    private String averageTradedPrice;

    @Column(name = "leaves_quantity")
    private Long leavesQuantity;

    @Column(name = "cumulative_quantity")
    private Long cumulativeQuantity;

    @Column(name = "disclosed_quantity")
    private Long disclosed_uantity;

    @Column(name = "generated_time")
    private String generatedDateTime;

    @Column(name = "exchange_transact_time")
    private String exchangeTransactTime;

    @Column(name = "last_update_time")
    private String lastUpdateTime;

    @Column(name = "expiry_date")
    private String expiryDate;

    @Column(name = "cancel_reject_reason")
    private String cancelRejectReason;

    @Column(name = "unique_identifier")
    private String orderUniqueIdentifier;

    @Column(name = "leg_status")
    private String legStatus;

    @Column(name = "message_code")
    private Long messageCode;

    @Column(name = "message_version")
    private String messageVersion;

    @Column(name = "token_id")
    private String tokenID;

    @Column(name = "application_type")
    private Long applicationType;

    @Column(name = "unique_key")
    private String uniqueKey;

    @Column(name = "source_type")
    private String sourceType;


}
