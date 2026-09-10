package com.quantlab.client.service;

import com.quantlab.client.dto.*;
import com.quantlab.common.entity.AppUser;
import com.quantlab.common.entity.Signal;
import com.quantlab.common.entity.Strategy;
import com.quantlab.common.entity.StrategyLeg;
import com.quantlab.common.exception.custom.StrategyNotFoundException;
import com.quantlab.common.exception.custom.UnauthorizedAccessException;
import com.quantlab.common.repository.AppUserRepository;
import com.quantlab.common.repository.StrategyRepository;
import com.quantlab.common.utils.staticstore.dropdownutils.LegType;
import com.quantlab.common.utils.staticstore.dropdownutils.SignalStatus;
import com.quantlab.common.utils.staticstore.dropdownutils.Status;
import com.quantlab.signal.service.AuthService;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import jakarta.persistence.TypedQuery;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

import static com.quantlab.common.utils.staticstore.AppConstants.AMOUNT_MULTIPLIER;
import static com.quantlab.signal.utils.staticdata.StaticStore.roundToTwoDecimalPlaces;

@Service
@Transactional(readOnly = true)
public class ReportsService {

    private static final Logger logger = LogManager.getLogger(ReportsService.class);

    @Autowired private EntityManager entityManager;
    @Autowired private AuthService authService;
    @Autowired private StrategyRepository strategyRepository;
    @Autowired private AppUserRepository appUserRepository;

    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("dd-MM-yyyy HH:mm:ss");
    private static final ZoneId ZONE_ID = ZoneId.systemDefault();

    private static double roundToTwoDecimalPlaces(double value) {
        return new BigDecimal(value).setScale(2, RoundingMode.HALF_UP).doubleValue();
    }

    public AllReportsResponseDto getAllReports(String clientId, AllReportsRequestDto req) {
        try {
            AppUser appUser = authService.getUserFromCLientId(clientId);

            // Single GROUP BY query -> returns (strategyId, strategyName, SUM(profitLoss))
            List<AggregatedStrategyRow> rows = fetchAggregatedByStrategy(
                    appUser.getId(), req.getFromDate(), req.getToDate(), req.getStrategyId()
            );

            List<AllReportsSingleResponseDto> items = new ArrayList<>(rows.size());
            double totalPnl = 0.0;

            for (AggregatedStrategyRow r : rows) {
                // Your DTO constructor already scales by AMOUNT_MULTIPLIER
                AllReportsSingleResponseDto dto =
                        new AllReportsSingleResponseDto(r.getStrategyId(), r.getStrategyName(), r.getPnlSum());
                items.add(dto);
                totalPnl += dto.getPnl();
            }

            AllReportsResponseDto res = new AllReportsResponseDto();
            res.setReports(items);
            res.setTotalPnl(totalPnl);
            return res;

        } catch (Exception e) {
            logger.error("Error fetching reports : {}", e.getMessage(), e);
            throw new RuntimeException("Error fetching reports", e);
        }
    }


