package com.quantlab.common.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Entity
@Getter
@Setter
@Table(name = "strategy_addition")
public class StrategyAdditions extends AuditingEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE,generator = "strategy_addition_seq")
    @SequenceGenerator(name = "strategy_addition_seq",initialValue = 1000,allocationSize = 1)
    @Column(name = "id", updatable = false)
    private Long id;

    @OneToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "strategy_id", nullable = false)
    private Strategy strategy;

    // deploy form it is used for only some strategy
    @Column(name = "delta_slippage")
    private Long deltaSlippage;

    @Column(name = "waiting_for_next_crossover")
    private Boolean waitingForNextCrossover = true;

    @Column(name = "last_traded_crossover_candle_timestamp")
    private Long lastTradedCrossoverCandleTimestamp;

    @Column(name = "last_exit_candle_timestamp")
    private Long lastExitCandleTimestamp;

    @Column(name = "last_accumulation_1h_bar_time")
    private Long lastAccumulation1hBarTime;

    @Column(name = "last_accumulation_15m_bar_time")
    private Long lastAccumulation15mBarTime;

    @Column(name = "last_accumulation_5m_bar_time")
    private Long lastAccumulation5mBarTime;

    @Column(name = "brr_high")
    private Double brrHigh;

    @Column(name = "brr_low")
    private Double brrLow;

    @Column(name = "brr_resistance")
    private Double brrResistance;

    @Column(name = "brr_support")
    private Double brrSupport;

    @Column(name = "brr_stop_loss")
    private Double brrStopLoss;

    @Column(name = "brr_phase")
    private String brrPhase;

    @Column(name = "brr_last_candle_time")
    private Long brrLastCandleTime;

    @Column(name = "brr_exit_buffer")
    private Double brrExitBuffer;

    @Column(name = "brr_direction")
    private String brrDirection;

}
