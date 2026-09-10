package com.quantlab.signal.strategy.driver;

import com.quantlab.common.entity.Strategy;
import com.quantlab.signal.strategy.StrategiesImplementation;
import com.quantlab.signal.utils.StrategyMapper;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class StrategyParser implements  Parser {

  private static final Logger logger = LoggerFactory.getLogger(StrategyParser.class);

  Map<String,String> StrategyMap = new HashMap<String,String>();

  @Autowired
  private ApplicationContext applicationContext;

  private final ConcurrentHashMap<Long, Object> strategyLocks = new ConcurrentHashMap<>();

  /** Track unknown tags already warned about to avoid log flooding */
  private final Set<String> warnedUnknownTags = ConcurrentHashMap.newKeySet();

  @PostConstruct
  public void init() {
    Arrays.stream(StrategyMapper.values())
            .forEach(type -> StrategyMap.put(type.getKey(), type.getLabel()));

  }

  @Override
  public void execute(Strategy strategy) {
    try {
      String nameOfTheBean = StrategyMap.get(strategy.getStrategyTag().toUpperCase());
      if (nameOfTheBean == null) {
        logUnknownTag(strategy, "execute");
        return;
      }
      StrategiesImplementation strategiesImplementation =  applicationContext.getBean(nameOfTheBean,StrategiesImplementation.class);
      strategiesImplementation.check(strategy);

    }catch (Exception e) {
//      e.printStackTrace();
      throw new RuntimeException(e.getMessage());
    }
  }



  @Override
  public void runStrategy(Strategy strategy) {
    try {

      String nameOfTheBean = StrategyMap.get(strategy.getStrategyTag().toUpperCase());
      if (nameOfTheBean == null) {
        logUnknownTag(strategy, "runStrategy");
        return;
      }
      StrategiesImplementation strategiesImplementation =  applicationContext.getBean(nameOfTheBean,StrategiesImplementation.class);
      strategiesImplementation.runStrategy(strategy);

    }catch (Exception e) {
//      e.printStackTrace();
      throw new RuntimeException(e);
    }
  }

  @Override
  public void exit(Strategy strategy) {
    try {

      String nameOfTheBean = StrategyMap.get(strategy.getStrategyTag().toUpperCase());
      if (nameOfTheBean == null) {
        logUnknownTag(strategy, "exit");
        return;
      }
      StrategiesImplementation strategiesImplementation =  applicationContext.getBean(nameOfTheBean,StrategiesImplementation.class);
      strategiesImplementation.exitStrategy(strategy);

    }catch (Exception e) {
      System.err.println(e.getMessage());
    }
  }

  @Override
  public void check(Strategy strategy) {
    Object lock = strategyLocks.computeIfAbsent(strategy.getId(), k -> new Object());
    synchronized (lock) {
      try {
        String nameOfTheBean = StrategyMap.get(strategy.getStrategyTag().toUpperCase());
        if (nameOfTheBean == null) {
          logUnknownTag(strategy, "check");
          return;
        }
        StrategiesImplementation strategiesImplementation =  applicationContext.getBean(nameOfTheBean,StrategiesImplementation.class);
        strategiesImplementation.check(strategy);
      } finally {
        strategyLocks.remove(strategy.getId());
      }
    }
  }

  /** Logs unknown strategy_tag once per tag to avoid flooding (1248 errors/day → 1 warning) */
  private void logUnknownTag(Strategy strategy, String method) {
    String tag = strategy.getStrategyTag();
    if (warnedUnknownTags.add(tag)) {
      logger.warn("[StrategyParser] Unknown strategy_tag '{}' — no mapping in StrategyMapper. " +
                      "strategyId={}, method={}. Skipping. Add mapping to StrategyMapper to resolve.",
              tag, strategy.getId(), method);
    }
  }

}