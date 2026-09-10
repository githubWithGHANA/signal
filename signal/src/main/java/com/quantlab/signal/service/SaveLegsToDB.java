package com.quantlab.signal.service;

import com.quantlab.common.entity.StrategyLeg;
import com.quantlab.common.repository.StrategyLegRepository;
import com.quantlab.signal.strategy.SignalService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.ConcurrencyFailureException;
import org.springframework.dao.DeadlockLoserDataAccessException;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.EnableRetry;
import org.springframework.retry.annotation.Recover;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@EnableRetry
@Service
public class SaveLegsToDB {

    private static final Logger logger = LoggerFactory.getLogger(SaveLegsToDB.class);

    @Autowired
    StrategyLegRepository strategyLegRepository;

    @Retryable(
        value = {ConcurrencyFailureException.class, DeadlockLoserDataAccessException.class, java.util.ConcurrentModificationException.class},
        maxAttempts = 3,
        backoff = @Backoff(delay = 1000)
    )
    @Transactional(propagation = Propagation.MANDATORY)  // Must be called from an existing transaction
    public List<StrategyLeg> saveStrategyLegsWithRetry(List<StrategyLeg> legs) {
        // Defensive snapshot: create a stable copy to avoid ConcurrentModificationException
        List<StrategyLeg> snapshot = (legs == null) ? Collections.emptyList() : new ArrayList<>(legs);
        try {
            return strategyLegRepository.saveAll(snapshot);
        } catch (java.util.ConcurrentModificationException cme) {
            logger.warn("ConcurrentModificationException while saving strategy legs - will be retried", cme);
            // Translate to a retryable exception so Retryable will retry
            throw new ConcurrencyFailureException("Concurrent modification during save", cme);
        } catch (Exception e) {
            logger.error("Error while saving strategy legs", e);
            throw new ConcurrencyFailureException("Failed to save strategy legs", e);
        }
    }

    @Recover
    public List<StrategyLeg> recoverFromDeadlock(Exception ex, List<StrategyLeg> legs) {
        logger.error("Failed to save legs after all retries due to concurrency issue", ex);
        throw new RuntimeException("Failed to save strategy legs after multiple retries", ex);
    }
}
