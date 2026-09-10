package com.quantlab.common.repository;

import com.quantlab.common.entity.DailyStatisticsEntity;
import com.quantlab.common.entity.Signal;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;

@Repository
public interface DailyStatisticsRepository extends JpaRepository<DailyStatisticsEntity, Long> {

    DailyStatisticsEntity findByStrategyId(Long strategyId);
}
