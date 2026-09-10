package com.quantlab.common.repository;

import com.quantlab.common.entity.AppUser;
import com.quantlab.common.entity.Signal;
import com.quantlab.common.entity.Strategy;
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
import java.util.Optional;
import java.util.Set;

import static com.quantlab.common.utils.staticstore.AppConstants.SIGNAL_STATUS_LIVE;

@Repository
public interface SignalRepository extends JpaRepository<Signal, Long> {

    @Query("SELECT s FROM Signal s WHERE s.status = 'active' OR s.status = 'standby'")
    List<Signal> findActiveOrStandBySignals();

    Optional<Signal> findById(Long id);


    @Query("select  e from Signal e where e.id = :id")
    Optional<Signal> findByID(Long id);

    @Query("SELECT s FROM Signal s WHERE s.strategy.id = :strategyId AND((CAST(s.createdAt AS date) = CURRENT_DATE) OR (s.status = '"+SIGNAL_STATUS_LIVE+"')) ORDER BY id DESC ")
    List<Signal> findCreatedTodayOrLive(@Param("strategyId") Long strategyId);

    List<Signal> findAllByAppUserIdAndStatus(Long id,String status);

    Optional<Signal> findByStrategyId(Long id);

    @Query(value = "SELECT s FROM Signal s WHERE s.strategy.id = :strategyId AND s.status = :status ORDER BY s.createdAt DESC")
    Optional<Signal> findTopByStrategyIdAndStatusOrderByCreatedAtDesc(@Param("strategyId") Long strategyId, @Param("status") String status);

    Optional<Signal> findFirstByStrategyIdAndStatusOrderByCreatedAtDesc(Long strategyId, String status);


    List<Signal> findByStrategyIdAndStatus(Long id,String status);

    List<Signal> findByStrategyIdAndStatusAndCreatedAtAfter(Long strategy_id, String status, Instant createdAt);

    List<Signal> findByStrategyIdAndStatusOrderByIdAsc(Long id, String status);


    List<Signal> findAllByStatus(String status);
    Signal findByStrategyIdAndStatusAndCreatedAtBetween(Long id,String status, Instant startOfDay, Instant endOfDay);


    List<Signal> findByStatusIn(@Param("statuses") List<String> statuses);

    List<Signal> findAllByStatusIn(List<String> statuses);

    @Query(value = "SELECT SUM(s.profit_loss) FROM signal.signal s WHERE CAST(s.created_at AS date) = CURRENT_DATE AND s.user_id = :userId AND s.execution_type = :executionType", nativeQuery = true)
    Long findAllByCreatedAtTodayAndAppUsers(@Param("userId") Long userId, @Param("executionType") String  executionType);

    @Query(value = "SELECT SUM(s.profit_loss) FROM signal.signal s WHERE s.status = :status AND s.strategy_id  = :strategyId", nativeQuery = true)
    Long findTotalPAndLByLiveSignals(@Param("status") String status,@Param("strategyId") Long strategyId);

    @Query(value = "SELECT SUM(s.profit_loss) FROM signal.signal s WHERE s.strategy_id = :strategyId", nativeQuery = true)
    Long findTotalStrategyPAndLBySignals(@Param("strategyId") Long strategyId);

    @Query(value = "SELECT SUM(s.profit_loss) FROM signal.signal s WHERE s.strategy_id  = :strategyId AND s.Status != :notStatus AND DATE(s.created_at) = CURRENT_DATE ", nativeQuery = true)
    Long findTotalPNLByNonLiveSignalsToday(@Param("notStatus") String notStatus,@Param("strategyId") Long strategyId);

    @Query(value = "SELECT SUM(s.profit_loss) FROM signal.signal s WHERE user_id = :userId  AND" +
            " s.execution_type = :executionType", nativeQuery = true)
    Long findOverallUserPAndL(@Param("userId") Long userId, @Param("executionType") String  executionType);


    @Query(value = "SELECT SUM(s.profit_loss) FROM signal.signal s " +
            "JOIN signal.strategy st ON s.strategy_id = st.id " +
            "WHERE st.id = :strategyId " +
            "AND s.execution_type = st.execution_type", nativeQuery = true)
    Long findOverallStrategyPAndL(@Param("strategyId") Long  strategyId);

