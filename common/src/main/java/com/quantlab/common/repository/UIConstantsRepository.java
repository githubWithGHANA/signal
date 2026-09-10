package com.quantlab.common.repository;

import com.quantlab.common.entity.StrategyLeg;
import com.quantlab.common.entity.UIConstants;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UIConstantsRepository extends JpaRepository<UIConstants, Long> {

    UIConstants findByCode(Long code);
}
