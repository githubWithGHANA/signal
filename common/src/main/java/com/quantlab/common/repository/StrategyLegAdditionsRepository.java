package com.quantlab.common.repository;

import com.quantlab.common.entity.StrategyLegAdditions;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface StrategyLegAdditionsRepository extends JpaRepository<StrategyLegAdditions, Long> {
}