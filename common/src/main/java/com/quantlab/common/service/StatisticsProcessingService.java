package com.quantlab.common.service;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

import com.quantlab.common.dao.SignalStatsDto;
import com.quantlab.common.dto.*;
import com.quantlab.common.entity.Signal;
import com.quantlab.common.entity.Strategy;
import com.quantlab.common.repository.SignalRepository;
import com.quantlab.common.utils.staticstore.dropdownutils.StatisticsMainTable;
import org.hibernate.Hibernate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import static com.quantlab.common.utils.staticstore.AppConstants.AMOUNT_MULTIPLIER;

@Service
public class StatisticsProcessingService {

    @Autowired
    private SignalRepository signalRepository;

    public static List<WeekStatsSummaryDto> getWeeklyPNLSummary(Strategy strategy) {
        LocalDate today = LocalDate.now();
        DayOfWeek currentDayOfWeek = today.getDayOfWeek();
        LocalDate weekStartDate = today.minusDays(currentDayOfWeek.getValue() - DayOfWeek.MONDAY.getValue());

        ZoneId zoneId = ZoneId.systemDefault(); // or choose your preferred zone

        List<Signal> weekRecords = strategy.getSignals()
                .stream()
                .filter(record -> {
                    Instant createdAt = record.getCreatedAt();
                    Instant start = weekStartDate.atStartOfDay(zoneId).toInstant();
                    Instant end = today.atTime(LocalTime.MAX).atZone(zoneId).toInstant();
                    return !createdAt.isBefore(start) && !createdAt.isAfter(end);
                })
                .collect(Collectors.toList());


        // Group records by DayOfWeek
        Map<DayOfWeek, List<Signal>> recordsByDay = weekRecords.stream()
                .collect(Collectors.groupingBy(record ->
                        record.getCreatedAt()
                                .atZone(zoneId)
                                .getDayOfWeek()
                ));


        List<WeekStatsSummaryDto> summary = new ArrayList<>();

        for (DayOfWeek day : DayOfWeek.values()) {
            LocalDate thisDay = weekStartDate.plusDays(day.getValue() - 1);

            if (thisDay.isAfter(today)) {
                summary.add(new WeekStatsSummaryDto(day.name(), 0, 0, 0));
                continue;
            }

            List<Signal> records = recordsByDay.getOrDefault(day, new ArrayList<>());
            if (records.isEmpty()) {
                summary.add(new WeekStatsSummaryDto(day.name(), 0, 0, 0));
            } else {
                double returns = (records.stream().mapToDouble(Signal::getProfitLoss).sum()) / (double) AMOUNT_MULTIPLIER;
                double maxProfit = (records.stream().mapToDouble(Signal::getProfitLoss).max().orElse(0.0)) / (double) AMOUNT_MULTIPLIER;
                double maxLoss = (records.stream().mapToDouble(Signal::getProfitLoss).min().orElse(0.0)) / (double) AMOUNT_MULTIPLIER;
                summary.add(new WeekStatsSummaryDto(day.name(), 0, 0, 0));
            }
        }

        return summary;
    }


    public static List<MontlyStatisticsDto> getFullMonthlyPNLSummary(Strategy strategy) {
        List<Signal> allRecords = strategy.getSignals();

        ZoneId zoneId = ZoneId.systemDefault();

        Map<YearMonth, List<Signal>> groupedByMonth = allRecords.stream()
                .collect(Collectors.groupingBy(record ->
                        YearMonth.from(record.getCreatedAt().atZone(zoneId))
                ));

        return groupedByMonth.entrySet().stream()
                .sorted(Map.Entry.comparingByKey()) // Optional: to sort by month
                .map(entry -> {
                    YearMonth yearMonth = entry.getKey();
                    List<Signal> records = entry.getValue().stream().filter(signal -> signal.getProfitLoss()!=null).toList();

                    int totalTrades = records.size();
                    double pnlRs = records.stream().mapToDouble(signal-> (double) signal.getProfitLoss() /signal.getMultiplier()).sum()/AMOUNT_MULTIPLIER;
                    double pnlPercent = Math.round((((pnlRs*100.0)/((double) strategy.getMinCapital() /AMOUNT_MULTIPLIER))*100.0)/100.0);
                    BigDecimal pnlRsScaled = BigDecimal.valueOf(pnlRs).setScale(2, RoundingMode.HALF_UP);
                    BigDecimal pnlPercentScaled = BigDecimal.valueOf(pnlPercent).setScale(2, RoundingMode.HALF_UP);

                    String monthLabel = yearMonth.getMonth() + " " + yearMonth.getYear(); // Example: JANUARY 2024

                    return new MontlyStatisticsDto(monthLabel, totalTrades, pnlRsScaled, pnlPercentScaled);
                })
                .collect(Collectors.toList());
    }


