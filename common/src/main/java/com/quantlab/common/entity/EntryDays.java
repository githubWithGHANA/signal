package com.quantlab.common.entity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "entry_days")
@Getter
@Setter
public class EntryDays extends AuditingEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "entry_days_seq")
    @SequenceGenerator(name = "entry_days_seq",initialValue = 1000,allocationSize = 1)
    @Column(name = "id", updatable = false)
    private Long id;

    // has to discuss has to save according to enum
    @Column(name = "day",  nullable = false)
    private String day;

    @ManyToOne
    @JoinColumn(name = "entry_details_id", nullable = false)
    private EntryDetails entryDetails;
}
