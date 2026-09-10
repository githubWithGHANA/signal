package com.quantlab.common.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "entry_details")
@Getter
@Setter
public class EntryDetails extends AuditingEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE,generator = "entry_details_seq")
    @SequenceGenerator(name = "entry_details_seq",initialValue = 1000,allocationSize = 1)
    @Column(name = "id", nullable = false, updatable = false)
    private Long id;

    @ManyToMany(fetch = FetchType.EAGER, cascade = {CascadeType.PERSIST, CascadeType.MERGE})
    @JoinTable(
            name = "entry_details_entry_days",
            joinColumns = @JoinColumn(name = "entry_details_id"),
            inverseJoinColumns = @JoinColumn(name = "entry_days_id")
    )    private List<EntryDays> entryDays;


    // entry time in hours
    @Column(name = "entry_hour_time")
    private Integer entryHourTime;

    // entry time in minutes
    @Column(name = "entry_mins_time")
    private Integer entryMinsTime;

    // entry time in seconds
    @Column(name = "entry_sec_time")
    private Integer entrySecTime;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "strategy_id")
    private Strategy strategy;

    // detailed entry time of the table including hour and minute and seconds
    @Column(name = "entry_time")
    @Temporal(TemporalType.TIMESTAMP)
    private Instant entryTime;

    // default value can be null ,  has to save from enum
    @Column(name = "status", nullable = true)
    private String status;

}
