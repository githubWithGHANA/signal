package com.quantlab.common.repository;

import com.quantlab.common.entity.DeploymentErrors;
import com.quantlab.common.entity.Order;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;

public interface DeploymentErrorsRepository extends JpaRepository<DeploymentErrors, Long> {

    @Query("SELECT d FROM DeploymentErrors d WHERE d.appUser.id = :userId AND d.strategy.id = :strategyId AND d.deployedOn >= :startOfDay AND d.deployedOn < :endOfDay ORDER BY d.deployedOn DESC")
    List<DeploymentErrors> findTodayByStrategyId(
            @Param("userId") Long userId,
            @Param("strategyId") Long strategyId,
            @Param("startOfDay") Instant startOfDay,
            @Param("endOfDay") Instant endOfDay
    );

    @Query("SELECT d FROM DeploymentErrors d WHERE d.appUser.id = :userId AND d.strategy.id = :strategyId ORDER BY d.deployedOn DESC LIMIT 10")
    List<DeploymentErrors> findLatest10ByStrategyId(
            @Param("userId") Long userId,
            @Param("strategyId") Long strategyId
    );

    @Query("SELECT d FROM DeploymentErrors d WHERE d.appUser.id = :userId AND d.deployedOn >= :startOfDay AND d.deployedOn < :endOfDay ORDER BY d.deployedOn DESC")
    List<DeploymentErrors> findByUserIdAndErrorToday(
            @Param("userId") Long userId,
            @Param("startOfDay") Instant startOfDay,
            @Param("endOfDay") Instant endOfDay
    );


    @Query("SELECT d FROM DeploymentErrors d WHERE d.appUser.id = :userId AND d.strategy.id = :strategyId")
    List<DeploymentErrors> findByStrategyIdAndError(
            @Param("strategyId") Long strategyId,
            @Param("userId") Long userId
            );

    @Query("SELECT d FROM DeploymentErrors d WHERE d.appUser.id = :userId")
    List<DeploymentErrors> findByUserId( @Param("userId") Long userId);

}
