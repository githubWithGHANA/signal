package com.quantlab.common.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name= "entity_days_mapping")
public class EntityDaysMapping  extends AuditingEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "entity_days_mapping_seq")
    @SequenceGenerator(name = "entity_days_mapping_seq",initialValue = 1000,allocationSize = 1)
    @Column(name = "id", updatable = false, nullable = false)
    private Long id;

    // need to discuss
    @ManyToOne
    @JoinColumn(name = "sc_id", nullable = false)
    private StrategyCategory strategyCategory;

    // status of the entry
    @Column(name = "status",nullable = false)
    private String status;

}
