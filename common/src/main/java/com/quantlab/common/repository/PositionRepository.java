package com.quantlab.common.repository;

import com.quantlab.common.entity.Position;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;

@Repository
@EnableJpaRepositories
public interface PositionRepository extends JpaRepository<Position,Long> {

     // Get today's positions for a specific user
     @Query("SELECT p FROM Position p WHERE p.appUser.id = :userId AND p.deployedOn >= :startOfDay AND p.deployedOn < :endOfDay")
     List<Position> findByAppUserIdAndDeployedOnToday(
             @Param("userId") Long userId,
             @Param("startOfDay") Instant startOfDay,
             @Param("endOfDay") Instant endOfDay
     );

    Page<Position> findAll(Specification<Position> spec, Pageable pageable);

    @Query("SELECT p FROM Position p WHERE p.appUser.id = :appUser AND p.deployedOn >= :startOfDay " +
            "AND p.deployedOn < :endOfDay AND p.exchangeInstrumentId = :exchangeInstrumentId")
    List<Position> findByAppUserIdAndDeployedOnTodayAndExchangeInstrumentId(
            @Param("appUser") Long userId,
            @Param("startOfDay") Instant startOfDay,
            @Param("endOfDay") Instant endOfDay,
            @Param("exchangeInstrumentId") String exchangeInstrumentId
            );
}
