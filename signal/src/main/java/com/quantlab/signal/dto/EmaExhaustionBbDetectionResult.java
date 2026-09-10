package com.quantlab.signal.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonPOJOBuilder;

/**
 * Detection result from EMA Exhaustion – Bollinger Reversal Sell screener.
 *
 * Produced by the market feed engine (separate service) and pushed to Redis
 * list at key "EMA_EXHAUSTION_BB:RESULTS".
 *
 * Consumed by EmaExhaustionBollingerSellStrategy for breakdown monitoring
 * and equity sell signal creation.
 *
 * Strategy Logic:
 * - Price overextended above 13 EMA (13+ candles OR 8%+)
 * - Signal candle closes inside upper BB with no touch (regular + Heikin Ashi)
 * - Sell entry on breakdown of signal candle low (within 3 candles)
 * - SL = highest high of 5-candle lookback
 * - T1 = middle BB, T2 = lower BB, Trail until close above middle BB
 */
@JsonDeserialize(builder = EmaExhaustionBbDetectionResult.Builder.class)
@JsonIgnoreProperties(ignoreUnknown = true)
public class EmaExhaustionBbDetectionResult {

    private final String symbol;
    private final double signalCandleLow;
    private final double signalCandleHigh;
    private final double signalCandleClose;
    private final long signalCandleEpochTime;

    // Stop loss
    private final double highestHighLast5Candles;

    // Bollinger Bands (initial values at scan time)
    private final double middleBollingerBand;
    private final double lowerBollingerBand;
    private final double upperBollingerBand;

    // EMA state
    private final double ema13Value;
    private final int consecutiveCandlesAboveEma;
    private final double percentAboveEma;

    // Market data
    private final double currentPrice;

    // Entry validity
    private final int validityRemainingCandles;

    // Timeframe
    private final String timeframe;

    // Cache validation
    private final long ttlExpiryMillis;

    private EmaExhaustionBbDetectionResult(Builder builder) {
        this.symbol = builder.symbol;
        this.signalCandleLow = builder.signalCandleLow;
        this.signalCandleHigh = builder.signalCandleHigh;
        this.signalCandleClose = builder.signalCandleClose;
        this.signalCandleEpochTime = builder.signalCandleEpochTime;
        this.highestHighLast5Candles = builder.highestHighLast5Candles;
        this.middleBollingerBand = builder.middleBollingerBand;
        this.lowerBollingerBand = builder.lowerBollingerBand;
        this.upperBollingerBand = builder.upperBollingerBand;
        this.ema13Value = builder.ema13Value;
        this.consecutiveCandlesAboveEma = builder.consecutiveCandlesAboveEma;
        this.percentAboveEma = builder.percentAboveEma;
        this.currentPrice = builder.currentPrice;
        this.validityRemainingCandles = builder.validityRemainingCandles;
        this.timeframe = builder.timeframe;
        this.ttlExpiryMillis = builder.ttlExpiryMillis;
    }

    @JsonIgnore
    public boolean isValid() {
        return System.currentTimeMillis() < ttlExpiryMillis;
    }

    @JsonIgnore
    public boolean isBreakdownCandidate() {
        return validityRemainingCandles > 0;
    }

    // Getters
    public String getSymbol() { return symbol; }
    public double getSignalCandleLow() { return signalCandleLow; }
    public double getSignalCandleHigh() { return signalCandleHigh; }
    public double getSignalCandleClose() { return signalCandleClose; }
    public long getSignalCandleEpochTime() { return signalCandleEpochTime; }
    public double getHighestHighLast5Candles() { return highestHighLast5Candles; }
    public double getMiddleBollingerBand() { return middleBollingerBand; }
    public double getLowerBollingerBand() { return lowerBollingerBand; }
    public double getUpperBollingerBand() { return upperBollingerBand; }
    public double getEma13Value() { return ema13Value; }
    public int getConsecutiveCandlesAboveEma() { return consecutiveCandlesAboveEma; }
    public double getPercentAboveEma() { return percentAboveEma; }
    public double getCurrentPrice() { return currentPrice; }
    public int getValidityRemainingCandles() { return validityRemainingCandles; }
    public String getTimeframe() { return timeframe; }
    public long getTtlExpiryMillis() { return ttlExpiryMillis; }

