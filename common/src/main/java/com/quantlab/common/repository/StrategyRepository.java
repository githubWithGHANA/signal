package com.quantlab.common.repository;


import com.quantlab.common.dao.ActiveStrategyDao;
import com.quantlab.common.dao.StrategyIdAndSourceIdDAO;
import com.quantlab.common.dto.StrategyStatus;
import com.quantlab.common.entity.AppUser;
import com.quantlab.common.entity.Strategy;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.sql.Timestamp;
import java.util.*;

import static com.quantlab.common.utils.staticstore.AppConstants.*;

@Repository
public interface StrategyRepository extends JpaRepository<Strategy, Long> {

    // custom Query to retrieve all strategies that are not marked for deletion
    @Query("SELECT s FROM Strategy s WHERE s.deleteIndicator != 'Y'")
    List<Strategy> findAllAvailableStrategies();

    // this will retrieve the strategy by ID that is not marked for deletion
    @Query("SELECT s FROM Strategy s WHERE s.id = :id AND s.deleteIndicator != 'Y'")
    Optional<Strategy> findAvailableStrategyById(@Param("id") Long id);

    @Query("SELECT new com.quantlab.common.dao.StrategyIdAndSourceIdDAO(s.id, s.sourceId) FROM Strategy s WHERE s.appUser.tenentId = :userId ")
    List<StrategyIdAndSourceIdDAO> findAllStrategyIDsAndSourceId(String userId);

    @Query("SELECT new com.quantlab.common.dao.StrategyIdAndSourceIdDAO(s.id, s.sourceId) FROM Strategy s WHERE s.appUser.id = 1L and s.id < 1000L")
    List<StrategyIdAndSourceIdDAO> findAllStrategyIDsAndSourceIdByAdminId();

    List<Strategy> findByIdIn(List<Long> ids);

    List<Strategy> findAll();

    List<Strategy> findAllByCreatedAtBefore(Date date);

    List<Strategy> findAllByAppUserId(Long id);

    Optional<Strategy> findById(Long id);

    Optional<Strategy> findByIdAndAppUser_TenentId(Long id, String clientId);

    List<Strategy> findByDeleteIndicator(String deleteIndicator);

    List<Strategy> findAllByAppUser_IdOrderByIdAsc(Long userId);

    List<Strategy> findAllByAppUser_Id(Long userId);

    List<Strategy> findAllByUserAdmin_Id(Long adminId);

    Optional<Strategy> findByName(String name);

    Optional<Strategy> findByNameAndAppUser(String name, AppUser appUser);

    List<Strategy> findAllByStatus(String status);

    ArrayList<Strategy> findByIsTemplate(String isTemplate);

    ArrayList<Strategy> findBySourceId(Long sourceId);

    @Query("SELECT s FROM Strategy s WHERE s.status = '" + STATUS_ACTIVE + "' OR s.status = '" + STATUS_LIVE + "' OR s.status = '" + STATUS_STAND_BY + "' and s.appUser.id =:userId")
    List<Strategy> findLiveOrActiveOrStandBySignals(@Param("userId") Long userId);

    List<Strategy> findByStatusInAndAppUser(List<String> statuses, AppUser appUser);

    List<Strategy> findByStatusInAndAppUserAndExecutionType(List<String> statuses, AppUser appUser, String executionType);

    List<Strategy> findAllBySubscriptionAndAppUserOrderByIdAsc(String subscription, AppUser appUser);


    @Query("SELECT s FROM Strategy s WHERE s.appUser.id =1")
    List<Strategy> findDefaultStrategys();


    @Query("SELECT s FROM Strategy s JOIN s.appUser u WHERE u IN :appUsers AND s.status = :status")
    List<Strategy> findStrategiesByAppUsersAndStatus(@Param("appUsers") List<AppUser> appUsers, @Param("status") String status);

    List<Strategy> findAllByStatusAndPositionType(String status, String positionType);

    List<Strategy> findAllByDeleteIndicator(String deleteIndicator);

    @Query("SELECT s.id FROM Strategy s WHERE s.sourceId = :sourceId")
    List<Long> findIdsBySourceId(@Param("sourceId") Long sourceId);

