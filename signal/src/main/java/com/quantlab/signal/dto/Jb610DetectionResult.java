package com.quantlab.signal.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonPOJOBuilder;

import java.time.Instant;

/**
 * Cached result of JB610 dual Supertrend crossover detection.
 *
 * Computed once per 30-min candle boundary:
 * - Supertrend (6,1) — fast trigger
 * - Supertrend (10,3) — confirmation filter
 *
 * Crossover signals:
 * - BULLISH: Fast ST crosses above Slow ST → Bull Put Spread
 * - BEARISH: Fast ST crosses below Slow ST → Bear Call Spread
 */
@JsonDeserialize(builder = Jb610DetectionResult.Builder.class)
public class Jb610DetectionResult {

    private final String underlying;
    private final Instant detectionTime;
    private final long candleEpochTime;

    // Crossover detection
    private final boolean crossoverDetected;
    private final String direction;           // "BULLISH" or "BEARISH"

    // Supertrend values (current candle)
    private final double fastSupertrendValue;  // ST(6,1)
    private final double slowSupertrendValue;  // ST(10,3)

    // Supertrend values (previous candle — for crossover confirmation)
    private final double prevFastSupertrendValue;
    private final double prevSlowSupertrendValue;

    // Market data at detection time
    private final double spotPrice;

    // Cache
    private final long ttlExpiryMillis;

    private Jb610DetectionResult(Builder builder) {
        this.underlying = builder.underlying;
        this.detectionTime = builder.detectionTime;
        this.candleEpochTime = builder.candleEpochTime;
        this.crossoverDetected = builder.crossoverDetected;
        this.direction = builder.direction;
        this.fastSupertrendValue = builder.fastSupertrendValue;
        this.slowSupertrendValue = builder.slowSupertrendValue;
        this.prevFastSupertrendValue = builder.prevFastSupertrendValue;
        this.prevSlowSupertrendValue = builder.prevSlowSupertrendValue;
        this.spotPrice = builder.spotPrice;
        this.ttlExpiryMillis = builder.ttlExpiryMillis;
    }

    @JsonIgnore
    public boolean isValid() {
        return System.currentTimeMillis() < ttlExpiryMillis;
    }

    @JsonIgnore
    public boolean shouldTriggerEntry() {
        return crossoverDetected;
    }

    @JsonIgnore
    public boolean isBullish() {
        return "BULLISH".equals(direction);
    }

    @JsonIgnore
    public boolean isBearish() {
        return "BEARISH".equals(direction);
    }

    public String getUnderlying() { return underlying; }
    public Instant getDetectionTime() { return detectionTime; }
    public long getCandleEpochTime() { return candleEpochTime; }
    public boolean isCrossoverDetected() { return crossoverDetected; }
    public String getDirection() { return direction; }
    public double getFastSupertrendValue() { return fastSupertrendValue; }
    public double getSlowSupertrendValue() { return slowSupertrendValue; }
    public double getPrevFastSupertrendValue() { return prevFastSupertrendValue; }
    public double getPrevSlowSupertrendValue() { return prevSlowSupertrendValue; }
    public double getSpotPrice() { return spotPrice; }
    public long getTtlExpiryMillis() { return ttlExpiryMillis; }

    public static Builder builder() {
        return new Builder();
    }

    @JsonPOJOBuilder(withPrefix = "")
    public static class Builder {
        private String underlying;
        private Instant detectionTime;
        private long candleEpochTime;
        private boolean crossoverDetected;
        private String direction;
        private double fastSupertrendValue;
        private double slowSupertrendValue;
        private double prevFastSupertrendValue;
        private double prevSlowSupertrendValue;
        private double spotPrice;
        private long ttlExpiryMillis;

        public Builder underlying(String underlying) { this.underlying = underlying; return this; }
        public Builder detectionTime(Instant detectionTime) { this.detectionTime = detectionTime; return this; }
        public Builder candleEpochTime(long candleEpochTime) { this.candleEpochTime = candleEpochTime; return this; }
        public Builder crossoverDetected(boolean crossoverDetected) { this.crossoverDetected = crossoverDetected; return this; }
        public Builder direction(String direction) { this.direction = direction; return this; }
        public Builder fastSupertrendValue(double fastSupertrendValue) { this.fastSupertrendValue = fastSupertrendValue; return this; }
        public Builder slowSupertrendValue(double slowSupertrendValue) { this.slowSupertrendValue = slowSupertrendValue; return this; }
        public Builder prevFastSupertrendValue(double prevFastSupertrendValue) { this.prevFastSupertrendValue = prevFastSupertrendValue; return this; }
        public Builder prevSlowSupertrendValue(double prevSlowSupertrendValue) { this.prevSlowSupertrendValue = prevSlowSupertrendValue; return this; }
        public Builder spotPrice(double spotPrice) { this.spotPrice = spotPrice; return this; }
        public Builder ttlExpiryMillis(long ttlExpiryMillis) { this.ttlExpiryMillis = ttlExpiryMillis; return this; }

        public Jb610DetectionResult build() {
            return new Jb610DetectionResult(this);
        }
    }

    @Override
    public String toString() {
        return "Jb610DetectionResult{" +
                "underlying='" + underlying + '\'' +
                ", crossoverDetected=" + crossoverDetected +
                ", direction='" + direction + '\'' +
                ", fastST=" + String.format("%.2f", fastSupertrendValue) +
                ", slowST=" + String.format("%.2f", slowSupertrendValue) +
                ", spotPrice=" + String.format("%.2f", spotPrice) +
                '}';
    }
}