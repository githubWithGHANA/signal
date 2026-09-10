package com.quantlab.common.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

/**
 * Leg-level metadata that extends the base StrategyLeg entity.
 * Contains per-leg parameters used by strategies that assign distinct roles
 * to individual legs (e.g. multi-target equity strategies).
 *
 * <p>Persisted as a @OneToOne from StrategyLeg. Each strategy may use a
 * different subset of fields.</p>
 */
@Entity
@Table(name = "strategy_leg_additions")
@Getter
@Setter
public class StrategyLegAdditions extends AuditingEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "strategy_leg_additions_seq")
    @SequenceGenerator(name = "strategy_leg_additions_seq", sequenceName = "strategy_leg_additions_seq",
            initialValue = 1000, allocationSize = 1)
    @Column(name = "id", updatable = false)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "strategy_leg_id")
    private StrategyLeg strategyLeg;

    // ── EMA Exhaustion BB Sell fields ──

    /** Target role for this leg: T1 (mid BB), T2 (lower BB), or TRAIL. */
    @Column(name = "target_type")
    private String targetType;

    /** Candle timeframe used to evaluate exit indicators (e.g. "1H"). */
    @Column(name = "entry_timeframe")
    private String entryTimeframe;
}