    @Modifying
    @Transactional
    @Query("UPDATE Strategy s SET s.deleteIndicator = :deleteFlag WHERE s.id IN :strategyIds")
    void updateDeleteIndicatorForStrategies(@Param("strategyIds") List<Long> strategyIds,
                                            @Param("deleteFlag") String deleteFlag);

    @Modifying
    @Transactional
    @Query("UPDATE Strategy s SET s.holdType = :holdType WHERE s.id IN :strategyIds")
    void updateHoldTypeForStrategies(@Param("strategyIds") List<Long> strategyIds,
                                     @Param("holdType") String holdType);

    List<Strategy> findAllByDeleteIndicatorAndUserAdminIsNotNull(String deleteIndicator);


    List<Strategy> findAllByDeleteIndicatorAndSubscription(String deleteIndicator, String subscription);

//    @Query(value = "SELECT DISTINCT st FROM Strategy st " +
//            "LEFT JOIN st.signals s " +
//            "WHERE s.id IS NULL OR st.id NOT IN (" +
//            "  SELECT DISTINCT s2.strategy.id FROM Signal s2 WHERE s2.status = 'live'" +
//            ")")
//    List<Strategy> findStrategiesWithoutLiveSignals();


    @Query(value = "SELECT DISTINCT st FROM Strategy st " +
            "LEFT JOIN st.signals s " +
            "WHERE (s.id IS NULL OR st.id NOT IN (" +
            "  SELECT DISTINCT s2.strategy.id FROM Signal s2 WHERE s2.status = 'live'" +
            ")) " +
            "AND st.subscription = 'Y' AND st.deleteIndicator != 'Y'")
    List<Strategy> findStrategiesWithoutLiveSignals();

    @Query("SELECT SUM(s.multiplier * s.minCapital) FROM Strategy s WHERE s.appUser.id = :userId AND s.subscription = :SubscriptionStatus AND s.executionType = :executionType")
    Long fetchDeployedStrategiesCapitalByUserId(@Param("userId") Long userId, @Param("SubscriptionStatus") String status, @Param("executionType") String executionType);

    @Modifying
    @Transactional
    @Query("UPDATE Strategy s SET s.status = :newStatus WHERE s.appUser.id = :appUserId AND s.status = :oldStatus AND s.executionType = :executionType")
    int updateStrategyStatusForAppUser(@Param("appUserId") Long appUserId, @Param("newStatus") String newStatus, @Param("oldStatus") String oldStatus, @Param("executionType") String executionType);

    @Query("SELECT s FROM Strategy s WHERE s.subscription = :subscription AND s.sourceId IS NOT NULL")
    List<Strategy> findAllBySubscriptionAndSourceNotNull(@Param("subscription") String subscription);


    List<Strategy> findAllByAppUserIdAndStatus(Long id, String status);

    List<Strategy> findAllByAppUserIdAndStatusAndExecutionType(Long id, String status, String executionType);

    @Modifying
    @Transactional
    @Query(value = "UPDATE signal.strategy set signal_count = signal_count +1  where id=:id",
            nativeQuery = true)
    void updateSignalCount(@Param("id") Long id);

    @Query("SELECT s.id FROM Strategy s WHERE s.subscription = :subscription")
    List<Long> findIdsBySubscription(@Param("subscription") String subscription);

    @Modifying
    @Transactional
    @Query(value = "UPDATE signal.strategy SET status = :status WHERE id = :id", nativeQuery = true)
    void updateStrategyStatus(@Param("id") Long id, @Param("status") String status);

    @Query(
            value = "SELECT t.leg_type, COALESCE(COUNT(sl.leg_type), 0) AS count " +
                    "FROM (VALUES (:type1), (:type2)) AS t(leg_type) " +
                    "LEFT JOIN strategy_leg sl ON sl.leg_type = t.leg_type AND sl.signal_id = :signalId " +
                    "GROUP BY t.leg_type",
            nativeQuery = true
    )
    List<Object[]> countLegsByTwoTypesForSignal(@Param("signalId") Long signalId, @Param("type1") String type1, @Param("type2") String type2);

    @Query(
            value = "SELECT id AS id, status AS status FROM signal.strategy WHERE user_id = :userId AND subscription = 'Y' AND delete_indicator != 'Y'",
            nativeQuery = true
    )
    List<StrategyStatus> findStrategyIdAndStatusBySubscriptionY(@Param("userId") Long userId);

