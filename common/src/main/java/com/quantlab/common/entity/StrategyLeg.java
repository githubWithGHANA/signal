package com.quantlab.common.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

@Getter
@Setter
@Entity
@Table(name = "strategy_leg")
public class StrategyLeg extends AuditingEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "strategies_legs_seq")
    @SequenceGenerator(name = "strategies_legs_seq", sequenceName = "strategies_legs_seq", initialValue = 1000,allocationSize = 1)
    @Column(name = "id", updatable = false)
    private Long id;


    @ManyToOne(fetch = FetchType.LAZY, cascade = CascadeType.MERGE)
    @JoinColumn(name = "strategy_id")
    private Strategy strategy;

    // name of the instrument when created Legs
    @Column(name = "name")
    private String name;

    // it is nsefo or nse equity
    @Column(name = "segment",nullable = false)
    private String segment;

    // its is buy leg or sell leg
    @Column(name = "buy_sell_flag",nullable = false)
    private String buySellFlag;

    // strike type in diy (exc:- ATM or OTM or ITM)
    @Column(name = "skt_type",nullable = true)
    private String sktType;

    // strike selection in diy form
    @Column(name = "skt_selection",nullable = true)
    private String sktSelection;

    // strike selection value from diy form
    @Column(name = "skt_selection_value",nullable = true)
    private String sktSelectionValue;

    // has to discuss
    @Column(name = "leg_exp_name")
    private String legExpName;

    @Column(name = "leg_line_expiry_date")
    private Instant legLineExpiryDate;

    // no of lots
    @Column(name = "no_of_lots",nullable = false)
    private Long noOfLots;

    @Column(name = "target_unit_toggle",nullable = true)
    private String targetUnitToggle;

    // target Unit weather it is percent or amount
    @Column(name = "target_unit_Type",nullable = true)
    private String targetUnitType;

    // target value
    @Column(name = "target_unit_value",nullable = true)
    private Long targetUnitValue;

    @Column(name = "target_final_value")
    private Long targetFinalValue;

    @Column(name = "stoploss_unit_toggle",nullable = true)
    private String stopLossUnitToggle;

    // stop loss unit weather it is percent ot amount
    @Column(name = "stoploss_unit_type",nullable = true)
    private String stopLossUnitType;

    // stop loss values
    @Column(name = "stoploss_unit_value",nullable = true)
    private Long stopLossUnitValue;

    @Column(name = "stoploss_final_value")
    private Long stopLossFinalValue;

    // signal which is mapped if the signal is created
    @ManyToOne(fetch = FetchType.LAZY ,cascade = CascadeType.ALL)
    @JoinColumn(name = "signal_id" , referencedColumnName = "id"  , nullable = true)
    private Signal signal;

    // status of the leg
    @Column(name = "status" ,nullable = false)
    private String status;

    // price which system is generated
    @Column(name = "price" )
    private Long price;

    // price which nse system is generated
    @Column(name = "executed_price" )
    private Long executedPrice;

    // instrumnet id which we can trac the leg in exchange
    @Column(name = "exchange_instrument_id")
    private Long exchangeInstrumentId;

    // admin
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "admin_id")
    private UserAdmin userAdmin;

    // user
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private AppUser appUser;

    // type of the option weather it is call option or put option
    @Column(name = "option_type")
    private String optionType;

    // states that weather it is open leg or exit leg
    @Column(name = "leg_type")
    private String legType;

    //  quantity which we need to keep on track
    @Column(name = "quantity",nullable = false)
    private Long quantity;

    @Column(name = "closing_price")
    private Long closingPrice;

    // we have to update the quantity which come from position socket need to update the latest quantity for manual handling
    @Column(name = "last_updated_quantity",nullable = false)
    private Long latestUpdatedQuantity;

    // multi order flag weather it is multiple orders or single order it is punching which  Y or N
    @Column(name = "multi_orders_flag",nullable = false)
    private String multiOrdersFlag;

    @Column(name = "traded_price")
    private Long tradedPrice;

    @Column(name = "lot_size")
    private Long lotSize;

    @Column(name = "profit_loss")
    private Long profitLoss;

    // trailing stop loss points
    @Column(name = "trailing_stoploss_points")
    private Long trailingStopLossPoints;

    // trailing distance given by the  user input
    @Column(name = "trailing_distance")
    private Long trailingDistance;

    // trailing trade point
    @Column(name ="trailing_trade_point")
    private Long trailingTradePoint;

    // trailing type it is y or n accordingly to trigger trailing
    @Column(name = "trailing_stoploss_toggle")
    private String trailingStopLossToggle;

    @Column(name = "trailing_stoploss_type")
    private String trailingStopLossType;

    @Column(name = "trailing_stoploss_value")
    private Long trailingStopLossValue;

    @Column(name = "trailing_stoploss_mtm_value")
    private Long trailingStopLossMtmValue;

    @Column(name = "ltp")
    private Long ltp;

    @Column(name = "mtm")
    private Long mtm;

    @Column(name = "current_delta")
    private Long currentDelta;

    @Column(name = "current_iv")
    private Long currentIV;

    @Column(name = "constant_delta")
    private Long constantDelta;

    @Column(name = "constant_iv")
    private Long constantIV;

    @Column(name = "value")
    private Long value;

    @Column(name = "filled_quantity")
    private Long filledQuantity;

    @Column(name = "position_quantity")
    private Long lastPositionQuantity;

    @Column(name = "leg_identifier")
    private String legIdentifier;

    // status of the leg when created it is exchange-open and exchange-close
    @Column(name = "exchange_status")
    private String exchangeStatus;

    // derivative type is type of derivative future or option
    @Column(name = "leg_derivative_type",nullable = false)
    private String derivativeType;

    @Column(name = "base_index_price")
    private Long baseIndexPrice;

    @Column(name = "latest_index_price")
    private Long latestIndexPrice;

    @Column(name = "tsl_anchor_price")
    private Long tslAnchorPrice;

    @Column(name = "tsl_activated")
    private Boolean tslActivated;

    @Column(name = "executed_time")
    private Instant executedTime;

    // Leg-level strategy-specific metadata (target role, timeframe, etc.).
    // Stored in its own table so future strategies can add per-leg fields without
    // bloating this entity. See StrategyLegAdditions.
    @OneToOne(fetch = FetchType.LAZY, cascade = CascadeType.ALL)
    @JoinColumn(name = "leg_additions_id", nullable = true)
    private StrategyLegAdditions legAdditions;


    public void setFields(StrategyLeg source) {
        this.name = source.getName();
        this.segment = source.getSegment();
        this.buySellFlag = source.getBuySellFlag();
        this.sktType = source.getSktType();
        this.sktSelection = source.getSktSelection();
        this.legExpName = source.getLegExpName();
        this.legLineExpiryDate = source.getLegLineExpiryDate();
        this.noOfLots = source.getNoOfLots();
        this.targetUnitToggle = source.getTargetUnitToggle();
        this.targetUnitType = source.getTargetUnitType();
        this.targetUnitValue = source.getTargetUnitValue();
        this.stopLossUnitToggle = source.getStopLossUnitToggle();
        this.stopLossUnitType = source.getStopLossUnitType();
        this.stopLossUnitValue = source.getStopLossUnitValue();
        this.signal = source.getSignal();
        this.status = source.getStatus();
        this.price = source.getPrice();
        this.executedPrice = source.getExecutedPrice();
        this.exchangeInstrumentId = source.getExchangeInstrumentId();
        this.userAdmin = source.getUserAdmin();
        this.appUser = source.getAppUser();
        this.optionType = source.getOptionType();
        this.legType = source.getLegType();
        this.quantity = source.getQuantity();
        this.closingPrice = source.getClosingPrice();
        this.latestUpdatedQuantity = source.getLatestUpdatedQuantity();
        this.multiOrdersFlag = source.getMultiOrdersFlag();
        this.tradedPrice = source.getTradedPrice();
        this.lotSize = source.getLotSize();
        this.profitLoss = source.getProfitLoss();
        this.trailingStopLossPoints = source.getTrailingStopLossPoints();
        this.trailingDistance = source.getTrailingDistance();
        this.trailingTradePoint = source.getTrailingTradePoint();
        this.trailingStopLossToggle = source.getTrailingStopLossToggle();
        this.ltp = source.getLtp();
        this.mtm = source.getMtm();
        this.currentDelta = source.getCurrentDelta();
        this.currentIV = source.getCurrentIV();
        this.constantDelta = source.getConstantDelta();
        this.constantIV = source.getConstantIV();
        this.value = source.getValue();
        this.exchangeStatus = source.getExchangeStatus();
        this.derivativeType = source.getDerivativeType();
        // Skip setting the id
    }

}
