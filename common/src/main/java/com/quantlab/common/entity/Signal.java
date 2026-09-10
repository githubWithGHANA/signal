package com.quantlab.common.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.util.List;


@Entity
@Table(name = "signal")
@Getter
@Setter
public class Signal extends AuditingEntity {


    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE,generator = "signals_seq")
    @SequenceGenerator(name = "signals_seq",initialValue = 1000,allocationSize = 1)
    @Column(name = "id",updatable = false)
    private Long id;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "user_id",  nullable = false)
    private AppUser appUser;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "strategy_id",  nullable = false)
    private Strategy strategy;

    @OneToMany(fetch = FetchType.LAZY, cascade = CascadeType.ALL , orphanRemoval = true)
    @JoinColumn(name = "signal_id",referencedColumnName = "id" ,nullable = true)
    private List<StrategyLeg> strategyLeg;

    // signal meta data like greeks and etc .....
    @OneToOne(fetch = FetchType.EAGER, cascade = CascadeType.ALL)
    @JoinColumn(name = "strategy_addition_id",  nullable = true)
    private SignalAdditions signalAdditions;

    // it is paper or live comes from enum
    @Column(name = "execution_type", nullable = false)
    private String executionType;

    // status of the signal has to update the every Morning (BOD)
    @Column(name = "status", nullable = false)
    private String status;

    // which has field in UI
    @Column(name = "deployed_on")
    private String deployedOn;

    // ui Field that how meany No of lots that need to send when generating  leg has to come from strategy
    @Column(name = "multiplier", nullable = false)
    private Long multiplier;

    // need to discuss
    @Column(name = "capital", nullable = false)
    private Long capital;

    @Column(name = "profit_loss")
    private Long profitLoss;

    // legs
    @OneToMany(fetch = FetchType.EAGER, mappedBy = "signal", cascade = CascadeType.ALL )
    @JsonIgnore
    private List<StrategyLeg> signalLegs;

    @Column(name = "position_type")
    private String positionType;

    @Column(name = "base_index_price")
    private Long baseIndexPrice;

    @Column(name = "latest_index_price")
    private Long latestIndexPrice;

    @Column(name = "last_pnl")
    private String lastPNL;

//    @ManyToOne(fetch = FetchType.EAGER)
//    @JoinColumn(name = "underlying_id",  nullable = false)
//    private Underlying underlying;

}
