package com.quantlab.signal.strategy.driver;

import com.quantlab.common.entity.Strategy;


public interface Parser {
    void execute(Strategy strategy);
    void check(Strategy strategy);
    void runStrategy(Strategy strategy);
    void exit(Strategy strategy);
}
