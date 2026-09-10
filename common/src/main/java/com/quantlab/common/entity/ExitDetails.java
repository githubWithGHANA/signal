package com.quantlab.common.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.time.Instant;

@Entity
@Table(name = "exit_details")
@Getter
@Setter
public class ExitDetails extends AuditingEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "exit_details_seq")
    @SequenceGenerator(name = "exit_details_seq",initialValue = 1000,allocationSize = 1)
    @Column(name = "id",  updatable = false)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "strategy_id")
    private Strategy strategy;

    // UI value for the profit that total profit of the strategy exeguted in one time it mtm Type
    @Column(name = "profit_mtm_unit_type",nullable = true)
    private String profitMtmUnitType;
    // UI value for the profit that total profit of the strategy in one time
    @Column(name = "profit_mtm_unit_value",nullable = true)
    private Long profitMtmUnitValue;

    // UI value total Stop loss of the strategy in the each time signal is exeguted for that it is an Unit
    @Column(name = "stoploss_mtm_unit_value")
    private Long stoplossMtmUnitValue;

    // UI value total stop loss of the strategy that each time signal is exeguted
    @Column(name = "exit_on_expiry_flag")
    private String exitOnExpiryFlag;

    // UI value for the maxmimam no of days
    @Column(name = "exit_after_entry_days")
    private Integer exitAfterEntryDays;

    @Column(name = "exit-time")
    private Instant exitTime;

    @Column(name = "exit_hour_time")
    private Integer exitHourTime;

    @Column(name = "exit_mins_time")
    private Integer exitMinsTime;

    @Column(name = "exit_sec_time")
    private Integer exitSecTime;

    @Column(name = "target_unit_toggle",nullable = true)
    private String targetUnitToggle;

    @Column(name = "target_unit_type",nullable = true)
    private String targetUnitType;

    @Column(name = "target_unit_value",nullable = true)
    private Long targetUnitValue;

    @Column(name = "stop_loss_unit_type",nullable = true)
    private String stopLossUnitType;

    @Column(name = "stop_loss_unit_toggle",nullable = true)
    private String stopLossUnitToggle;

    @Column(name = "stop_loss_unit_value",nullable = true)
    private Long stopLossUnitValue;

    @Column(name = "status", nullable = false)
    private String status;

    @Column(name = "premium_capital",nullable = true)
    private Long premiumCapital;

    @Column(name = "exit_strategy_date")
    private Instant exitStrategyDate;

}