    @Transactional(readOnly = true)
    public ReportsByStrategyResponseDto getReportsByStrategy1(String clientId, ReportsByStrategyRequestDto requestDto) {
        try {
            if (requestDto.getStrategyId() == null) {
                throw new IllegalArgumentException("Strategy ID is required");
            }

            AppUser appUser = authService.getUserFromCLientId(clientId);
            Strategy strategy = strategyRepository.findById(requestDto.getStrategyId())
                    .orElseThrow(() -> new StrategyNotFoundException("No strategy found with ID: " + requestDto.getStrategyId()));

            if (!Objects.equals(strategy.getAppUser().getAppUserId(), appUser.getAppUserId())) {
                throw new UnauthorizedAccessException("Cannot access other user's strategy");
            }

            List<SignalSlimRow> signals = fetchSignalsByStrategyAndUser(
                    appUser.getId(),
                    requestDto.getStrategyId(),
                    Status.RMS_ERROR.getKey(),
                    requestDto.getFromDate(),
                    requestDto.getToDate()
            );

            if (signals.isEmpty()) {
                ReportsByStrategyResponseDto res = new ReportsByStrategyResponseDto();
                res.setStrategyName(strategy.getName());
                res.setStrategyID(strategy.getId());
                res.setTotalPNL(0.0);
                res.setReportsSignalDTOs(Collections.emptyList());
                return res;
            }

            List<Long> signalIds = signals.stream().map(SignalSlimRow::getSignalId).toList();

            List<ReportsLegDto> allLegs = fetchLegsForSignals(signalIds);

            Map<Long, List<ReportsLegDto>> legsBySignal = allLegs.stream()
                    .collect(Collectors.groupingBy(ReportsLegDto::getSignalId));

            Map<LocalDate, ReportsSignalDto> map = new HashMap<>();
            ZoneId zone = ZoneId.systemDefault();

            for (SignalSlimRow s : signals) {
                LocalDate date = s.getCreatedAt().atZone(zone).toLocalDate();
                double pnl = (s.getProfitLoss() == null ? 0.0 : s.getProfitLoss() / (double) AMOUNT_MULTIPLIER);

                ReportsSignalDto dto = map.computeIfAbsent(date, d -> {
                    ReportsSignalDto r = new ReportsSignalDto();
                    r.setDate(d.format(DateTimeFormatter.ofPattern("dd-MM-yyyy")));
                    r.setPnl(0.0);
                    r.setReportsLegDtoList(new ArrayList<>());
                    return r;
                });

                dto.setPnl(roundToTwoDecimalPlaces(dto.getPnl() + pnl));

                // Attach legs belonging to this signal
                List<ReportsLegDto> legs = legsBySignal.get(s.getSignalId());
                if (legs != null) dto.getReportsLegDtoList().addAll(legs);
            }

            List<LocalDate> sortedDates = map.keySet().stream().sorted().toList();

            double cumulative = 0.0;
            Double previousCumulative = null;

            for (LocalDate date : sortedDates) {
                ReportsSignalDto dto = map.get(date);
                double dayPnl = roundToTwoDecimalPlaces(dto.getPnl());

                cumulative += dayPnl;
                dto.setSequentialPNL(roundToTwoDecimalPlaces(cumulative));

                if (previousCumulative == null) {
                    dto.setPnlChange(0.0); // first date
                } else {
                    dto.setPnlChange(roundToTwoDecimalPlaces(cumulative - previousCumulative));
                }
                previousCumulative = cumulative;
            }
            List<ReportsSignalDto> sortedResponse = sortedDates.stream()
                    .sorted(Comparator.reverseOrder())
                    .map(map::get)
                    .toList();

            ReportsByStrategyResponseDto res = new ReportsByStrategyResponseDto();
            res.setStrategyName(strategy.getName());
            res.setStrategyID(strategy.getId());
            res.setTotalPNL(roundToTwoDecimalPlaces(map.values().stream().mapToDouble(ReportsSignalDto::getPnl).sum()));
            res.setReportsSignalDTOs(sortedResponse);
            return res;

        } catch (Exception e) {
            logger.error("Error fetching reports by strategyId: {}", requestDto.getStrategyId(), e);
            throw new RuntimeException("Error fetching reports by strategyId: " + requestDto.getStrategyId(), e);
        }
    }




    private List<AggregatedStrategyRow> fetchAggregatedByStrategy(
            Long userId, Instant from, Instant to, Long strategyId
    ) {
        StringBuilder jpql = new StringBuilder(
                "SELECT s.strategy.id, s.strategy.name, SUM(s.profitLoss) " +
                        "FROM Signal s WHERE s.appUser.id = :userId " +
                        "AND s.executionType = s.strategy.executionType"
        );

        jpql.append(" AND s.status != '").append(Status.RMS_ERROR.getKey()).append("'");

        if (from != null && to != null) {
            jpql.append(" AND s.createdAt BETWEEN :from AND :to");
        } else if (from != null) {
            jpql.append(" AND s.createdAt >= :from");
        } else if (to != null) {
            jpql.append(" AND s.createdAt <= :to");
        }

        if (strategyId != null) {
            jpql.append(" AND s.strategy.id = :strategyId");
        }

        jpql.append(" GROUP BY s.strategy.id, s.strategy.name ORDER BY s.strategy.id ASC");

        Query q = entityManager.createQuery(jpql.toString());
        q.setParameter("userId", userId);
        if (from != null) q.setParameter("from", from);
        if (to != null) q.setParameter("to", to);
        if (strategyId != null) q.setParameter("strategyId", strategyId);

        @SuppressWarnings("unchecked")
        List<Object[]> raw = q.getResultList();

        List<AggregatedStrategyRow> out = new ArrayList<>(raw.size());
        for (Object[] row : raw) {
            Long sId = (Long) row[0];
            String sName = (String) row[1];
            Long sum = (row[2] == null) ? 0L : (Long) row[2];
            out.add(new AggregatedStrategyRow(sId, sName, sum));
        }
        return out;
    }