    @Query("SELECT COUNT(s) FROM Strategy s WHERE s.appUser.id = :userId AND s.status = :status AND s.executionType = :executionType")
    Long findCountOfUserStrategiesByStatusAndExecutionMode(@Param("userId") Long userId, @Param("status") String status, @Param("executionType") String executionType);

    @Query("""
            SELECT s
            FROM Strategy s
            WHERE s.deleteIndicator = :deleteIndicator
              AND s.subscription = :subscription
              AND s.status IN :statuses
              AND s.executionType = :executionType
            """)
    List<Strategy> findStrategies(@Param("deleteIndicator") String deleteIndicator,
                                  @Param("subscription") String subscription,
                                  @Param("statuses") List<String> statuses,
                                  @Param("executionType") String executionType);

    List<Strategy> findAllByStatusInAndPositionType(List<String> status, String positionType);

    @Modifying
    @org.springframework.transaction.annotation.Transactional
    @Query(value = """
            UPDATE signal.strategy
            SET status = :newStatus
            WHERE s_position_type = :positionType
              AND status IN (:statuses)
            """, nativeQuery = true)
    int bulkUpdateStatus(
            @Param("newStatus") String newStatus,
            @Param("positionType") String executionType,
            @Param("statuses") List<String> statuses
    );


    @Query(value = "SELECT s.id, s.category, s.expiry, u.u_name, s.status, u.id, s.atm_type, s.execution_type, sg.id " +
            "FROM signal.strategy s " +
            "JOIN signal.signal sg ON sg.strategy_id = s.id " +
            "JOIN signal.underlying u ON s.underlying_id = u.id " +
            "WHERE sg.id = :signalId",
            nativeQuery = true)
    Object findStrategyPNLDtoBySignalId(@Param("signalId") Long signalId);

    @Query(value = "SELECT s.id, s.category, s.expiry, u.u_name, s.status, u.id, " +
            "s.atm_type, s.execution_type, sg.id " +
            "FROM signal.strategy s " +
            "JOIN signal.signal sg ON sg.strategy_id = s.id " +
            "JOIN signal.underlying u ON s.underlying_id = u.id " +
            "WHERE sg.id IN (:signalIds)",
            nativeQuery = true)
    List<Object[]> findStrategyPNLDtoBySignalIds(@Param("signalIds") Set<Long> signalIds);


    @Query("""
            SELECT new com.quantlab.common.dao.ActiveStrategyDao(
                s.name,
                s.id,
                s.lastDeployedOn,
                s.executionType,
                s.status,
                s.minCapital,
                s.multiplier,
                s.signalCount,
                s.category,
                s.minCapital,
                s.positionType,
                s.isHidden,
                s.reSignalCount,
                s.todayPNL
            )
            FROM Strategy s
            WHERE s.appUser.id = :userId AND s.subscription = :subscription
            ORDER BY s.id ASC
            """)
    List<ActiveStrategyDao> findAllActiveStrategies(String subscription, Long userId);


    @Modifying
    @Transactional
    @Query("UPDATE Strategy s SET s.drawDown = :drawDown WHERE s.id = :id")
    void updateDrawDown(@Param("id") Long id, @Param("drawDown") Long drawDown);

    @Query(value = "SELECT todays_pnl FROM signal.strategy WHERE id = :id", nativeQuery = true)
    Long findTodayPNLById(@Param("id") Long id);

    @Modifying
    @Query(value = "UPDATE signal.strategy SET todays_pnl = :pnl WHERE id = :id", nativeQuery = true)
    void updateTodayPNLById(@Param("id") Long id, @Param("pnl") Long pnl);

    List<Strategy> findAllByAppUser_IdAndCategoryOrderByIdAsc(Long userId, String category);


    // Native query returning raw rows; default method maps rows to StrategySummaryDao
    @Query(value = "SELECT s.id, s.name, s.description, s.atm_type, s.multiplier, s.min_capital, u.id AS underlying_id, u.u_name AS underlying_name, s.s_position_type AS position_type, s.execution_type, s.created_at, s.strategy_tag, s.status, s.category, s.draw_down, s.subscription, s.last_deployed_on, s.is_hidden, s.re_signal_count, s.todays_pnl, s.expiry FROM signal.strategy s JOIN signal.underlying u ON s.underlying_id = u.id WHERE s.user_id = :userId AND s.category = :category ORDER BY s.id ASC", nativeQuery = true)
    List<Object[]> findAllStrategySummariesByUserIdAndCategoryNative(@Param("userId") Long userId, @Param("category") String category);

