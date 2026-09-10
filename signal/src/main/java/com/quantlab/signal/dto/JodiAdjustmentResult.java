package com.quantlab.signal.dto;

import lombok.Data;
import lombok.Getter;

@Data
public class JodiAdjustmentResult {
    private boolean shouldAdjust;
    private int newStrike;
    private String expiry;
    private double newPremium;

    public JodiAdjustmentResult(boolean shouldAdjust) {
        this.shouldAdjust = shouldAdjust;
    }

    public JodiAdjustmentResult(boolean shouldAdjust, int newStrike, String expiry, double newPremium) {
        this.shouldAdjust = shouldAdjust;
        this.newStrike = newStrike;
        this.expiry = expiry;
        this.newPremium = newPremium;
    }

    public boolean shouldAdjust() {
        return shouldAdjust;
    }
}
