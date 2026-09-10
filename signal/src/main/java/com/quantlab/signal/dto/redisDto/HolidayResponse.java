package com.quantlab.signal.dto.redisDto;

import lombok.Data;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Data
public class HolidayResponse {
    private String type;
    private String code;
    private String description;
    private Map<String, HolidayMarketData> result;


    public Map<String, List<String>> getNseAndBseHolidaysOnly() {
        if (result == null) return Collections.emptyMap();

        return result.entrySet().stream()
                .filter(entry -> entry.getKey().startsWith("NSE") || entry.getKey().startsWith("BSE"))
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        entry -> Optional.ofNullable(entry.getValue().getHoliday()).orElse(Collections.emptyList())
                ));
    }

    public List<String> getNseAndBseWorkingWeekends() {
        if (result == null) return Collections.emptyList();

        return result.entrySet().stream()
                .filter(entry -> entry.getKey().startsWith("NSE") || entry.getKey().startsWith("BSE"))
                .flatMap(entry -> Optional.ofNullable(entry.getValue().getWorking()).orElse(Collections.emptyList()).stream())
                .distinct()
                .collect(Collectors.toList());
    }
}