    default List<com.quantlab.common.dao.StrategySummaryDao> findAllStrategySummariesByUserIdAndCategory(@Param("userId") Long userId, @Param("category") String category) {
        List<Object[]> rows = findAllStrategySummariesByUserIdAndCategoryNative(userId, category);
        List<com.quantlab.common.dao.StrategySummaryDao> results = new ArrayList<>();
        for (Object[] r : rows) {
            Long id = r[0] != null ? ((Number) r[0]).longValue() : null;
            String name = r[1] != null ? r[1].toString() : null;
            String description = r[2] != null ? r[2].toString() : null;
            String atmType = r[3] != null ? r[3].toString() : null;
            Long multiplier = r[4] != null ? ((Number) r[4]).longValue() : null;
            Long minCapital = r[5] != null ? ((Number) r[5]).longValue() : null;
            Long underlyingId = r[6] != null ? ((Number) r[6]).longValue() : null;
            String underlyingName = r[7] != null ? r[7].toString() : null;
            String positionType = r[8] != null ? r[8].toString() : null;
            String executionType = r[9] != null ? r[9].toString() : null;
            Instant createdAt = null;
            if (r[10] instanceof Timestamp) createdAt = ((Timestamp) r[10]).toInstant();
            else if (r[10] instanceof Instant) createdAt = (Instant) r[10];

            String strategyTag = r[11] != null ? r[11].toString() : null;
            String status = r[12] != null ? r[12].toString() : null;
            String categoryCol = r[13] != null ? r[13].toString() : null;
            Long drawDown = r[14] != null ? ((Number) r[14]).longValue() : null;
            String subscription = r[15] != null ? r[15].toString() : null;
            String lastDeployedOn = r[16] != null ? r[16].toString() : null;

            Boolean isHidden = null;
            Object isHiddenObj = r[17];
            if (isHiddenObj instanceof Boolean) isHidden = (Boolean) isHiddenObj;
            else if (isHiddenObj instanceof Number) isHidden = ((Number) isHiddenObj).intValue() != 0;
            else if (isHiddenObj instanceof String) isHidden = "Y".equalsIgnoreCase((String) isHiddenObj) || "true".equalsIgnoreCase((String) isHiddenObj);

            Integer reSignalCount = r[18] != null ? ((Number) r[18]).intValue() : null;
            Long todayPNL = r[19] != null ? ((Number) r[19]).longValue() : null;
            String expiry = r[20] != null ? r[20].toString() : null;

            results.add(new com.quantlab.common.dao.StrategySummaryDao(
                    id,
                    name,
                    description,
                    atmType,
                    multiplier,
                    minCapital,
                    underlyingId,
                    underlyingName,
                    positionType,
                    executionType,
                    createdAt,
                    strategyTag,
                    status,
                    categoryCol,
                    drawDown,
                    subscription,
                    lastDeployedOn,
                    isHidden,
                    reSignalCount,
                    todayPNL,
                    expiry
            ));
        }
        return results;
    }

    @Query(value = """
            SELECT s.name
            FROM signal.strategy s
            WHERE s.user_id = :userId
              AND s.execution_type = :executionType AND s.subscription = 'Y'
            """, nativeQuery = true)
    List<String> findListOfStrategyNamesByUserAndExecutionType(@Param("userId") Long userId,
                                                               @Param("executionType") String executionType);

    @Query(value = """
            SELECT COUNT(*)
            FROM signal.strategy s
            WHERE s.user_id = :userId
              AND s.execution_type = :executionType
              AND (s.status = :status OR s.manual_exit_type != :manualExitType)
            """, nativeQuery = true)
    Long countStrategiesByUserIdStatusOrManualExitTypeAndExecutionType(
            @Param("userId") Long userId,
            @Param("status") String status,
            @Param("manualExitType") String manualExitType,
            @Param("executionType") String executionType);

