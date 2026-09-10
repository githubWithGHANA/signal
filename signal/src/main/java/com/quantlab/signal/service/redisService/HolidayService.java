package com.quantlab.signal.service.redisService;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.quantlab.signal.dto.redisDto.HolidayResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

import static com.quantlab.common.utils.staticstore.AppConstants.HOLIDAY_REDIS_KEY;

@Service
public class HolidayService {

    private final RedisTemplate<String, Object> redisTemplateForKeys;
    private final ObjectMapper objectMapper;

    @Autowired
    public HolidayService(RedisTemplate<String, Object> redisTemplateForKeys, ObjectMapper objectMapper) {
        this.redisTemplateForKeys = redisTemplateForKeys;
        this.objectMapper = objectMapper;
    }

    public HolidayResponse getHolidayResponse(String key) {
        Object value = redisTemplateForKeys.opsForValue().get(key);
        if (value == null) {
            return null;
        }

        try {
            return objectMapper.readValue(value.toString(), HolidayResponse.class);
        } catch (Exception e) {
            throw new RuntimeException("Failed to deserialize HOLIDAY key into HolidayResponse", e);
        }
    }

    public boolean isTodayAHoliday() {
        return isHoliday(LocalDate.now());
    }

    public boolean isTodayWorkingWeekend() {
        return isWorkingWeekend(LocalDate.now());
    }

    public boolean isHoliday(LocalDate date) {
        HolidayResponse holidayResponse = getHolidayResponse(HOLIDAY_REDIS_KEY);
        if (holidayResponse == null || holidayResponse.getResult() == null) {
            return false;
        }
        String formattedDate = date.format(DateTimeFormatter.ofPattern("ddMMMyyyy", Locale.ENGLISH));

        return holidayResponse.getResult().entrySet().stream()
                .filter(entry -> entry.getKey().startsWith("NSEFO") || entry.getKey().startsWith("BSEFO"))
                .flatMap(entry -> entry.getValue().getHoliday().stream())
                .anyMatch(day -> day.equalsIgnoreCase(formattedDate));
    }

    public boolean isWorkingWeekend(LocalDate date) {
        DayOfWeek day = date.getDayOfWeek();
        if (day != DayOfWeek.SATURDAY && day != DayOfWeek.SUNDAY) {
            return false;
        }

        HolidayResponse holidayResponse = getHolidayResponse(HOLIDAY_REDIS_KEY);
        if (holidayResponse == null || holidayResponse.getResult() == null) {
            return false;
        }
        String formattedDate = date.format(DateTimeFormatter.ofPattern("ddMMMyyyy", Locale.ENGLISH));

        return holidayResponse.getResult().entrySet().stream()
                .filter(entry -> entry.getKey().startsWith("NSE") || entry.getKey().startsWith("BSE"))
                .flatMap(entry -> entry.getValue().getWorking().stream())
                .anyMatch(dayStr -> dayStr.equalsIgnoreCase(formattedDate));
    }

    public boolean isBusinessDay(LocalDate date) {
        DayOfWeek day = date.getDayOfWeek();
        boolean isWeekend = day == DayOfWeek.SATURDAY || day == DayOfWeek.SUNDAY;
        if (isWeekend) {
            return isWorkingWeekend(date);
        }
        return !isHoliday(date);
    }
}
