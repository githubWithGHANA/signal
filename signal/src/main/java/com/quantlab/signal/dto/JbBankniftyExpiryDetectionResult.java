package com.quantlab.signal.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonPOJOBuilder;

import java.time.Instant;

/**
 * Holds the cached result of JB Banknifty Expiry Special detection.
 *
 * Computed once per 15-min candle boundary by the engine service:
 * - RSI of ATM Straddle combined premium (CE + PE)
 * - Supertrend (10,3) on 15-min straddle price chart
 *
 * Cached in Redis and consumed by all strategy instances.
 */
@JsonDeserialize(builder = JbBankniftyExpiryDetectionResult.Builder.class)
public class JbBankniftyExpiryDetectionResult {

    private final String underlying;
    private final Instant detectionTime;
    private final long candleEpochTime;

    // RSI detection
    private final boolean rsiSignalTriggered;  // RSI < 30
    private final double rsiValue;

    // Supertrend state
    private final boolean priceAboveSupertrend;  // true = violation (need hedge)
    private final double supertrendValue;
    private final double straddelClosePrice;     // combined CE+PE close price

    // ATM strike info
    private final Integer atmStrike;
    private final double cePremium;
    private final double pePremium;

    // For hedge calculation
    private final double currentMarketPrice;     // spot/synthetic price

    // Cache validation
    private final long ttlExpiryMillis;

    private JbBankniftyExpiryDetectionResult(Builder builder) {
        this.underlying = builder.underlying;
        this.detectionTime = builder.detectionTime;
        this.candleEpochTime = builder.candleEpochTime;
        this.rsiSignalTriggered = builder.rsiSignalTriggered;
        this.rsiValue = builder.rsiValue;
        this.priceAboveSupertrend = builder.priceAboveSupertrend;
        this.supertrendValue = builder.supertrendValue;
        this.straddelClosePrice = builder.straddelClosePrice;
        this.atmStrike = builder.atmStrike;
        this.cePremium = builder.cePremium;
        this.pePremium = builder.pePremium;
        this.currentMarketPrice = builder.currentMarketPrice;
        this.ttlExpiryMillis = builder.ttlExpiryMillis;
    }

    @JsonIgnore
    public boolean isValid() {
        return System.currentTimeMillis() < ttlExpiryMillis;
    }

    @JsonIgnore
    public boolean shouldTriggerEntry() {
        return rsiSignalTriggered;
    }

    @JsonIgnore
    public boolean isSuperTrendViolated() {
        return priceAboveSupertrend;
    }

    // Getters
    public String getUnderlying() { return underlying; }
    public Instant getDetectionTime() { return detectionTime; }
    public long getCandleEpochTime() { return candleEpochTime; }
    public boolean isRsiSignalTriggered() { return rsiSignalTriggered; }
    public double getRsiValue() { return rsiValue; }
    public boolean isPriceAboveSupertrend() { return priceAboveSupertrend; }
    public double getSupertrendValue() { return supertrendValue; }
    public double getStraddelClosePrice() { return straddelClosePrice; }
    public Integer getAtmStrike() { return atmStrike; }
    public double getCePremium() { return cePremium; }
    public double getPePremium() { return pePremium; }
    public double getCurrentMarketPrice() { return currentMarketPrice; }
    public long getTtlExpiryMillis() { return ttlExpiryMillis; }

    public static Builder builder() {
        return new Builder();
    }

    @JsonPOJOBuilder(withPrefix = "")
    public static class Builder {
        private String underlying;
        private Instant detectionTime;
        private long candleEpochTime;
        private boolean rsiSignalTriggered;
        private double rsiValue;
        private boolean priceAboveSupertrend;
        private double supertrendValue;
        private double straddelClosePrice;
        private Integer atmStrike;
        private double cePremium;
        private double pePremium;
        private double currentMarketPrice;
        private long ttlExpiryMillis;

        public Builder underlying(String underlying) { this.underlying = underlying; return this; }
        public Builder detectionTime(Instant detectionTime) { this.detectionTime = detectionTime; return this; }
        public Builder candleEpochTime(long candleEpochTime) { this.candleEpochTime = candleEpochTime; return this; }
        public Builder rsiSignalTriggered(boolean rsiSignalTriggered) { this.rsiSignalTriggered = rsiSignalTriggered; return this; }
        public Builder rsiValue(double rsiValue) { this.rsiValue = rsiValue; return this; }
        public Builder priceAboveSupertrend(boolean priceAboveSupertrend) { this.priceAboveSupertrend = priceAboveSupertrend; return this; }
        public Builder supertrendValue(double supertrendValue) { this.supertrendValue = supertrendValue; return this; }
        public Builder straddelClosePrice(double straddelClosePrice) { this.straddelClosePrice = straddelClosePrice; return this; }
        public Builder atmStrike(Integer atmStrike) { this.atmStrike = atmStrike; return this; }
        public Builder cePremium(double cePremium) { this.cePremium = cePremium; return this; }
        public Builder pePremium(double pePremium) { this.pePremium = pePremium; return this; }
        public Builder currentMarketPrice(double currentMarketPrice) { this.currentMarketPrice = currentMarketPrice; return this; }
        public Builder ttlExpiryMillis(long ttlExpiryMillis) { this.ttlExpiryMillis = ttlExpiryMillis; return this; }

        public JbBankniftyExpiryDetectionResult build() {
            return new JbBankniftyExpiryDetectionResult(this);
        }
    }

    @Override
    public String toString() {
        return "JbBankniftyExpiryDetectionResult{" +
                "underlying='" + underlying + '\'' +
                ", rsiSignalTriggered=" + rsiSignalTriggered +
                ", rsiValue=" + String.format("%.2f", rsiValue) +
                ", priceAboveSupertrend=" + priceAboveSupertrend +
                ", supertrendValue=" + String.format("%.2f", supertrendValue) +
                ", atmStrike=" + atmStrike +
                ", cePremium=" + String.format("%.2f", cePremium) +
                ", pePremium=" + String.format("%.2f", pePremium) +
                '}';
    }
}