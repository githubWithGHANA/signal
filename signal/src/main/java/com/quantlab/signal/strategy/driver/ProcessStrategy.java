package com.quantlab.signal.strategy.driver;

import com.quantlab.common.entity.Strategy;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;


public class ProcessStrategy implements Runnable {

    private Strategy strategy  ;
    private final Parser parser;
    public ProcessStrategy(Parser parser, Strategy strategy) {
        this.parser = parser;
        this.strategy = strategy;
    }

    @Override
    public void run() {
                parser.execute(strategy);
        }

    public void setStrategy(Strategy strategy) {
        this.strategy=strategy;
    }

}