    public static StatisticsResponseDto<Number> calculateStatistics(Strategy strategy) {

        Hibernate.initialize(strategy.getSignals());
//        Long signalCount = strategy.getSignals().stream().filter(signal -> signal.getProfitLoss()!=null)
//                .map(AuditingEntity::getCreatedAt)
//                .distinct()
//                .count();
//        Long noOfWinningTrades = strategy.getSignals().stream()
//                .map(Signal::getProfitLoss)
//                .filter(Objects::nonNull)
//                .filter(pnl -> pnl >= 0)
//                .count();
//        Long noOfLossingDays = strategy.getSignals().stream()
//                .map(Signal::getProfitLoss)
//                .filter(Objects::nonNull)
//                .filter(pnl -> pnl < 0)
//                .count();
        Long noOfDays = strategy.getSignals().stream()
                .filter(signal -> signal.getProfitLoss() != null)
                .map(signal -> signal.getCreatedAt().atZone(ZoneId.systemDefault()).toLocalDate())
                .distinct()
                .count();
        Long totalorders = strategy.getSignals().stream().mapToLong(signal ->signal.getSignalLegs().size()).sum();
//        boolean hasAtLeastOneMonthData = strategy.getSignals().stream()
//                .filter(signal -> signal.getProfitLoss() != null)
//                .map(signal -> YearMonth.from(signal.getCreatedAt().atZone(ZoneId.systemDefault())))
//                .distinct().findAny().isPresent();

        AtomicLong TotalProfit = new AtomicLong(0);

        AtomicReference<Instant> lastWinDate = new AtomicReference<>(null);
        AtomicLong winStreak = new AtomicLong(0);
        AtomicLong maxWinningStreakDays = new AtomicLong(0);
        AtomicLong winDays = new AtomicLong(0);
        AtomicLong peakProfit = new AtomicLong(0);
        AtomicLong totalPositiveProfit = new AtomicLong(0);
        strategy.getSignals().stream().filter(signal -> signal.getProfitLoss()!=null)
                .collect(Collectors.groupingBy(
                        signal -> signal.getCreatedAt().atZone(ZoneId.systemDefault()).toLocalDate(),
                        Collectors.summingDouble(signal ->
                                (signal.getProfitLoss() / signal.getMultiplier()))
                )).entrySet().stream()
                .filter(entry -> entry.getValue() >= 0).forEach(entry -> {
                    Instant date = Instant.from(entry.getKey().atStartOfDay(ZoneId.systemDefault()));
                    winDays.incrementAndGet();
                    totalPositiveProfit.addAndGet(entry.getValue().intValue());
                    TotalProfit.addAndGet(entry.getValue().intValue());
                    if(entry.getValue() > peakProfit.get()){
                        peakProfit.set(entry.getValue().intValue());
                    }
                    if(lastWinDate.get() == null) {
                        lastWinDate.set(date);
                        winStreak.set(1);
                        maxWinningStreakDays.set(1);
                    } else {
                        long currentDays = date.getEpochSecond() / (24 * 60 * 60);
                        long previousDays = lastWinDate.get().getEpochSecond() / (24 * 60 * 60);
                        if (currentDays - previousDays == 1) {
                            lastWinDate.set(date);
                            winStreak.incrementAndGet();
                            maxWinningStreakDays.set(Math.max(maxWinningStreakDays.get(), winStreak.get()));
                        }else{
                            lastWinDate.set(date);
                            winStreak.set(1);
                        }
                    }
                });


        AtomicReference<Instant> lastLossDate = new AtomicReference<>(null);
        AtomicLong lossStreak = new AtomicLong(0);
        AtomicLong maxLosingStreakDays = new AtomicLong(0);
        AtomicLong lossDays = new AtomicLong(0);
        AtomicLong peakLoss = new AtomicLong(0);
        AtomicLong totalLoss = new AtomicLong(0);
        strategy.getSignals().stream().filter(signal -> signal.getProfitLoss()!=null)
                .collect(Collectors.groupingBy(
                        signal -> signal.getCreatedAt().atZone(ZoneId.systemDefault()).toLocalDate(),
                        Collectors.summingDouble(signal ->  (double)
                                (signal.getProfitLoss() / signal.getMultiplier()))
                )).entrySet().stream()
                .filter(entry -> entry.getValue() < 0).forEach(entry -> {
                    Instant date = Instant.from(entry.getKey().atStartOfDay(ZoneId.systemDefault()));
                    lossDays.incrementAndGet();
                    TotalProfit.addAndGet(entry.getValue().intValue());
                    totalLoss.addAndGet(entry.getValue().intValue());
                    if(entry.getValue() < peakLoss.get()){
                        peakLoss.set(entry.getValue().intValue());
                    }
                    if(lastLossDate.get() == null) {
                        lastLossDate.set(date);
                        lossStreak.set(1);
                        maxLosingStreakDays.set(1);
                    } else {
                        long currentDays = date.getEpochSecond() / (24 * 60 * 60);
                        long previousDays = lastLossDate.get().getEpochSecond() / (24 * 60 * 60);
                        if (currentDays - previousDays == 1) {
                            lastLossDate.set(date);
                            lossStreak.incrementAndGet();
                            maxLosingStreakDays.set(Math.max(maxLosingStreakDays.get(), lossStreak.get()));
                        }else{
                            lastLossDate.set(date);
                            lossStreak.set(1);
                        }
                    }
                });

        List<Signal> signals = strategy.getSignals().stream()
                .filter(signal -> signal.getProfitLoss() != null)
                .collect(Collectors.toList());

        Map<DayOfWeek, List<Signal>> signalsByDay = signals.stream() .filter(signal -> {
                    DayOfWeek day = signal.getCreatedAt().atZone(ZoneId.systemDefault()).getDayOfWeek();
                    return day != DayOfWeek.SATURDAY && day != DayOfWeek.SUNDAY;
                })
                .collect(Collectors.groupingBy(signal ->
                        signal.getCreatedAt().atZone(ZoneId.systemDefault()).getDayOfWeek()
                ));
        System.out.println(signalsByDay);
        Long capital = strategy.getMinCapital()/ AMOUNT_MULTIPLIER;
        Double roi = (double) Math.round((double) (((double)  TotalProfit.get()/capital)*100.00)/100.0);
        List<WeekStatsSummaryDto> weekStats = new ArrayList<>();
        for (DayOfWeek day : DayOfWeek.values()) {
            if (DayOfWeek.SATURDAY == day || DayOfWeek.SUNDAY == day) {
                continue;
            }
            List<Signal> daySignals = signalsByDay.getOrDefault(day, new ArrayList<>());
            double returns = daySignals.stream().mapToDouble(signal ->  (double)
                    (signal.getProfitLoss() / signal.getMultiplier())).sum()/AMOUNT_MULTIPLIER;
            double maxProfit = daySignals.stream().mapToDouble(signal ->  (double)
                    (signal.getProfitLoss() / signal.getMultiplier())).max().orElse(0.0)/AMOUNT_MULTIPLIER;
            double maxLoss = daySignals.stream().mapToDouble(signal ->  (double)
                    (signal.getProfitLoss() / signal.getMultiplier())).min().orElse(0.0)/AMOUNT_MULTIPLIER;
            weekStats.add(new WeekStatsSummaryDto(
                    day.name(),
                    roundToTwoDecimals(returns),
                    roundToTwoDecimals(maxProfit),
                    roundToTwoDecimals(maxLoss)
            ));
        }
        Long minCapital = strategy.getMinCapital()/ AMOUNT_MULTIPLIER;
        Long DD = Math.abs((peakProfit.get()/AMOUNT_MULTIPLIER) - (peakLoss.get()/AMOUNT_MULTIPLIER));
        double winRate = noOfDays != 0 ? Math.round((((winDays.get() * 100.0) / noOfDays)*100.0)/100.0) : 0.0;
        double lossRate = noOfDays != 0 ? Math.round((((lossDays.get() * 100.0) / noOfDays)*100.0)/100.0) : 0.0;
        double DDPercent = Math.round(((((double) DD /(minCapital)) * 100.0)*100.0)/100.0);
        List<StatisticsResponseObjDto<Number>> statisticsList = new ArrayList<>();
        double avgProfitDaysProfit = (double) Math.round((((double) (totalPositiveProfit.get() / AMOUNT_MULTIPLIER) /winDays.get())*100.0)/100.0);
        double avgLossDaysLoss = (double) Math.round((((double) (totalLoss.doubleValue()/ AMOUNT_MULTIPLIER) /lossDays.get())*100.0)/100.0);
        statisticsList.add(new StatisticsResponseObjDto<Number>("Capital Required", strategy.getMinCapital()/ AMOUNT_MULTIPLIER));
        statisticsList.add(new StatisticsResponseObjDto<Number>("Total Trading Days", noOfDays));
        statisticsList.add(new StatisticsResponseObjDto<Number>("Win Days", winDays.get()));
        statisticsList.add(new StatisticsResponseObjDto<Number>("Loss Days", lossDays.get()));
        statisticsList.add(new StatisticsResponseObjDto<Number>("Max Winning Streak Days", maxWinningStreakDays.get()));
        statisticsList.add(new StatisticsResponseObjDto<Number>("Max Losing Streak Days", maxLosingStreakDays.get()));
        statisticsList.add(new StatisticsResponseObjDto<Number>("Win Rate",winRate));
        statisticsList.add(new StatisticsResponseObjDto<Number>("Loss Rate",lossRate));
        statisticsList.add(new StatisticsResponseObjDto<Number>("Avg Monthly Profit", 0.0));
        statisticsList.add(new StatisticsResponseObjDto<Number>("Total Profit", TotalProfit.get()/AMOUNT_MULTIPLIER));
        statisticsList.add(new StatisticsResponseObjDto<Number>("Avg Monthly ROI", 0.0));
        statisticsList.add(new StatisticsResponseObjDto<Number>("Total ROI", roi));
//        statisticsList.add(new StatisticsResponseObjDto<Number>("Standard Deviation (Annualised)", 0.0));
//        statisticsList.add(new StatisticsResponseObjDto<Number>("Sharpe Ratio (Annualised)", null));
//        statisticsList.add(new StatisticsResponseObjDto<Number>("Sorting Ratio (Annualised)", null));
        statisticsList.add(new StatisticsResponseObjDto<Number>("Max Profit In Day", peakProfit.doubleValue()/AMOUNT_MULTIPLIER));
        statisticsList.add(new StatisticsResponseObjDto<Number>("Max Loss In Day", peakLoss.doubleValue()/AMOUNT_MULTIPLIER));
        statisticsList.add(new StatisticsResponseObjDto<Number>("Avg Profit/Loss Daily", (double) Math.round(((TotalProfit.doubleValue()/AMOUNT_MULTIPLIER)/noOfDays)*100.0)/100.0));
        statisticsList.add(new StatisticsResponseObjDto<Number>("Avg Profit On Profit Days", avgProfitDaysProfit));
        statisticsList.add(new StatisticsResponseObjDto<Number>("Avg Loss On Loss Days", avgLossDaysLoss));
        statisticsList.add(new StatisticsResponseObjDto<Number>("Avg no.of trades (Buy + Sell) per trading day", totalorders/noOfDays));
        statisticsList.add(new StatisticsResponseObjDto<Number>(StatisticsMainTable.MAX_DRAWDOWN_PERCENT.getLabel(), DDPercent));
        statisticsList.add(new StatisticsResponseObjDto<Number>("Max Drawdown", DD));

        StatisticsIndexes statisticsIndexes = new StatisticsIndexes();
        statisticsIndexes.setTotalProfit(TotalProfit.get());
        statisticsIndexes.setMaxDrawDown(DD);
        statisticsIndexes.setMaxDrawDownPercent((long) (DDPercent * AMOUNT_MULTIPLIER));
        statisticsIndexes.setWinRate((long) (winRate * AMOUNT_MULTIPLIER));
        statisticsIndexes.setLossRate((long) (lossRate * AMOUNT_MULTIPLIER));
        statisticsIndexes.setCapitalRequired(strategy.getMinCapital());
        statisticsIndexes.setTotalTradingDays(noOfDays);
        statisticsIndexes.setAvgTradesPerDay(totalorders/noOfDays);
        statisticsIndexes.setAvgProfitOnWinDays((long) (avgProfitDaysProfit * AMOUNT_MULTIPLIER));
        statisticsIndexes.setAvgLossOnLossDays((long) (avgLossDaysLoss * AMOUNT_MULTIPLIER));
        statisticsIndexes.setAvgDailyProfit((long) ((TotalProfit.doubleValue()/noOfDays)*AMOUNT_MULTIPLIER));

        PerformanceOverviewDto  performanceOverviewDto = new PerformanceOverviewDto();
        if(winDays.get() != 0){
            performanceOverviewDto.setWinRatio((winDays.get()+lossDays.get())/winDays.get());
        }else{
            performanceOverviewDto.setWinRatio(0L);
        }
        performanceOverviewDto.setWinDays(winDays.get());
        performanceOverviewDto.setLossDays(lossDays.get());

//        List<MontlyStatisticsDto> monthlyStatistics = getFullMonthlyPNLSummary(strategy);
        List<MontlyStatisticsDto> monthlyStatistics = new ArrayList<>();
        StatisticsResponseDto<Number> res = new StatisticsResponseDto<Number>();
        res.setMonthlyStatistics(monthlyStatistics);
        res.setStatistics(statisticsList);
        res.setWeekStatsSummary(weekStats);
        res.setPerformanceOverview(performanceOverviewDto);
        res.setProfitStatistics(new ProfitStatisticsDto(TotalProfit.get()/AMOUNT_MULTIPLIER,0.0,roi));
        res.setRiskMetrics(new RiskMetricsDto(DD,0L,0L));
        res.setStatisticsIndexes(statisticsIndexes);
        return res;
    }

