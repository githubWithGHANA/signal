package com.quantlab.common.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "userrole")
@Getter
@Setter
public class UserRole extends AuditingEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE,generator = "user_roles_seq")
    @SequenceGenerator(name = "user_roles_seq",initialValue = 1000,allocationSize = 1)
    @Column(name = "id", updatable = false)
    private Long id;

    @Column(name = "role_name", nullable = false)
    private String roleName;

    @Column(name = "role_desc", nullable = false)
    private String roleDesc;

    @Column(name = "status", nullable = false)
    private String status;

}
