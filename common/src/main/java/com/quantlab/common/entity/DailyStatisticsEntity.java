package com.quantlab.common.entity;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.quantlab.common.dto.StatisticsResponseDto;
import com.vladmihalcea.hibernate.type.json.JsonBinaryType;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.Type;
import org.postgresql.util.PGobject;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Map;


@Entity
@Table(name = "daily_statistics")
@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
public class DailyStatisticsEntity extends AuditingEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "user_id", nullable = false)
    private String userId;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "strategy_id", nullable = false, unique = true)
    private Strategy strategy;

    @Column(name = "date", nullable = false)
    private LocalDate date;

    @Column(name = "total_profit")
    private Long totalProfit;

    @Column(name = "total_roi")
    private Long totalRoi;

    @Column(name = "max_draw_down")
    private Long maxDrawDown;

    @Column(name = "max_draw_down_percent")
    private Long maxDrawDownPercent;

    @Column(name = "win_rate")
    private Long winRate;

    @Column(name = "loss_rate")
    private Long lossRate;

    @Column(name = "capital_required")
    private Long capitalRequired;

    @Column(name = "total_trading_days")
    private Long totalTradingDays;

    @Column(name = "total_trades")
    private Long totalTrades;

    @Column(name = "avg_trades_per_day")
    private Long avgTradesPerDay;

    @Column(name = "avg_daily_profit")
    private Long avgDailyProfit;

    @Column(name = "avg_profit_on_win_days")
    private Long avgProfitOnWinDays;

    @Column(name = "avg_loss_on_loss_days")
    private Long avgLossOnLossDays;

    @Column(columnDefinition = "text")
    private String statsJson; // raw JSON to string

    @Column(name = "last_updated", updatable = false)
    private LocalDateTime lastUpdated = LocalDateTime.now();

    @Column(name = "execution_type")
    private String executionType;

}