    @SuppressWarnings("unchecked")
    private List<SignalSlimRow> fetchSignalsByStrategyAndUser(
            Long userId,
            Long strategyId,
            String errorStatus,
            Instant from,
            Instant to
    ) {
        StringBuilder jpql = new StringBuilder(
                "SELECT s.id, s.createdAt, s.profitLoss " +
                        "FROM Signal s " +
                        "WHERE s.appUser.id = :userId AND s.strategy.id = :strategyId " +
                        "AND s.executionType = s.strategy.executionType "
        );

        if (errorStatus != null) {
            jpql.append("AND s.status != :errorStatus ");
        }

        if (from != null && to != null) {
            jpql.append("AND s.createdAt BETWEEN :from AND :to ");
        } else if (from != null) {
            jpql.append("AND s.createdAt >= :from ");
        } else if (to != null) {
            jpql.append("AND s.createdAt <= :to ");
        }

        jpql.append("ORDER BY s.createdAt ASC");

        Query q = entityManager.createQuery(jpql.toString());
        q.setParameter("userId", userId);
        q.setParameter("strategyId", strategyId);
        if (errorStatus != null) q.setParameter("errorStatus", errorStatus);
        if (from != null) q.setParameter("from", from);
        if (to != null) q.setParameter("to", to);

        List<Object[]> raw = q.getResultList();
        List<SignalSlimRow> out = new ArrayList<>(raw.size());
        for (Object[] row : raw) {
            Long id = ((Number) row[0]).longValue();
            Instant createdAt = (Instant) row[1];
            Long profitLoss = (row[2] == null) ? 0L : ((Number) row[2]).longValue();
            out.add(new SignalSlimRow(id, createdAt, profitLoss, null, strategyId, null));
        }
        return out;
    }

    @SuppressWarnings("unchecked")
    private List<ReportsLegDto> fetchLegsForSignals(List<Long> signalIds) {
        if (signalIds.isEmpty()) return Collections.emptyList();

        List<Object[]> raw = entityManager.createQuery(
                        "SELECT l.id, l.signal.id, l.exchangeInstrumentId, l.quantity, l.price, l.createdAt " +
                                "FROM StrategyLeg l " +
                                "WHERE l.signal.id IN :signalIds", Object[].class)
                .setParameter("signalIds", signalIds)
                .getResultList();

        List<ReportsLegDto> legs = new ArrayList<>(raw.size());
        for (Object[] row : raw) {
            ReportsLegDto dto = new ReportsLegDto();
            dto.setLegId(((Number) row[0]).longValue());
            dto.setSignalId(((Number) row[1]).longValue());
            dto.setInstrument((Long) row[2]);
            dto.setQuantity(((Number) row[3]).longValue());
            dto.setPrice(row[4] != null ? ((Number) row[4]).doubleValue() : 0.0);
            dto.setDate((Instant) row[5]);
            dto.setExchange(""); // placeholder if needed
            legs.add(dto);
        }
        return legs;
    }

    @Transactional(readOnly = true)
    public ReportsByStrategyResponseDto getDefaultReportsByStrategy(String strategyName) {
        try {
            Optional<AppUser> appUser = appUserRepository.findByUserRole_id(4L);
            if (appUser.isEmpty()) {
                return null;
            }
            Optional<Strategy> strategy = strategyRepository.findByNameAndAppUser_id(strategyName, appUser.get().getId());

            ReportsByStrategyRequestDto requestDto = new ReportsByStrategyRequestDto();
            if (strategy.isPresent()) {
                requestDto.setStrategyId(strategy.get().getId());
            } else {
                return null;
            }

            return getReportsByStrategy1(appUser.get().getTenentId(), requestDto);
        } catch (Exception e) {
            logger.error("Error fetching reports by strategyId: {}", strategyName, e);
            throw new RuntimeException("Error fetching reports by strategyId: " + strategyName, e);
        }
    }


}
