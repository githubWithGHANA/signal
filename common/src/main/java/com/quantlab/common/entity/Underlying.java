package com.quantlab.common.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "underlying")
@Getter
@Setter
public class Underlying extends AuditingEntity{

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE,generator = "underlying_seq")
    @SequenceGenerator(name = "underlying_seq",initialValue = 1000,allocationSize = 1)
    @Column(name = "id", updatable = false)
    private Long id;

    @Column(name = "u_name", nullable = false)
    private String name;

    @Column(name = "u_id", nullable = false)
    private String underlyingId;

    @Column(name = "status", nullable = false)
    private String status;

}
