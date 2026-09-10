package com.quantlab.common.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "ui_constants")
public class UIConstants extends AuditingEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "pk_ui_constants")
    @SequenceGenerator(name = "pk_ui_constants", sequenceName = "ui_constants_seq", initialValue = 1000, allocationSize = 1)
    @Column(name = "id", updatable = false)
    private Long id;


    @Column(name = "field_name")
    private String fieldName;

    @Column(name = "description")
    private String description;

    @Column(name = "code")
    private Long code;


}
