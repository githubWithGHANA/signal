package com.quantlab.common.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;


@Entity
@Table(name = "app_user")
@Getter
@Setter
public class AppUser extends AuditingEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE ,generator = "app_user_seq")
    @SequenceGenerator(name = "app_user_seq",initialValue = 1000,allocationSize = 1)
    @Column(name = "id")
    private Long id;

    @Column(name = "user_id", nullable = false, unique = true)
    private String userId;

    @Column(name = "token_id",nullable = true)
    private String tokenId;

    @Column(name = "user_name",nullable = false)
    private String userName;


    @Column(name = "user_account_id",nullable = true)
    private String userAccountId;

    @ManyToOne(cascade = CascadeType.ALL, fetch = FetchType.EAGER)
    @JoinColumn(name = "user_role" , nullable = false, unique = false)
    private UserRole userRole;

    @Column(name = "tenent_id", nullable = true)
    private String tenentId;

    @ManyToOne
    @JoinColumn(name = "admin_id" , nullable = false)
    private UserAdmin admin;

    @Column(name = "status", nullable = false)
    private String status;

    // all finance related things area stored in the Multiples of the 1000 when we want to use can dived by 1000
    @Column(name = "investment", nullable = true)
    private Long investment;

    @Column(name = "current_value", nullable = true)
    private Long currentValue;

    @Column(name = "today_profit_loss", nullable = true)
    private Long todayProfitLoss;

    @Column(name = "overall_profit_loss", nullable = true)
    private Long overallProfitLoss;

    @Column(name = "app_user_id", nullable = true)
    private Long appUserId;

    // has to change
//    @PrePersist
//    private void setUserId() {
//        this.userId = "user" + this.id;
//    }

    @PrePersist
    private void setAppUserId() {
        this.appUserId = this.id;
        this.userId = "user" + this.id;
    }

}
