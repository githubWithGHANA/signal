package com.quantlab.signal.strategy;

import com.quantlab.common.entity.Signal;
import com.quantlab.common.entity.Strategy;

import java.util.List;

public interface StrategiesImplementation<U> {
    Signal runStrategy(Strategy strategy);
    void exitStrategy(Strategy strategy);
    void check(Strategy strategy);
}
