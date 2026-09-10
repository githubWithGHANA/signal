package com.quantlab.signal.service;

import java.time.Instant;

public record TradingDayRuntimeCleanupEvent(Instant cleanedAt) {
}
