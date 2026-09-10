package com.quantlab.signal.dto.redisDto;

import lombok.Data;

import java.util.List;

@Data
public class HolidayMarketData {
    private List<String> holiday;
    private List<String> working;
}