    @Query(value = "SELECT SUM(s.profit_loss) " +
            "FROM signal.signal s " +
            "JOIN signal.strategy st ON s.strategy_id = st.id " +
            "WHERE s.user_id = :userId " +
            "AND s.position_type = :positionType " +
            "AND (DATE(s.created_at) = CURRENT_DATE OR s.status = :status) " +
            "AND st.subscription = :subscribed " +
            "AND s.execution_type = :executionType",
            nativeQuery = true)
    Long findByExecutionPositionalPAndL(@Param("userId") Long userId,
                                        @Param("positionType") String positionType,
                                        @Param("status") String status,
                                        @Param("subscribed") String subscribed,
                                        @Param("executionType") String executionType);

    @Query(value = "SELECT SUM(s.profit_loss) FROM signal.signal s " +
            "JOIN signal.strategy st ON s.strategy_id = st.id " +
            "WHERE s.user_id = :userId " +
            "AND s.position_type = :positionType " +
            "AND DATE(s.created_at) = CURRENT_DATE "+
            "AND st.subscription = :subscribed " +
            "AND s.execution_type = :executionType",
            nativeQuery = true)
    Long findByExecutionIntraDayPAndL(@Param("userId") Long userId,
                                      @Param("positionType") String positionType,
                                      @Param("subscribed") String subscribed,
                                      @Param("executionType") String  executionType);


    @Query(value = "SELECT SUM(s.profit_loss) FROM signal.signal s WHERE s.strategy_id = :strategyId AND execution_type = :executionType", nativeQuery = true)
    Long findTotalPAndLBySignals(@Param("strategyId") Long strategyId, @Param("executionType") String executionType);

    @Query("SELECT s FROM Signal s JOIN s.appUser u WHERE u IN :appUsers AND s.status = :status")
    List<Signal> findSignalsByAppUsersAndStatus(@Param("appUsers") List<AppUser> appUsers, @Param("status") String status);

    @Query(value = "SELECT * FROM signal.signal s WHERE s.user_id = :userId AND status = :status", nativeQuery = true)
    List<Signal> findByUserIdAndStatus(@Param("userId") Long userId, @Param("status") String status);

    @Query(value = """
    SELECT SUM(s.profit_loss)
    FROM signal.signal s
    WHERE s.strategy_id = :strategyId
      AND (
            (s.created_at >= CURRENT_DATE AND s.created_at < CURRENT_DATE + INTERVAL '1 day')
          )
    """, nativeQuery = true)
    Long findMTMByStrategyId(@Param("strategyId") Long strategyId);

    @Query(value = "SELECT s.id FROM signal.signal s " +
            "WHERE s.status = :status " +
            "  AND s.updated_at >= CURRENT_DATE " +
            "  AND s.updated_at < CURRENT_DATE + INTERVAL '1 day' " +
            "  AND s.last_pnl IS DISTINCT FROM :lastPNL",
            nativeQuery = true)
    List<Long> findRecentlyExitedSignalIds(@Param("lastPNL") String lastPNL,
                                           @Param("status") String status);

    Optional<Signal> findFirstByStrategyOrderByCreatedAtDesc(Strategy strategy);

    List<Signal> findByStrategyAndStatusOrderById(Strategy strategy, String status);

    List<Signal> findAllByStrategyAndStatus(Strategy strategy, String status);

    List<Signal> findAllByStrategyAndStatusIn(Strategy strategy, List<String> status);

    List<Signal> findAllByStatusInAndPositionType(List<String> status, String positionType);

    long countByStrategyAndStatus(Strategy strategy, String status);
    long countByStrategyIdAndStatus(Long strategyId, String status);


    @Query(value = "SELECT id FROM signal.signal s " +
            "WHERE s.status = :status " +
            "AND ((s.position_type = :intraDay and DATE(s.created_at) = CURRENT_DATE )" +
            "or  s.position_type = :positional)",
            nativeQuery = true)
    List<Long> findSignalsByPositionType(@Param("positional") String positional,
                                         @Param("intraDay") String intraDay,
                                         @Param("status") String status);

