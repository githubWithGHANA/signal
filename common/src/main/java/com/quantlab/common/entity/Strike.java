package com.quantlab.common.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "strike")
public class Strike extends AuditingEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE,generator = "strikes_seq")
    @SequenceGenerator(name = "strikes_seq",initialValue = 1000,allocationSize = 1)
    @Column(name = "id",  updatable = false)
    private Long id;

    @Column(name = "stk_name", nullable = false)
    private String stkName;

    @Column(name = "status", nullable = false)
    private String status;

}
