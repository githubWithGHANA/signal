package com.quantlab.common.entity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "instrument_details")
public class InstrumentDetails extends AuditingEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE,generator = "instrument_details_seq")
    @SequenceGenerator(name = "instrument_details_seq",initialValue = 1000,allocationSize = 1)
    @Column(name = "id", updatable = false)
    private Long id;

    @Column(name = "exchange_segment_id", nullable = false)
    private int exchangeSegmentId;

    @Column(name = "index", nullable = false)
    private String index;

    @Column(name = "exchange_instrument_id", nullable = false)
    private Long exchangeInstrumentId;

    @Column(name = "expiry_date", nullable = false)
    private String expiryDate;

}
