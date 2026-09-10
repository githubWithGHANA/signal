package com.quantlab.common.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

@Entity
@Table(name = "user_admin")
@Getter
@Setter
public class UserAdmin extends AuditingEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE,generator = "user_admins_seq")
    @SequenceGenerator(name = "user_admins_seq",initialValue = 1000,allocationSize = 1)
    @Column(name = "id", updatable = false)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "admin_id", referencedColumnName = "id", nullable = true)
    private UserAdmin userAdmin;

    @Column(name = "admin_name")
    private String adminName;

    @Column(name = "first_name")
    private String firstName;

    @Column(name = "middle_name")
    private String middleName;

    @Column(name = "last_name")
    private String lastName;

    @Column(name = "role_name")
    private String roleName;

    @Column(name = "admin_level")
    private String adminLevel;

    @Column(name = "email", nullable = false, unique = true)
    private String email;

    @Column(name = "password")
    private String password;

    @Column(name = "phone_number")
    private String phoneNumber;

    @Column(name = "otp")
    private String otp;

    @Column(name = "otp_expiry")
    private Instant otpExpiry;

    @Column(name = "otp_status")
    private String otpStatus;

    @Column(name = "status")
    private String status;

}