    public StatisticsResponseDto<Number> calculateNewStatistics(Strategy strategy) {

        List<Object[]> rows = signalRepository.findSignalStatsByStrategyId(strategy.getId(), strategy.getExecutionType());
        List<SignalStatsDto> signals = rows.stream().map(this::mapRowToDto).collect(Collectors.toList());
        return calculateStatisticsFromDtos(signals, strategy);
    }

    private SignalStatsDto mapRowToDto(Object[] row) {
        // row: id, profit_loss, created_at, multiplier, leg_count
        Long id = null;
        Long profitLoss = null;
        Instant createdAt = null;
        Long multiplier = null;
        Integer legCount = 0;
        try {
            if (row[0] instanceof Number) id = ((Number) row[0]).longValue();
            if (row[1] instanceof Number) profitLoss = ((Number) row[1]).longValue();
            if (row[2] instanceof java.sql.Timestamp) createdAt = ((java.sql.Timestamp) row[2]).toInstant();
            else if (row[2] instanceof Instant) createdAt = (Instant) row[2];
            if (row[3] instanceof Number) multiplier = ((Number) row[3]).longValue();
            if (row[4] instanceof Number) legCount = ((Number) row[4]).intValue();
        } catch (Exception e) {
            // fallthrough with best-effort partial mapping
        }
        if (multiplier == null || multiplier == 0L) multiplier = 1L;
        return new SignalStatsDto(id, profitLoss, createdAt, multiplier, legCount);
    }