    @Query(value = "SELECT * FROM signal.signal s " +
            "WHERE s.strategy_id = :strategyId " +
            "AND ((s.position_type = :intraDay AND DATE(s.created_at) = CURRENT_DATE) " +
            "OR (s.position_type = :positional AND (DATE(s.created_at) = CURRENT_DATE OR s.status = :status))) ORDER BY s.id DESC",
            nativeQuery = true)
    List<Signal> findSignalsByPositionTypeAndStrategy(@Param("positional") String positional,
                                                      @Param("intraDay") String intraDay,
                                                      @Param("status") String status,
                                                      @Param("strategyId") Long strategyId);



    @Query(value = "SELECT * FROM signal.signal s " +
            "WHERE s.strategy_id = :strategyId  and s.status = 'live'" +
            "AND ((s.position_type = :intraDay  and DATE(s.created_at) = CURRENT_DATE  )" +
            "or  s.position_type = :positional)  ORDER BY s.id DESC",
            nativeQuery = true)
    List<Signal> findSignalsByPositionTypeAndStrategyLive(@Param("positional") String positional,
                                                          @Param("intraDay") String intraDay,
                                                          @Param("strategyId") Long strategyId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT s FROM Signal s WHERE s.id = :id")
    Optional<Signal> findByIdForUpdate(@Param("id") Long id);

    @Modifying
    @Transactional
    @Query(value = "UPDATE signal.signal SET profit_loss = :profitLoss, latest_index_price = :indexNow WHERE id = :id", nativeQuery = true)
    void updateProfitLossAndIndexNowById(@Param("id") Long id,
                                         @Param("profitLoss") Long profitLoss,
                                         @Param("indexNow") Long indexNow);

    @Transactional
    @Modifying
    @Query(value = "UPDATE signal.signal SET profit_loss = :profitLoss, last_pnl = :lastPNL " +
            "WHERE id = :signalId", nativeQuery = true)
    void updateSignalProfitAndLastPNL(@Param("signalId") Long signalId,
                                      @Param("profitLoss") Long profitLoss,
                                      @Param("lastPNL") String lastPNL);


    @Modifying
    @Transactional
    @Query(value = "UPDATE signal.signal SET status = :status, updated_at = CURRENT_TIMESTAMP WHERE id = :id", nativeQuery = true)
    void updateSignalStatus(@Param("id") Long id, @Param("status") String status);

    @Query(value = "SELECT id, strategy_id, profit_loss, user_id, base_index_price, latest_index_price, strategy_addition_id, position_type, execution_type" +
            " FROM signal.signal " +
            "WHERE status = :status " +
            "AND ((position_type = :intraDay AND DATE(created_at) = CURRENT_DATE) " +
            "OR position_type = :positional)", nativeQuery = true)
    List<Object[]> findSignalDetails(@Param("positional") String positional,
                                     @Param("intraDay") String intraDay,
                                     @Param("status") String status);

    @Query(value = """
    SELECT id
    FROM signal.signal
    WHERE status = :status
      AND (
          (position_type = :intraDay
           AND created_at >= CURRENT_DATE
           AND created_at < CURRENT_DATE + INTERVAL '1 day')
          OR position_type = :positional
      )
    """, nativeQuery = true)
    Set<Long> findLiveSignalId(@Param("positional") String positional,
                               @Param("intraDay") String intraDay,
                               @Param("status") String status);



    @Modifying
    @Transactional
    @Query(value = """
       UPDATE signal.signal
       SET status = :newStatus
       WHERE position_type = :positionType
         AND status IN (:statuses)
       """, nativeQuery = true)
    int bulkUpdateStatus(
            @Param("newStatus") String newStatus,
            @Param("positionType") String positionType,
            @Param("statuses") List<String> statuses
    );


    @Query(value = """
    SELECT *
    FROM signal.signal s
    WHERE s.strategy_id = :strategyId
      AND (
            s.status = :status
            OR (s.created_at >= CURRENT_DATE AND s.created_at < CURRENT_DATE + INTERVAL '1 day')
            OR (
                s.position_type = 'Positional'
                AND s.updated_at >= CURRENT_DATE
                AND s.updated_at < CURRENT_DATE + INTERVAL '1 day'
            )
          )
    ORDER BY s.id DESC
    """, nativeQuery = true)
    List<Signal> findTodayAndLiveSignalsByStrategy(
            @Param("strategyId") Long strategyId,
            @Param("status") String status);

    @Query(value = """
    SELECT *
    FROM signal.signal s
    WHERE s.status = :status
      AND s.last_pnl != :lastPnl
      AND s.id > :lastId
        AND s.execution_type = :executionType
    ORDER BY s.id ASC
    LIMIT :limit
    """, nativeQuery = true)
    List<Signal> fetchSignalsBatchAfterId(@Param("status") String status,
                                          @Param("lastPnl") String lastPnl,
                                          @Param("lastId") Long lastId,
                                          @Param("limit") int limit,
                                          @Param("executionType") String executionType);

    @Query(value = "SELECT s.id FROM signal.signal s WHERE s.strategy_id = :strategyId ORDER BY s.created_at DESC LIMIT 1", nativeQuery = true)
    Long findTopIdByStrategyIdOrderByCreatedAtDesc(@Param("strategyId") Long strategyId);

    @Query("SELECT new com.quantlab.common.dao.DeltaNeutralSignalDao(s.id, sa.entryUnderlingPrice) FROM Signal s JOIN s.signalAdditions sa WHERE s.id = :id")
    Optional<com.quantlab.common.dao.DeltaNeutralSignalDao> findDeltaNeutralSignalById(@Param("id") Long id);

    @Query(value = "SELECT s.id FROM signal.signal s WHERE s.strategy_id = :strategyId AND status = :status ORDER BY s.created_at DESC LIMIT 1", nativeQuery = true)
    Long findTopIdByStrategyIdAndStatusOrderByCreatedAtDesc(@Param("strategyId") Long strategyId, @Param("status") String status);

    @Query("SELECT new com.quantlab.common.dao.PhoenixSignalDto(s.id, s.baseIndexPrice, sa.currentAtm) FROM Signal s JOIN s.signalAdditions sa WHERE s.id = :id")
    Optional<com.quantlab.common.dao.PhoenixSignalDto> findPhoenixSignalDtoByIdForUpdate(@Param("id") Long id);


    @Query("""
    SELECT s.id
    FROM Signal s
    WHERE s.strategy.id = :strategyId
      AND s.status = :status
    ORDER BY s.createdAt DESC
""")
    Long findLatestSignalId(Long strategyId, String status);

    Optional<Signal> findFirstByStatusAndStrategy_idOrderByCreatedAtDesc(String status, Long strategyId);

    @Query(value = "SELECT s.id, s.profit_loss, s.created_at, s.multiplier, COALESCE(COUNT(sl.id),0) AS leg_count " +
            "FROM signal.signal s LEFT JOIN signal.strategy_leg sl ON sl.signal_id = s.id " +
            "WHERE s.strategy_id = :strategyId AND s.profit_loss IS NOT NULL AND s.status = 'exit' AND s.execution_type = :executionType " +
            "GROUP BY s.id, s.profit_loss, s.created_at, s.multiplier " +
            "ORDER BY s.created_at ASC", nativeQuery = true)
    List<Object[]> findSignalStatsByStrategyId(@Param("strategyId") Long strategyId, @Param("executionType") String executionType);


    @Query("""
    SELECT s.signalAdditions.id
    FROM Signal s
    WHERE s.strategy.id = :strategyId
      AND s.status = :status
    ORDER BY s.createdAt DESC
""")
    Long findLatestSignalId_SignalAdditions(Long strategyId, String status);

    @Modifying
    @Transactional
    @Query("UPDATE Signal s SET s.status = :status WHERE s.id = :id")
    int updateStatusById(@Param("id") Long id,
                         @Param("status") String status);


    @Query("SELECT MAX(s.createdAt) FROM Signal s WHERE s.strategy.id = :strategyId")
    Instant findLastCreatedAtByStrategyId(@Param("strategyId") Long strategyId);

}
