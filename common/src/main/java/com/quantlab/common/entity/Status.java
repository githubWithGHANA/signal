package com.quantlab.common.entity;

import jakarta.persistence.*;

@Entity
@Table(name = "status")
public class Status extends AuditingEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE,generator = "status_seq")
    @SequenceGenerator(name = "status_seq",initialValue = 1000,allocationSize = 1)
    @Column(name = "id", updatable = false)
    private Long id;

    @Column(name = "name", nullable = false)
    private String name;

}