    /**
     * Core computation operating on lightweight DTOs.
     */
    public static StatisticsResponseDto<Number> calculateStatisticsFromDtos(List<SignalStatsDto> signalsDto, Strategy strategy) {
        ZoneId zoneId = ZoneId.systemDefault();
        DateTimeFormatter monthFormatter = DateTimeFormatter.ofPattern("MMM yyyy", Locale.ENGLISH);

        // Fast-path: empty input
        if (signalsDto == null || signalsDto.isEmpty()) {
            StatisticsResponseDto<Number> emptyRes = new StatisticsResponseDto<>();
            emptyRes.setMonthlyStatistics(new ArrayList<>());
            emptyRes.setStatistics(new ArrayList<>());
            emptyRes.setWeekStatsSummary(new ArrayList<>());
            emptyRes.setPerformanceOverview(new PerformanceOverviewDto());
            emptyRes.setProfitStatistics(new ProfitStatisticsDto(0L, 0.0, 0.0));
            emptyRes.setRiskMetrics(new RiskMetricsDto(0L, 0L, 0L));
            emptyRes.setStatisticsIndexes(new StatisticsIndexes());
            return emptyRes;
        }

        // Daily aggregation (date -> summed profit for that date). Use primitive double for sums.
        Map<java.time.LocalDate, Double> dailySum = new java.util.HashMap<>();
        Map<YearMonth, Double> monthlyPnlSum = new java.util.HashMap<>();
        Map<YearMonth, Integer> monthlyTrades = new java.util.HashMap<>();

        // Weekday aggregation arrays indexed by DayOfWeek.getValue()-1 (MONDAY=1)
        double[] weekdaySum = new double[7];
        double[] weekdayMax = new double[7];
        double[] weekdayMin = new double[7];
        java.util.Arrays.fill(weekdayMax, Double.NEGATIVE_INFINITY);
        java.util.Arrays.fill(weekdayMin, Double.POSITIVE_INFINITY);

        long totalOrders = 0L;

        // Single pass over signals to populate daily sums and weekday stats
        for (SignalStatsDto s : signalsDto) {
            if (s == null) continue;
            Instant created = s.getCreatedAt();
            Long pnl = s.getProfitLoss();
            if (created == null || pnl == null) continue;

            long multiplier = (s.getMultiplier() == null || s.getMultiplier() == 0L) ? 1L : s.getMultiplier();
            double value = ((double) pnl) / (double) multiplier; // per-signal contribution (not scaled by AMOUNT_MULTIPLIER yet)

            java.time.LocalDate date = created.atZone(zoneId).toLocalDate();
            dailySum.merge(date, value, Double::sum);
            YearMonth yearMonth = YearMonth.from(date);
            monthlyPnlSum.merge(yearMonth, value, Double::sum);

            DayOfWeek day = date.getDayOfWeek();
            int idx = day.getValue() - 1;
            if (day != DayOfWeek.SATURDAY && day != DayOfWeek.SUNDAY) {
                weekdaySum[idx] += value;
                if (value > weekdayMax[idx]) weekdayMax[idx] = value;
                if (value < weekdayMin[idx]) weekdayMin[idx] = value;
            }

            Integer legCount = s.getLegCount();
            // Leg rows already include both OPEN and EXIT entries; convert to combined trade count.
            int tradeCount = (legCount == null ? 0 : legCount / 2);
            totalOrders += tradeCount;
            monthlyTrades.merge(yearMonth, tradeCount, Integer::sum);
        }

        // Prepare sorted list of dates to compute streaks and totals
        java.util.List<java.time.LocalDate> dates = new java.util.ArrayList<>(dailySum.keySet());
        java.util.Collections.sort(dates);

        long noOfDays = dates.size();

        long winDays = 0L, lossDays = 0L;
        long maxWinStreak = 0L, currentWinStreak = 0L;
        long maxLossStreak = 0L, currentLossStreak = 0L;

        double totalProfit = 0.0;
        double totalPositiveProfit = 0.0;
        double totalLoss = 0.0;

        double peakProfit = Double.NEGATIVE_INFINITY;
        double peakLoss = Double.POSITIVE_INFINITY;

        java.time.LocalDate prevWinDate = null;
        java.time.LocalDate prevLossDate = null;

        for (java.time.LocalDate d : dates) {
            double dayVal = dailySum.getOrDefault(d, 0.0);
            totalProfit += dayVal;

            if (dayVal >= 0.0) {
                winDays++;
                totalPositiveProfit += dayVal;
                if (prevWinDate != null && prevWinDate.plusDays(1).equals(d)) {
                    currentWinStreak++;
                } else {
                    currentWinStreak = 1;
                }
                if (currentWinStreak > maxWinStreak) maxWinStreak = currentWinStreak;
                prevWinDate = d;
                if (dayVal > peakProfit) peakProfit = dayVal;
            } else {
                lossDays++;
                totalLoss += dayVal;
                if (prevLossDate != null && prevLossDate.plusDays(1).equals(d)) {
                    currentLossStreak++;
                } else {
                    currentLossStreak = 1;
                }
                if (currentLossStreak > maxLossStreak) maxLossStreak = currentLossStreak;
                prevLossDate = d;
                if (dayVal < peakLoss) peakLoss = dayVal;
            }
        }

        // Build week stats (Mon-Fri), convert values by AMOUNT_MULTIPLIER when reporting
        java.util.List<WeekStatsSummaryDto> weekStats = new java.util.ArrayList<>();
        for (DayOfWeek day : DayOfWeek.values()) {
            if (day == DayOfWeek.SATURDAY || day == DayOfWeek.SUNDAY) continue;
            int idx = day.getValue() - 1;
            double returns = weekdaySum[idx] / AMOUNT_MULTIPLIER;
            double maxP = (weekdayMax[idx] == Double.NEGATIVE_INFINITY) ? 0.0 : (weekdayMax[idx] / AMOUNT_MULTIPLIER);
            double minP = (weekdayMin[idx] == Double.POSITIVE_INFINITY) ? 0.0 : (weekdayMin[idx] / AMOUNT_MULTIPLIER);
            weekStats.add(new WeekStatsSummaryDto(
                    day.name(),
                    roundToTwoDecimals(returns),
                    roundToTwoDecimals(maxP),
                    roundToTwoDecimals(minP)
            ));
        }

        // Keep both representations: stored (scaled) min capital and human-readable capital in rupees
        long minCapitalStored = (strategy.getMinCapital() == null ? 0L : strategy.getMinCapital());
        long capital = minCapitalStored / AMOUNT_MULTIPLIER; // capital in rupees for display
        double roi = (minCapitalStored == 0L) ? 0.0 : Math.round((totalProfit / (double) minCapitalStored) * 10000.0) / 100.0;

        long peakProfitScaled = (peakProfit == Double.NEGATIVE_INFINITY) ? 0L : Math.round(peakProfit / AMOUNT_MULTIPLIER);
        // peakLoss was tracked as the most negative day value (e.g. -5000). Convert to positive magnitude
        long peakLossScaled = (peakLoss == Double.POSITIVE_INFINITY) ? 0L : Math.round(Math.abs(peakLoss) / AMOUNT_MULTIPLIER);
        // DrawDown (DD) per requirement: highest total loss in a single day (magnitude)
        long DD = peakLossScaled;

        double winRate = (noOfDays == 0L) ? 0.0 : Math.round(((double) winDays * 100.0 / (double) noOfDays) * 100.0) / 100.0;
        double lossRate = (noOfDays == 0L) ? 0.0 : Math.round(((double) lossDays * 100.0 / (double) noOfDays) * 100.0) / 100.0;
        // Drawdown percentage: DD as percent of minimum capital
        double DDPercent = (capital == 0L) ? 0.0 : Math.round(((double) DD / (double) capital) * 100.0);

        // Prepare statistics list
        java.util.List<StatisticsResponseObjDto<Number>> statisticsList = new java.util.ArrayList<>();
        double avgProfitDaysProfit = (winDays == 0L) ? 0.0 : Math.round(((totalPositiveProfit / AMOUNT_MULTIPLIER) / (double) winDays) * 100.0) / 100.0;
        double avgLossDaysLoss = (lossDays == 0L) ? 0.0 : Math.round(((totalLoss / AMOUNT_MULTIPLIER) / (double) lossDays) * 100.0) / 100.0;

        statisticsList.add(new StatisticsResponseObjDto<>("Capital Required", strategy.getMinCapital() / AMOUNT_MULTIPLIER));
        statisticsList.add(new StatisticsResponseObjDto<>("Total Trading Days", noOfDays));
        statisticsList.add(new StatisticsResponseObjDto<>("Win Days", winDays));
        statisticsList.add(new StatisticsResponseObjDto<>("Loss Days", lossDays));
        statisticsList.add(new StatisticsResponseObjDto<>("Max Winning Streak Days", maxWinStreak));
        statisticsList.add(new StatisticsResponseObjDto<>("Max Losing Streak Days", maxLossStreak));
        statisticsList.add(new StatisticsResponseObjDto<>("Win Rate", winRate));
        statisticsList.add(new StatisticsResponseObjDto<>("Loss Rate", lossRate));
        statisticsList.add(new StatisticsResponseObjDto<>("Avg Monthly Profit", 0.0));
        statisticsList.add(new StatisticsResponseObjDto<>("Total Profit", Math.round(totalProfit) / AMOUNT_MULTIPLIER));
        statisticsList.add(new StatisticsResponseObjDto<>("Avg Monthly ROI", 0.0));
        statisticsList.add(new StatisticsResponseObjDto<>("Total ROI", roi));
        statisticsList.add(new StatisticsResponseObjDto<>("Max Profit In Day", (double) peakProfitScaled));
        statisticsList.add(new StatisticsResponseObjDto<>("Max Loss In Day", (double) peakLossScaled));
        statisticsList.add(new StatisticsResponseObjDto<>("Avg Profit/Loss Daily", (double) Math.round(((totalProfit / AMOUNT_MULTIPLIER) / (noOfDays == 0L ? 1.0 : (double) noOfDays)) * 100.0) / 100.0));
        statisticsList.add(new StatisticsResponseObjDto<>("Avg Profit On Profit Days", avgProfitDaysProfit));
        statisticsList.add(new StatisticsResponseObjDto<>("Avg Loss On Loss Days", avgLossDaysLoss));
        statisticsList.add(new StatisticsResponseObjDto<>("Avg no.of trades (Buy + Sell) per trading day", totalOrders / (noOfDays == 0L ? 1L : noOfDays)));
        statisticsList.add(new StatisticsResponseObjDto<>(StatisticsMainTable.MAX_DRAWDOWN_PERCENT.getLabel(), DDPercent));
        statisticsList.add(new StatisticsResponseObjDto<>("Max Drawdown", DD));

        // Prepare statistics indexes
        StatisticsIndexes statisticsIndexes = new StatisticsIndexes();
        statisticsIndexes.setTotalProfit(Math.round(totalProfit));
        statisticsIndexes.setMaxDrawDown(DD);
        statisticsIndexes.setMaxDrawDownPercent((long) (DDPercent * AMOUNT_MULTIPLIER));
        statisticsIndexes.setWinRate((long) (winRate * AMOUNT_MULTIPLIER));
        statisticsIndexes.setLossRate((long) (lossRate * AMOUNT_MULTIPLIER));
        statisticsIndexes.setCapitalRequired(strategy.getMinCapital());
        statisticsIndexes.setTotalTradingDays(noOfDays);
        statisticsIndexes.setAvgTradesPerDay(totalOrders / (noOfDays == 0L ? 1L : noOfDays));
        statisticsIndexes.setAvgProfitOnWinDays((long) (avgProfitDaysProfit * AMOUNT_MULTIPLIER));
        statisticsIndexes.setAvgLossOnLossDays((long) (avgLossDaysLoss * AMOUNT_MULTIPLIER));
        statisticsIndexes.setAvgDailyProfit((long) ((totalProfit / (noOfDays == 0L ? 1.0 : (double) noOfDays)) * AMOUNT_MULTIPLIER));

        PerformanceOverviewDto performanceOverviewDto = new PerformanceOverviewDto();
        performanceOverviewDto.setWinRatio((winDays == 0L) ? 0L : (long) ((winDays + lossDays) / winDays));
        performanceOverviewDto.setWinDays(winDays);
        performanceOverviewDto.setLossDays(lossDays);

        List<MontlyStatisticsDto> monthlyStatistics = monthlyPnlSum.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(entry -> {
                    YearMonth ym = entry.getKey();
                    double pnlRs = entry.getValue() / AMOUNT_MULTIPLIER;
                    double pnlPercent = (minCapitalStored == 0L) ? 0.0 : Math.round((entry.getValue() / (double) minCapitalStored) * 10000.0) / 100.0;
                    BigDecimal pnlRsScaled = BigDecimal.valueOf(pnlRs).setScale(2, RoundingMode.HALF_UP);
                    BigDecimal pnlPercentScaled = BigDecimal.valueOf(pnlPercent).setScale(2, RoundingMode.HALF_UP);
                    return new MontlyStatisticsDto(
                            ym.format(monthFormatter),
                            monthlyTrades.getOrDefault(ym, 0),
                            pnlRsScaled,
                            pnlPercentScaled
                    );
                })
                .collect(Collectors.toList());
        StatisticsResponseDto<Number> res = new StatisticsResponseDto<>();
        res.setMonthlyStatistics(monthlyStatistics);
        res.setStatistics(statisticsList);
        res.setWeekStatsSummary(weekStats);
        res.setPerformanceOverview(performanceOverviewDto);
        res.setProfitStatistics(new ProfitStatisticsDto(Math.round(totalProfit) / AMOUNT_MULTIPLIER, 0.0, roi));
        res.setRiskMetrics(new RiskMetricsDto(DD, 0L, 0L));
        res.setStatisticsIndexes(statisticsIndexes);
        return res;
    }

    private static double roundToTwoDecimals(double value) {
        return Math.round(value * 100.0) / 100.0;
    }

}
