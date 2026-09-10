package com.quantlab.common.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "strategy_category")
@Getter
@Setter
public class StrategyCategory extends AuditingEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "strategy_category_seq")
    @SequenceGenerator(name = "strategy_category_seq",initialValue = 1000,allocationSize = 1)
    @Column(name = "id", updatable = false)
    private Long id;

    // name
    @Column(name = "name",nullable = false)
    private String Name;

    // description
    @Column(name = "desc", nullable = false)
    private String Desc;

    @Column(name = "status",nullable = false)
    private String status;

}
