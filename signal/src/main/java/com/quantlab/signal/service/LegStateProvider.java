package com.quantlab.signal.service;

import com.quantlab.common.entity.StrategyLeg;
import com.quantlab.common.repository.StrategyLegRepository;
import com.quantlab.common.repository.StrategyRepository;
import com.quantlab.signal.dto.redisDto.RedisLegState;
import com.quantlab.signal.service.redisService.RedisLegService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;

import static com.quantlab.common.utils.staticstore.AppConstants.AMOUNT_MULTIPLIER;

@Component
public class LegStateProvider {

    private static final Logger logger = LoggerFactory.getLogger(LegStateProvider.class);

    @Autowired
    private RedisLegService redisLegService;

    @Autowired
    private StrategyLegRepository strategyLegRepository;

    @Autowired
    StrategyRepository strategyRepository;

    @Transactional
    public RedisLegState getLegState(Long strategyId, Long legId) {
        try {
            RedisLegState s = redisLegService.readLegState(strategyId, legId);
            if (s != null) return s;

            // fallback to DB
            StrategyLeg leg = strategyLegRepository.findById(legId).orElse(null);
            if (leg == null) return null;
            String underlying = leg.getStrategy().getUnderlying().getName().toUpperCase(Locale.ROOT);
            RedisLegState dbState = dbToRedisState(leg);
            // optionally write back to redis for future speed
            redisLegService.writeLegState(dbState, underlying);
            return dbState;
        } catch (Exception e) {
            logger.error("Error getting leg state for {}:{}", strategyId, legId, e);
            return null;
        }
    }

    private RedisLegState dbToRedisState(StrategyLeg leg) {
        RedisLegState s = new RedisLegState();
        s.setStrategyId(leg.getStrategy().getId());
        s.setLegId(leg.getId());
        if (leg.getExecutedPrice() != null) s.setExecutedPrice(leg.getExecutedPrice() / (double) AMOUNT_MULTIPLIER);
        s.setEntryTimestamp(
                leg.getCreatedAt() == null
                        ? System.currentTimeMillis()
                        : leg.getCreatedAt().toEpochMilli()
        );

        s.setTargetPct(leg.getTargetUnitValue() == null ? 0.0 : leg.getTargetUnitValue() / (double) AMOUNT_MULTIPLIER);
        s.setStopLossPct(leg.getStopLossUnitValue() == null ? 0.0 : leg.getStopLossUnitValue() / (double) AMOUNT_MULTIPLIER);
        s.setTslActive(Boolean.TRUE.equals(leg.getTslActivated()));
        s.setTslPoints(leg.getTrailingStopLossPoints() == null ? null :
                leg.getTrailingStopLossPoints() / (double) AMOUNT_MULTIPLIER);
        s.setTslAnchor(leg.getTslAnchorPrice() == null ? null :
                leg.getTslAnchorPrice() / (double) AMOUNT_MULTIPLIER);
        s.setTrailingDistance(leg.getTrailingDistance() == null ? 5.0 : leg.getTrailingDistance());
        s.setLastLtp(0.0);
        s.setStatus(leg.getStatus());
        s.setUpdatedAt(System.currentTimeMillis());
        return s;
    }
}
