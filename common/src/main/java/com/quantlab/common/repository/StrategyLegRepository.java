package com.quantlab.common.repository;


import com.quantlab.common.dto.StrategyLegPNLDTO;
import com.quantlab.common.dto.DiyLegStopLossCheckDto;
import com.quantlab.common.entity.StrategyLeg;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

@Repository
public interface StrategyLegRepository extends JpaRepository<StrategyLeg, Long> {

    public List<StrategyLeg> findByStrategyIdAndStrategy_StrategyTag(Long strategyId, String category);

    public List<StrategyLeg> findBySignal_IdAndAppUser_AppUserId(Long signalId, Long userId);

    public List<StrategyLeg> findBySignalIdAndLegType(Long signalId, String legType);

    public List<StrategyLeg> findBySignalIdAndStatus(Long signalId,String status);
    // Custom delete query
    @Modifying
    @Query("DELETE FROM StrategyLeg l WHERE l.id NOT IN :ids AND l.strategy.id = :strategyId")
    void deleteEntitiesNotInAndByStrategyId(List<Long> ids, Long strategyId);

    @Modifying
    @Query("DELETE FROM StrategyLeg l WHERE l.strategy.id = :strategyId")
    void deleteByStrategyId(Long strategyId);

    @Modifying
    @Query("UPDATE StrategyLeg s SET s.lastPositionQuantity = :lastPositionQuantity WHERE s.appUser.id = :appUserId AND s.exchangeInstrumentId = :exchangeInstrumentId AND s.updatedAt >= :startOfDay AND s.updatedAt < :endOfDay")
    int updatePositionQuantity(@Param("lastPositionQuantity") Long lastPositionQuantity,
                               @Param("exchangeInstrumentId") Long exchangeInstrumentId,
                               @Param("startOfDay") Instant startOfDay,
                               @Param("endOfDay") Instant endOfDay,
                               @Param("appUserId") Long id
                               );

    @Query("SELECT s FROM StrategyLeg s WHERE s.strategy.id = :strategyId AND s.signal IS NULL")
    List<StrategyLeg>findDefaultStrategyLegs(@Param("strategyId") Long strategyId);

    // New: batch fetch default legs for multiple strategy IDs to avoid N+1 queries
    @Query("SELECT s FROM StrategyLeg s WHERE s.strategy.id IN :strategyIds AND s.signal IS NULL")
    List<StrategyLeg> findDefaultStrategyLegsByStrategyIds(@Param("strategyIds") List<Long> strategyIds);

    @Query("SELECT new map(leg.exchangeInstrumentId, leg.price) " +
            "FROM StrategyLeg leg " +
            "WHERE leg.signal.id = :signalId AND LOWER(leg.legType) = 'exit'")
    Map<Long, Long> findExitLegPricesBySignalId(@Param("signalId") Long signalId);

    @Query("SELECT DISTINCT leg.exchangeInstrumentId, leg.executedPrice " +
            "FROM StrategyLeg leg " +
            "WHERE leg.signal.id = :signalId AND LOWER(leg.legType) = 'exit'")
    List<Object[]> findExitLegIdAndPricePairs(@Param("signalId") Long signalId);

    @Modifying
    @Transactional
    @Query(value = "UPDATE signal.strategy_leg SET ltp = :ltp, profit_loss = :profitLoss, current_iv = :currentIV, current_delta = :currentDelta " +
            "WHERE id = :legId", nativeQuery = true)
    void updateRealtimeLegData(@Param("legId") Long legId,
                               @Param("ltp") Long ltp,
                               @Param("profitLoss") Long profitLoss,
                               @Param("currentIV") Long currentIV,
                               @Param("currentDelta") Long currentDelta);