    public static Builder builder() {
        return new Builder();
    }

    @JsonPOJOBuilder(withPrefix = "")
    public static class Builder {
        private String symbol;
        private double signalCandleLow;
        private double signalCandleHigh;
        private double signalCandleClose;
        private long signalCandleEpochTime;
        private double highestHighLast5Candles;
        private double middleBollingerBand;
        private double lowerBollingerBand;
        private double upperBollingerBand;
        private double ema13Value;
        private int consecutiveCandlesAboveEma;
        private double percentAboveEma;
        private double currentPrice;
        private int validityRemainingCandles;
        private String timeframe;
        private long ttlExpiryMillis;

        public Builder symbol(String symbol) { this.symbol = symbol; return this; }
        public Builder signalCandleLow(double signalCandleLow) { this.signalCandleLow = signalCandleLow; return this; }
        public Builder signalCandleHigh(double signalCandleHigh) { this.signalCandleHigh = signalCandleHigh; return this; }
        public Builder signalCandleClose(double signalCandleClose) { this.signalCandleClose = signalCandleClose; return this; }
        public Builder signalCandleEpochTime(long signalCandleEpochTime) { this.signalCandleEpochTime = signalCandleEpochTime; return this; }
        public Builder highestHighLast5Candles(double highestHighLast5Candles) { this.highestHighLast5Candles = highestHighLast5Candles; return this; }
        public Builder middleBollingerBand(double middleBollingerBand) { this.middleBollingerBand = middleBollingerBand; return this; }
        public Builder lowerBollingerBand(double lowerBollingerBand) { this.lowerBollingerBand = lowerBollingerBand; return this; }
        public Builder upperBollingerBand(double upperBollingerBand) { this.upperBollingerBand = upperBollingerBand; return this; }
        public Builder ema13Value(double ema13Value) { this.ema13Value = ema13Value; return this; }
        public Builder consecutiveCandlesAboveEma(int consecutiveCandlesAboveEma) { this.consecutiveCandlesAboveEma = consecutiveCandlesAboveEma; return this; }
        public Builder percentAboveEma(double percentAboveEma) { this.percentAboveEma = percentAboveEma; return this; }
        public Builder currentPrice(double currentPrice) { this.currentPrice = currentPrice; return this; }
        public Builder validityRemainingCandles(int validityRemainingCandles) { this.validityRemainingCandles = validityRemainingCandles; return this; }
        public Builder timeframe(String timeframe) { this.timeframe = timeframe; return this; }
        public Builder ttlExpiryMillis(long ttlExpiryMillis) { this.ttlExpiryMillis = ttlExpiryMillis; return this; }

        public EmaExhaustionBbDetectionResult build() {
            return new EmaExhaustionBbDetectionResult(this);
        }
    }

    @Override
    public String toString() {
        return "EmaExhaustionBbDetectionResult{" +
                "symbol='" + symbol + '\'' +
                ", signalCandleLow=" + String.format("%.2f", signalCandleLow) +
                ", highestHighLast5=" + String.format("%.2f", highestHighLast5Candles) +
                ", middleBB=" + String.format("%.2f", middleBollingerBand) +
                ", lowerBB=" + String.format("%.2f", lowerBollingerBand) +
                ", ema13=" + String.format("%.2f", ema13Value) +
                ", candlesAboveEma=" + consecutiveCandlesAboveEma +
                ", pctAboveEma=" + String.format("%.2f", percentAboveEma) +
                ", validity=" + validityRemainingCandles +
                ", timeframe='" + timeframe + '\'' +
                '}';
    }
}