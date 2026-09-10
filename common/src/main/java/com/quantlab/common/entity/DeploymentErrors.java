package com.quantlab.common.entity;


import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.List;

@Entity
@Table(name = "deployment_errors")
@Getter
@Setter
public class DeploymentErrors extends AuditingEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "deployment_errors_seq")
    @SequenceGenerator(name = "deployment_errors_seq",initialValue = 1000,allocationSize = 1)
    @Column(name = "id", updatable = false)
    private Long id;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "user_id",  nullable = false)
    private AppUser appUser;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "strategy_id",  nullable = false)
    private Strategy strategy;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "strategy_leg_id")
    private StrategyLeg strategyLeg;

    @Column(name = "status")
    private String status;

    @Column(name = "description")
    private List<String> description;

    @Column(name = "deployed_on")
    private Instant deployedOn;

    @Column(name = "error_code")
    private String errorCode;
}
