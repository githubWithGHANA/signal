package com.quantlab.common.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;


@Getter
@Setter
@Entity
@Table(name = "leg_line_expiry")
public class LegLineExpiry extends AuditingEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "leg_line_expiry_seq")
    @SequenceGenerator(name = "leg_line_expiry_seq",initialValue = 1000,allocationSize = 1)
    @Column(name = "id")
    private Long id;

    @Column(name = "leg_exp_name", nullable = false)
    private String legExpName;

    @Column(name = "leg_line_expiry_date", nullable = false)
    private String legLIneExpiryDate;

}