    @Query(value = "SELECT id, ltp, profit_loss, current_iv, current_delta, buy_sell_flag, " +
            "filled_quantity, price, name, status, leg_type, lot_size, no_of_lots, " +
            "signal_id, exchange_instrument_id, executed_price, constant_iv, constant_delta, " +
            "latest_index_price, base_index_price " +
            "FROM signal.strategy_leg " +
            "WHERE signal_id = :signalId AND leg_type = :legType " +
            "ORDER BY exchange_instrument_id, created_at",
            nativeQuery = true)
    List<Object[]> findLegsBySignalIdAndLegType(@Param("signalId") Long signalId, @Param("legType") String legType);

    @Query(value = "SELECT id, ltp, profit_loss, current_iv, current_delta, buy_sell_flag, " +
            "filled_quantity, price, name, status, leg_type, lot_size, no_of_lots, " +
            "signal_id, exchange_instrument_id, executed_price, constant_iv, constant_delta, " +
            "latest_index_price, base_index_price " +
            "FROM signal.strategy_leg " +
            "WHERE signal_id = :signalId AND leg_type = :type",
            nativeQuery = true)
    List<Object[]> findLegsBySignalIdAndStatus(@Param("signalId") Long signalId,
                                               @Param("type") String type);


    List<StrategyLeg> findByStrategyIdAndLegType(Long strategyId,String legType);

    @Query(value = "SELECT COUNT(*) = 0 FROM signal.strategy_leg WHERE signal_id = :signalId AND status <> :status", nativeQuery = true)
    boolean isAllLegsStatusBySignalId(@Param("signalId") Long signalId, @Param("status") String status);

    @Query(value = "SELECT COUNT(*) = 0 FROM signal.strategy_leg WHERE signal_id = :signalId AND status = :status", nativeQuery = true)
    boolean noLegHasStatusBySignalId(@Param("signalId") Long signalId, @Param("status") String status);

    @Modifying
    @Query(value = "UPDATE signal.strategy_leg SET exchange_status = :exchangeStatus WHERE id = :id", nativeQuery = true)
    void updateExchangeStatus(@Param("id") Long id, @Param("exchangeStatus") String exchangeStatus);

    List<StrategyLeg> findByStrategyId(Long strategyId);

    List<StrategyLeg> findByStrategyIdAndSignalIdIsNull(Long strategyId);

    @Query(value = """
    SELECT id
    FROM signal.strategy_leg
    WHERE signal_id = :signalId
      AND exchange_instrument_id = :exchangeInstrumentId
      AND LOWER(leg_type) != LOWER(:legType)
    ORDER BY created_at DESC
    LIMIT 1
    """, nativeQuery = true)
    Long findLatestLegId(@Param("signalId") Long signalId,
                         @Param("exchangeInstrumentId") Long exchangeInstrumentId,
                         @Param("legType") String legType);


