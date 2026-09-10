package com.quantlab.common.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.List;

@Entity
@Table(name = "strategy" ,uniqueConstraints = {
        @UniqueConstraint(columnNames = {"user_id", "name"})
})
@Getter
@Setter
@NoArgsConstructor
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
public class Strategy extends AuditingEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE,generator = "strategies_seq")
    @SequenceGenerator(name = "strategies_seq",initialValue = 1000,allocationSize = 1)
    @Column(name = "id", updatable = false)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "sc_id", nullable = false)
    private StrategyCategory strategyCategory;

    // legs
    @OneToMany(fetch = FetchType.LAZY, mappedBy = "strategy", cascade = CascadeType.ALL )
    @JsonIgnore
    private List<StrategyLeg> strategyLeg;

//    @JoinColumn(name = "signal_id",  nullable = true)
    @OneToMany(fetch = FetchType.LAZY, mappedBy = "strategy")
    private List<Signal> signals;

    // to use for conditions
    // EntryDetails type
    @OneToOne(mappedBy = "strategy", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private EntryDetails entryDetails;

    @OneToOne(mappedBy = "strategy", cascade = CascadeType.ALL, fetch = FetchType.LAZY, optional = true)
    private StrategyAdditions strategyAdditions;

    @OneToOne(mappedBy = "strategy", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private ExitDetails exitDetails;

    // name of the strategy
    @Column(name = "name", nullable = false)
    private String name;

    // description of the strategy
    @Column(name = "description")
    private String description;

    // type refers to diy or in house and prebuild
    @Column(name = "type_of_strategy")
    private String typeOfStrategy;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "underlying_id",  nullable = false)
    private Underlying underlying;

    // refers
    @Column(name = "underling_type")
    private String underlingType;

    // refers positions is intraday or positional
    @Column(name = "s_position_type")
    private String positionType;

    // statues of the strategy
    @Column(name = "status")
    private String status;

    // referees in diy form which atm type it is
    @Column(name = "atm_type")
    private String atmType;

    // refers that such that it is current week or next weed aor this month etc ...
    @Column(name = "expiry", nullable = false)
    private String expiry;

// tg refers to identify it is diy or delta nutral are etc .... identify the strategy by simple tag
    @Column(name = "strategy_tag" , nullable = false)
    private String strategyTag;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "admin_id")
    @JsonIgnore
    private UserAdmin userAdmin;

    // current week or next week
    @Column(name = "expiry_type" , nullable = true)
    private String expiryType;

    @Column(name = "category")
    private String category;

    // miniman capital required to execute the strategy
    @Column(name = "min_capital")
    private Long minCapital;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "user_id", nullable = true)
    @JsonIgnore
    private AppUser appUser;

    // multiplier is witch is used to multi ply and send to order engine
    @Column(name = "multiplier", nullable = false)
    private Long multiplier;

    // diy form values such that it is stopLoss final value
    @Column(name = "stop_loss", nullable = true)
    private Long stopLoss;

    // diy form values such that it is stopLoss final value
    @Column(name = "target", nullable = true)
    private Long target;

    //
    @Column(name = "execution_type")
    private String executionType;

    // whether strategy has to stop  executing  or not related to pause all
    @Column(name = "hold_type")
    private String holdType;

    // deploy page field such that each strategy can execute how many times sequentially one after another
    @Column(name = "re_signal_count")
    private Integer reSignalCount;

    @Column(name = "signal_count")
    private Integer signalCount;

    @Column(name = "draw_down")
    private Long drawDown;

    @Column(name = "strike_date", nullable = true)
    private LocalDateTime strikeDate;

    @Column(name = "trailing_stoploss")
    private Long trailingStopLoss;

    @Column(name = "original_template")
    private String isTemplate;

    @Column(name = "algo_id")
    private String algoId;

    @Column(name = "algo_category")
    private String algoCategory;

    @Column(name = "strategy_source_id")
    private Long sourceId;

    @Column(name = "trailing_stoploss_type")
    private String trailingStopLossType;

    @Column(name = "last_deployed_on")
    private String lastDeployedOn;

    @Column(name = "subscription")
    private String subscription;

    @Column(name = "manual_exit_type")
    private String manualExitType;

    @Column(name = "is_hidden")
    private Boolean isHidden = false;

    @Column(name = "approval_status")
    private String approvalStatus;

    @Column(name = "nff_id")
    private String NFFID;

    @Column(name = "remarks")
    private String remarks;

    @Column(name = "todays_pnl")
    private Long todayPNL;

    @Column(name = "total_pnl")
    private Long totalPNL;

}
