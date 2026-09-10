package com.quantlab.signal.utils;

import com.quantlab.common.entity.Strategy;
import com.quantlab.common.utils.staticstore.AppConstants;
import com.quantlab.common.utils.staticstore.dropdownutils.OptionType;
import com.quantlab.signal.dto.ExpiryDatesDTO;
import com.quantlab.signal.service.redisService.HolidayService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.TextStyle;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.quantlab.signal.utils.staticdata.StaticStore.indexExpiryDates;

@Component
public class  CommonUtils {

    private static final Pattern TR_TRADING_SYMBOL_PATTERN = Pattern.compile("^(.+)(\\d{4})-(\\d{2})-(\\d{2})-?(.+)$");

    @Autowired
    private HolidayService holidayService;

    private static final org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(CommonUtils.class);

    public static  Integer adjustToNearestMultipleOf50(double number) {
        // Get the remainder when dividing by 50
        double remainder = number % 50;

        // Check if the remainder is less than 30 or greater/equal to 30
        if (remainder < 30) {
            // Round down to the nearest multiple of 50
            return (int) (Math.floor(number / 50) * 50);
        } else {
            // Round up to the next multiple of 50
            return (int) (Math.ceil(number / 50) * 50);
        }
    }

    public Integer adjustToNearestMultiple(double number, Integer multiple) {
        // Get the remainder when dividing by 50
        double remainder = number % multiple;

        // Check if the remainder is less than 30 or greater/equal to 30
        if (remainder < (multiple/2)) {
            // Round down to the nearest multiple of 50
            return (int) (Math.floor(number / multiple) * multiple);
        } else {
            // Round up to the next multiple of 50
            return (int) (Math.ceil(number / multiple) * multiple);
        }
    }


    public String getExpiryShotDateByIndex(String date, String underling, String optionType) {
        // has to found the data from the
        if (date == null || !indexExpiryDates.containsKey(underling)) {
            return null;
        }
        String key = underling ;
        if (optionType.equalsIgnoreCase(OptionType.FUTURE.getKey())){
            key=underling+ "_" + optionType;
        }
        ExpiryDatesDTO expiryDatesDTO = indexExpiryDates.get(key);
        if (date.equalsIgnoreCase("currentWeek")) {
            // has to fetch tha latest week data format is YYMDD
            return expiryDatesDTO.getCurrentWeek().toLocalDate().toString();
        }if (date.equalsIgnoreCase("currentMonth")) {
            // has to fetch the current month format is YYMMM
            return expiryDatesDTO.getCurrentMonth().toLocalDate().toString();
        }if (date.equalsIgnoreCase("nextWeek")) {
            // has to fetch tha next  week data format is YYMDD
            return expiryDatesDTO.getNextWeek().toLocalDate().toString();
        }if (date.equalsIgnoreCase("nextMonth")) {
            // has to fetch the current month format is YYMMM
            return expiryDatesDTO.getNextMonth().toLocalDate().toString();
        }
        return expiryDatesDTO.getNextMonth().toLocalDate().toString();
    }
    public boolean shouldRunScheduler() {
        try {
            return true;
//
//            if (holidayService.isTodayAHoliday()) {
//                System.out.println(" ----- Today is a holiday. Scheduler will not run ----- ");
//                return false;
//            }
//
//            DayOfWeek day = LocalDate.now().getDayOfWeek();
//            boolean isWeekend = day == DayOfWeek.SATURDAY || day == DayOfWeek.SUNDAY;
//
//            if (isWeekend && !holidayService.isTodayWorkingWeekend()) {
//                System.out.println(" ---- Today is a weekend and not a working day. Scheduler will not run. ---- ");
//                return false;
//            }
//
//            return true;
        } catch (Exception e) {
            logger.error("Error while checking holiday. Proceeding with scheduler by default.", e);
            return false;
        }
    }

    public String getTrTradingSymbol(String optionKey) {
        logger.info("getTrTradingSymbol called with optionKey: {}", optionKey);
        try {
            if (optionKey == null) return null;

            Matcher matcher = TR_TRADING_SYMBOL_PATTERN.matcher(optionKey);
            if (!matcher.matches()) return null;

            String symbol = matcher.group(1);
            String year = matcher.group(2);
            String month = matcher.group(3);
            String day = matcher.group(4);

            // Format expiry
            LocalDate date = LocalDate.of(Integer.parseInt(year), Integer.parseInt(month), Integer.parseInt(day));
            String expiry = String.format("%02d%s%s",
                    date.getDayOfMonth(),
                    date.getMonth().getDisplayName(TextStyle.SHORT, Locale.ENGLISH).toUpperCase(),
                    String.valueOf(date.getYear()).substring(2)
            );

            String strikeOption = matcher.group(5);

            if ("FUTURE".equalsIgnoreCase(strikeOption)) {
                strikeOption = "FUT";
            }

            return symbol + expiry + strikeOption;

        } catch (Exception e) {
            logger.error("Error while generating TR trading symbol for optionKey: {}. Error: {}", optionKey, e.getMessage());
            return null;
        }
    }

    public String buildOptionSymbol(String underlying, String expiry, int strikePrice, String optionType) {
        if (underlying == null || expiry == null || optionType == null) {
            throw new IllegalArgumentException("Invalid parameters for building option symbol");
        }

        String expiryDate = getExpiryShotDateByIndex(expiry, underlying, OptionType.OPTION.getKey());

        return String.format("%s%s-%d%s",
                underlying,
                expiryDate,
                strikePrice,
                optionType.toUpperCase());
    }
    /**
     * Checks if today (IST) is the expiry day for the given strategy.
     * Uses the strategy's expiry config (e.g., "currentWeek") and underlying
     * to resolve the actual expiry date, then compares with today.
     *
     * @param strategy the strategy to check
     * @return true if today is the strategy's expiry day, false otherwise or on error
     */
    public boolean isExpiryDay(Strategy strategy) {
        try {
            if (strategy == null || strategy.getExpiry() == null || strategy.getUnderlying() == null) {
                return false;
            }
            String expiryDateStr = getExpiryShotDateByIndex(
                    strategy.getExpiry(),
                    strategy.getUnderlying().getName(),
                    OptionType.OPTION.getKey());
            if (expiryDateStr == null) {
                return false;
            }
            LocalDate expiryDate = LocalDate.parse(expiryDateStr);
            LocalDate todayIst = LocalDate.now(ZoneId.of(AppConstants.IST_ZONE_ID));
            boolean isExpiry = todayIst.equals(expiryDate);
            if (isExpiry) {
                logger.info("[ExpiryCheck] Today IS expiry day for strategyId={}, underlying={}, expiry={}",
                        strategy.getId(), strategy.getUnderlying().getName(), expiryDateStr);
            }
            return isExpiry;
        } catch (Exception e) {
            logger.error("[ExpiryCheck] Error checking expiry day for strategyId={}: {}",
                    strategy.getId(), e.getMessage());
            return false;
        }
    }

}
