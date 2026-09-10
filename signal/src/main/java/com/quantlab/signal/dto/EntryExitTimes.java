package com.quantlab.signal.dto;

import lombok.Data;

import java.time.Instant;

@Data
public class EntryExitTimes {
    private int entryHour;
    private int entryMinute;
    private int nowHour ;
    private int nowMinute;
    private Instant exitInstantTime;
    private Instant entryInstantTime;
    private Instant nowInstantTime;
}
