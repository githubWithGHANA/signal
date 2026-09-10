package com.quantlab.common.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

@Entity
@Table(name = "token_log_info")
@Getter
@Setter
public class TokenLogInfo extends AuditingEntity{

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE,generator = "token_log_info_seq")
    @SequenceGenerator(name = "token_log_info_seq",initialValue = 1000,allocationSize = 1)
    @Column(name = "id", updatable = false)
    private Long id;

    @Column(name= "machine_id")
    private String machineId;

    @Column(name= "user_agent")
    private String  userAgent;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private AppUser appUser;

    @Column(name = "trade_token")
    private String tradeToken;

    // xts or tr
    @Column(name = "token_type")
    private String tokenType;

    @Column(name = "token_generated_time")
    private Instant tokenGeneratedTime;

    @Column(name = "welcome_acknowledged_time")
    private Instant welcomeAcknowledgedTime;

    // whether welcome accepted or declined
    @Column(name = "acknowledgement_type")
    private String acknowledgementType;

    @Column(name = "remarks")
    private String remarks;

}
