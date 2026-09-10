package com.quantlab.common.utils.staticstore.dropdownutils;

import lombok.Getter;

@Getter
public enum Status {
    STANDBY("standby", "Standby"),
    ACTIVE("active", "Active"),
    PRE_ACTIVE("pre-active", "Pre-Active"),
    INACTIVE("available", "Available"),
    LIVE("live", "Live"),
    ERROR("error", "Error"),
    ERROR_PLACING_ORDER("errorPlacingOrder", "Error Placing Order"),
    FAILURE("failure", "Failure"),
    RMS_ERROR("rms-error","RMS Error"),
    PENDING("pending","Pending"),
    EXIT_PENDING("exit-pending","Exit-Pending"),
    PLACED("placed","Placed"),
    PARTIALLY_FILLED("partially-filled","Partially-Filled"),
    PAUSED("paused","Paused"),
    RETRYING("retrying","Retrying"),
    CANCELLED("cancelled","Cancelled"),
    DELETED("deleted", "Deleted"),
    EXIT("exit","Exit");


    private final String key;
    private final String label;

    Status(String key, String label) {
        this.key = key;
        this.label = label;
    }
}