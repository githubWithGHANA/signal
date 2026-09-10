package com.quantlab.common.repository;

import com.quantlab.common.entity.StrategyCategory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface StrategyCategoryRepository extends JpaRepository<StrategyCategory, Long> {
}
