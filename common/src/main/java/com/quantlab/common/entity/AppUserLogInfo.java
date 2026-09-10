package com.quantlab.common.entity;


import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

@Entity
@Table(name = "app_user_log_info")
@Getter
@Setter
public class AppUserLogInfo extends AuditingEntity{

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE,generator = "app_user_log_info_seq")
    @SequenceGenerator(name = "app_user_log_info_seq",initialValue = 1000,allocationSize = 1)
    @Column(name = "id", updatable = false)
    private Long id;

    @Column(name= "login_time")
    private Instant loggedinTime;

    @Column(name= "mechine_id")
    private String mechineId;

    @Column(name= "user_agent")
    private String  userAgent;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private AppUser appUser;

    @Column(name = "xts_token")
    private String xtsToken;

    @Column(name = "token_generated_time")
    private Instant tokenGeneratedTime;

}
