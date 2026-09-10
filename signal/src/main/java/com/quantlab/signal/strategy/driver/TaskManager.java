package com.quantlab.signal.strategy.driver;


import com.quantlab.signal.strategy.DeltaNeutralStrategyHedge;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadPoolExecutor;

@Service
public class TaskManager {

    private static final Logger logger = LoggerFactory.getLogger(TaskManager.class);

    private int availableProcessors = Runtime.getRuntime().availableProcessors()*2;
    private int poolSize = (int) Math.ceil(availableProcessors * 0.6);

    private ExecutorService es;

    @PostConstruct
    public void init() {
        logger.info("Initializing TaskManager :- the pool size is: " + poolSize);
        if (es == null) {
            es = Executors.newFixedThreadPool(poolSize);
        }
    }

    public void submitTask(Runnable task) throws IllegalStateException {
        if (es == null)
            throw new IllegalStateException("Worker provider not configured properly");
        es.execute(task);
    }

    /**
     * Returns running thread count from the executors
     *
     * @return Active thread count
     */
    public int getRunningThread() {
        if (es == null)
            return -1;
        return ((ThreadPoolExecutor) es).getActiveCount();
    }
}
