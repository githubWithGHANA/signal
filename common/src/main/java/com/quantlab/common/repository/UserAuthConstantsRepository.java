package com.quantlab.common.repository;

import com.quantlab.common.dto.ProfitLossStatsDao;
import com.quantlab.common.entity.UserAuthConstants;
import jakarta.transaction.Transactional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UserAuthConstantsRepository extends JpaRepository<UserAuthConstants, Long> {

    Optional<UserAuthConstants> findByClientId(String clientId);

    UserAuthConstants findByAppUserUserId(String id);

    @Modifying
    @Transactional
    @Query("UPDATE UserAuthConstants u SET " +
            "u.userTradingMode = :newType, " +
            "u.xtsToken = NULL " +
            "WHERE (u.userTradingMode != :newType OR u.userTradingMode IS NULL) " +
            "AND (u.previousLoggedinTime IS NULL OR FUNCTION('DATE', u.previousLoggedinTime) != CURRENT_DATE)")
    int updateUserTradingModeAndFlushXtsTokens(@Param("newType") String newType);


    @Query("""
    SELECT new com.quantlab.common.dto.ProfitLossStatsDao(
        u.id,
        MIN(c.minProfit),
        MAX(c.maxLoss),
        c.clientId
    )
    FROM UserAuthConstants c
    JOIN AppUser u ON c.appUser.id = u.id
    GROUP BY c.clientId, u.id
""")
    List<ProfitLossStatsDao> getProfitLossStats();
}
