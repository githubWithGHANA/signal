package com.quantlab.signal.utils;

import com.quantlab.signal.utils.excel.ExcelExporter;
import org.apache.commons.math3.distribution.NormalDistribution;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.stereotype.Component;

@Component
public class Delta {

    private static final Logger logger = LogManager.getLogger(Delta.class);
    private double N(double d1) {
        NormalDistribution normalDistribution = new NormalDistribution();
        return normalDistribution.cumulativeProbability(d1);
    }

    private double calculateD1(double future, double strike, double riskFreeInterest, double sigma, double timeToExpiry) {
        double sigmaSquared = sigma * sigma;
        double numerator = Math.log(future / strike) + (riskFreeInterest + sigmaSquared / 2.0) * timeToExpiry;
        double denominator = sigma * Math.sqrt(timeToExpiry);

        return numerator / denominator;
    }

    /**
     *
     * @param future future price of the underling
     * @param strike strike price
     * @param timeToExpiry time to expiration in years
     * @param iv implied volatility
     * @param riskFreeInterest annual risk-free interest rate
     * @param optionType option type call or put
     * @return delta value
     */

    public double calculateDelta(double future, int strike, double timeToExpiry, double iv, double riskFreeInterest, char optionType) {
        double d1 =calculateD1(future, strike,riskFreeInterest,iv, timeToExpiry);
        logger.info("d1: " + d1);
        logger.info("Nd1: " + N(d1));
        if (optionType =='p'){
            return  N(d1)-1.0;
        }else {
            return N(d1);
        }
    }
}
