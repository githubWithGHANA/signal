package com.quantlab.common.repository;
import com.quantlab.common.entity.StrategyAdditions;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;
import java.util.Optional;

public interface StrategyAdditionsRepository extends JpaRepository<StrategyAdditions, Long> {

    @Modifying
    @Transactional
    @Query("UPDATE StrategyAdditions a SET a.lastExitCandleTimestamp = :epoch WHERE a.strategy.id = :strategyId")
    void updateLastExit(@Param("strategyId") Long strategyId,
                        @Param("epoch") Long epoch);
}
