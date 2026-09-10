package com.quantlab.common.entity;


import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

    @Entity
    @Table(name = "user_auth_constants")
    @Getter
    @Setter
    public class UserAuthConstants extends AuditingEntity{

     @Id
     @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "user_auth_constants_seq")
     @SequenceGenerator(name = "user_auth_constants_seq", sequenceName = "user_auth_constants_seq",initialValue = 1000,allocationSize = 1)
     @Column(name = "id", nullable = false, updatable = false)
     private Long id;

     @Column(name = "client_id", unique = true, nullable = false)
     private String clientId;

     @Column(name = "token")
     private String token;

     @Column(name = "otp_session_id")
     private String otpSessionId;

     @Column(name = "app_id")
     private String appId;

     @Column(name = "app_key")
     private String appKey;

     @Column(name ="email_id")
     private String emailId;

     @Column(name ="name")
     private String name;

     @Column(name ="address")
     private String address;

     @Column(name ="xts_client")
     private Boolean xtsClient;

     @Column(name = "mobile_number")
     private String mobileNumber;

     @Column(name = "xts_app_key", length = 1200)
     private String xtsAppKey;

     @Column(name = "xts_secret_key", length = 1200)
     private String xtsSecretKey;

     @Column(name= "previous_login_time")
     private LocalDateTime previousLoggedinTime;

     @ManyToOne()
     @JoinColumn(name = "user_id")
     private AppUser appUser;

     @Column(name = "min_profit")
     private Long minProfit;

     @Column(name = "max_loss")
     private Long maxLoss;

     @Column(name = "user_token_type")
     private String userTokenType;

     @Column(name = "user_trading_mode")
     private String userTradingMode;

     @Column(name ="last_welcome_ack_time")
     private LocalDateTime lastWelcomeAckTime;

     @Column(name = "xts_token")
     private String xtsToken;

     @Column(name = "is_cug_user")
     private Boolean isCugUser;

     @Column(name = "tr_token")
     private String trToken;

     @Column(name = "user_session_id")
     private String userSessionId;

     @Column(name = "jsession_id")
     private String jsessionId;

     @Column(name = "product_alias")
     private String productAlias;

     @Column(name = "broker_name")
     private String brokerName;

     @Column(name = "branch_id")
     private String branchId;
}