    @Modifying
    @Query(value = """
            UPDATE signal.strategy
            SET manual_exit_type = :manualExitType
            WHERE subscription = :subscription
              AND execution_type = :executionType
              AND user_id = :userId
            """, nativeQuery = true)
    int setManualExitTypeForAppUserByExecutionModeAndSubscription(
            @Param("userId") Long userId,
            @Param("manualExitType") String manualExitType,
            @Param("subscription") String subscription,
            @Param("executionType") String executionType);


    @Query(value = "SELECT SUM(s.todays_pnl) FROM signal.strategy s WHERE s.user_id = :userId AND s.execution_type = :executionType AND s.subscription = :subscription", nativeQuery = true)
    Long sumAllTodaysPAndLByUserIdAndExecutionType(Long userId, String executionType, String subscription);

    @Query(value = "SELECT SUM(s.total_pnl) FROM signal.strategy s WHERE s.user_id = :userId AND s.execution_type = :executionType AND s.subscription = :subscription", nativeQuery = true)
    Long sumAllTotalPAndLByUserIdAndExecutionType(Long userId, String executionType, String subscription);

    @Query(value = """
            SELECT
                COALESCE(SUM(s.todays_pnl), 0) AS todaysPnl,
                COALESCE(SUM(s.total_pnl), 0) AS totalPnl,
                COALESCE(SUM(s.multiplier * s.min_capital), 0) AS deployedCapital
            FROM signal.strategy s
            WHERE s.user_id = :userId
              AND s.execution_type = :executionType
              AND s.subscription = :subscription
            """, nativeQuery = true)
    Map<String, Object> fetchUserPnLAndCapital(
            @Param("userId") Long userId,
            @Param("executionType") String executionType,
            @Param("subscription") String subscription);


    @Query("SELECT s.id FROM Strategy s WHERE s.status = :status")
    List<Long> getPendingStrategyIdsByStatus(@Param("status") String status);

    @Query("SELECT s.id FROM Strategy s WHERE s.status IN :statuses")
    List<Long> getStrategyIdsByStatuses(@Param("statuses") List<String> statuses);

    @Query("SELECT s FROM Strategy s LEFT JOIN FETCH s.strategyAdditions WHERE s.id = :id")
    Optional<Strategy> findByIdWithAdditions(@Param("id") Long id);


    @Query("""
            SELECT new com.quantlab.common.dao.ActiveStrategyDao(
                s.name,
                s.id,
                s.lastDeployedOn,
                s.executionType,
                s.status,
                s.minCapital,
                s.multiplier,
                s.signalCount,
                s.category,
                s.minCapital,
                s.positionType,
                s.isHidden,
                s.reSignalCount,
                s.todayPNL
            )
            FROM Strategy s
            WHERE s.id = :strategyId""")
    ActiveStrategyDao findReadyToDeployPageStrategy(Long strategyId);




    @Query("SELECT new com.quantlab.common.dao.StrategyStatusResignalDao(s.status, s.reSignalCount, s.signalCount, s.manualExitType) FROM Strategy s WHERE s.id = :id")
    Optional<com.quantlab.common.dao.StrategyStatusResignalDao> findStatusAndCountsById(@Param("id") Long id);

    Optional<Strategy> findByNameAndAppUser_id(String strategyName, Long id);

    @Modifying
    @Transactional
    @Query("""
    UPDATE Strategy s
    SET s.status = 'active'
    WHERE s.appUser.id = :appUserId
      AND s.status = 'pre-active'
    """)
    int convertAllPreActiveStrategiesToActive(@Param("appUserId") Long appUserId);

    @Modifying
    @Transactional
    @Query("""
    UPDATE Strategy s
    SET s.status = 'active'
    WHERE s.appUser.id = :appUserId
      AND s.status = 'pre-active'
      AND s.executionType = 'PaperTrading'
    """)
    int convertPaperTradingPreActiveStrategiesToActive(@Param("appUserId") Long appUserId);

    @Query("SELECT s.strategy FROM Signal s WHERE s.id = :signalId")
    Optional<Strategy> findStrategyBySignalId(@Param("signalId") Long signalId);

    @Modifying
    @Transactional
    @Query("UPDATE Strategy s SET s.status = :status WHERE s.id = :id")
    int updateStatusById(@Param("id") Long id,
                         @Param("status") String status);

}