    @Transactional
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = """
    UPDATE signal.strategy_leg
    SET profit_loss = :profitLoss
    WHERE id = :id
    """, nativeQuery = true)
    int updateProfitLossById(@Param("id") Long id,
                             @Param("profitLoss") Long profitLoss);

    @Query(value = "SELECT sl.id, sl.ltp, sl.profit_loss, sl.current_iv, sl.current_delta, " +
           "sl.buy_sell_flag, sl.filled_quantity, sl.price, sl.name, sl.status, sl.leg_type, " +
           "sl.lot_size, sl.no_of_lots, sl.signal_id, sl.exchange_instrument_id, sl.executed_price, " +
           "sl.constant_iv, sl.constant_delta, sl.latest_index_price, sl.base_index_price " +
           "FROM signal.strategy_leg sl WHERE sl.signal_id IN ?1 AND sl.leg_type = ?2", nativeQuery = true)
    List<Object[]> findLegsBySignalIdsAndStatus(Set<Long> signalIds, String legType);

    @Query(value = "SELECT id, ltp, profit_loss, current_iv, current_delta, buy_sell_flag, " +
            "filled_quantity, price, name, status, leg_type, lot_size, no_of_lots, " +
            "signal_id, exchange_instrument_id, executed_price, constant_iv, constant_delta, " +
            "latest_index_price, base_index_price " +
            "FROM signal.strategy_leg " +
            "WHERE signal_id = :signalId " +
            "AND exchange_instrument_id = :exchangeInstrumentId " +
            "AND leg_type = 'open' " +
            "AND (profit_loss IS NULL OR profit_loss = 0) " +
            "ORDER BY created_at ASC " +
            "LIMIT 1",
            nativeQuery = true)
    List<Object[]> findOldestOpenLeg(@Param("signalId") Long signalId,
                                            @Param("exchangeInstrumentId") Long exchangeInstrumentId);

    @Query(value = "SELECT SUM(sl.profit_loss) FROM signal.strategy_leg sl WHERE sl.strategy_id = :strategyId AND DATE(sl.created_at) = CURRENT_DATE", nativeQuery = true)
    Optional<Long> sumAllLegsProfitLossByStrategyId(Long strategyId);


    long countBySignalId(Long signalId);

    List<StrategyLeg> findBySignalId(Long id);
    List<StrategyLeg> findBySignalIdAndIdInAndStatus(Long signalId, List<Long> ids, String status);
    long countBySignalIdAndStatusAndLegType(Long signalId, String status, String legType);

    @Query("SELECT new com.quantlab.common.dao.DeltaNeutralLegDao(sl.legType, sl.optionType, sl.noOfLots) FROM StrategyLeg sl WHERE sl.signal.id = :signalId")
    List<com.quantlab.common.dao.DeltaNeutralLegDao> findLegsSummaryBySignalId(@Param("signalId") Long signalId);

    @Query(value = "SELECT strategy_id FROM signal.strategy_leg WHERE id = :legId", nativeQuery = true)
    Long findStrategyIdByLegId(@Param("legId") Long legId);

    @Query(value = "SELECT id, exchange_instrument_id, buy_sell_flag, status " +
            "FROM signal.strategy_leg " +
            "WHERE signal_id = :signalId", nativeQuery = true)
    List<Object[]> findMinimalLegsBySignalId(@Param("signalId") Long signalId);

    @Query("""
            SELECT new com.quantlab.common.dto.DiyLegStopLossCheckDto(
                sl.id,
                sl.name,
                sl.status,
                sl.trailingStopLossToggle,
                sl.trailingDistance,
                sl.trailingStopLossPoints,
                sl.stopLossUnitType,
                sl.stopLossUnitToggle,
                sl.stopLossUnitValue,
                sl.stopLossFinalValue,
                sl.targetUnitType,
                sl.targetUnitToggle,
                sl.targetUnitValue,
                sl.targetFinalValue,
                sl.executedPrice,
                sl.exchangeInstrumentId,
                sl.quantity,
                sl.buySellFlag
            )
            FROM StrategyLeg sl
            JOIN sl.signal s
            WHERE s.strategy.id = :strategyId
              AND s.status = :signalStatus
              AND s.createdAt = (
                    SELECT MAX(s2.createdAt)
                    FROM Signal s2
                    WHERE s2.strategy.id = :strategyId
                      AND s2.status = :signalStatus
                )
              AND sl.status = :legStatus
              AND (sl.stopLossFinalValue IS NOT NULL OR sl.targetFinalValue IS NOT NULL)
            """)
    List<DiyLegStopLossCheckDto> findLatestSignalLegsForStopLossCheck(@Param("strategyId") Long strategyId,
                                                                       @Param("signalStatus") String signalStatus,
                                                                       @Param("legStatus") String legStatus);

    // Surgical update used after createEquitySignal (REQUIRES_NEW) commits — saving the
    // detached leg via saveAll triggered a cascade-merge that nulled signal_id, orphaning legs.
    @Modifying
    @Transactional
    @Query(value = "UPDATE signal.strategy_leg SET leg_additions_id = :legAdditionsId WHERE id = :legId", nativeQuery = true)
    void updateLegAdditionsId(@Param("legId") Long legId,
                              @Param("legAdditionsId") Long legAdditionsId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT l FROM StrategyLeg l WHERE l.id = :id")
    Optional<StrategyLeg> findByIdForUpdate(Long id);

}
