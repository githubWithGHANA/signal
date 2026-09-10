package com.quantlab.signal.utils.staticdata;

import com.quantlab.signal.dto.ExpiryDatesDTO;
import com.quantlab.signal.dto.redisDto.MasterResponseFO;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

import static com.quantlab.common.utils.staticstore.AppConstants.AMOUNT_MULTIPLIER;

@Component
public class StaticStore {

    public static Map<String, MasterResponseFO> redisMasterResponseFOData = new HashMap<>();
    public static Map<String, ArrayList<LocalDateTime>> redisFetchedData= new HashMap<>();
    public static Map<String, List<String>> redisFetchedIndexExpiry;
    //    private List<Strategy> allStrategys = new ArrayList<>();
    public static Map<String, ExpiryDatesDTO> indexExpiryDates = new HashMap<>();
    public static Map<String, List<Integer>> redisIndexStrikePrices = new HashMap<>();
    public static LocalDate EXCEPTION_DATE; // used to set exception dates when strategies can run

    public static double roundToTwoDecimalPlaces(double value) {
        return new BigDecimal(value)
                .setScale(2, RoundingMode.HALF_UP)
                .doubleValue();
    }
}
