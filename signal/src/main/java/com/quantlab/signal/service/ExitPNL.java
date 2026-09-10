package com.quantlab.signal.service;

import com.quantlab.common.dto.SignalPNLDTO;
import com.quantlab.common.dto.StrategyLegPNLDTO;
import com.quantlab.common.entity.Signal;
import com.quantlab.common.entity.StrategyLeg;
import com.quantlab.common.repository.SignalRepository;
import com.quantlab.common.repository.StrategyLegRepository;
import com.quantlab.common.repository.StrategyRepository;
import com.quantlab.common.utils.staticstore.dropdownutils.LegSide;
import com.quantlab.common.utils.staticstore.dropdownutils.LegStatus;
import com.quantlab.common.utils.staticstore.dropdownutils.LegType;
import com.quantlab.common.utils.staticstore.dropdownutils.SignalStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import static com.quantlab.common.utils.staticstore.AppConstants.AMOUNT_MULTIPLIER;

@Service
public class ExitPNL {
    private static final Logger logger = LoggerFactory.getLogger(ExitPNL.class);

    @Autowired
    StrategyRepository strategyRepository;

    @Autowired
    SignalRepository signalRepository;

    @Autowired
    StrategyLegRepository strategyLegRepository;

    public static final ConcurrentHashMap<Long, Double> strategyNonLivePNL = new ConcurrentHashMap<>();

    @Transactional
    public boolean setFinalPNL(Signal signalId) {
        try {
            logger.info("Inside ExitPNL setFinalPNL for signal : {}", signalId);
            long signalPNL = 0L;
            Long existingPNL = 0L;
            boolean hasValidLegs = false;

            List<Object[]> nonExitLegsResponse = strategyLegRepository.findLegsBySignalIdAndLegType(signalId.getId(), LegType.OPEN.getKey());
            List<StrategyLegPNLDTO> nonExitLegs = mapToStrategyLegPNLDTOList(nonExitLegsResponse);
            List<Object[]> exitLegsResponse = strategyLegRepository.findLegsBySignalIdAndLegType(signalId.getId(), LegType.EXIT.getKey());
            List<StrategyLegPNLDTO> exitLegs = mapToStrategyLegPNLDTOList(exitLegsResponse);

            for (StrategyLegPNLDTO leg : nonExitLegs) {
                StrategyLegPNLDTO exitLeg = findExitLeg(exitLegs, leg.getExchangeInstrumentId());
                if (exitLeg == null)
                    continue;

                Long executedPrice = exitLeg.getExecutedPrice();
                if (executedPrice != null) {
                    long legPNL = (long) (processPNL(leg, executedPrice / (double) AMOUNT_MULTIPLIER, leg.getExecutedPrice()) * AMOUNT_MULTIPLIER);
                    leg.setCurrentIV(exitLeg.getConstantIV());
                    leg.setCurrentDelta(exitLeg.getConstantDelta());
                    leg.setProfitLoss(legPNL);
                    signalPNL = signalPNL + legPNL;
                    hasValidLegs = true;
                }
            }
            if (hasValidLegs && signalId.getStatus().equalsIgnoreCase(SignalStatus.EXIT.getKey())) {
                String lastPNL = SignalStatus.EXIT.getKey();
                if (!(LocalTime.now().isAfter(LocalTime.of(9, 15)) &&
                        LocalTime.now().isBefore(LocalTime.of(15, 31)))) {
                    lastPNL = SignalStatus.SCHEDULED_PNL.getKey();
                }
                saveLegsInDB(nonExitLegs);
                signalRepository.updateSignalProfitAndLastPNL(signalId.getId(), signalPNL, lastPNL);

                if (!lastPNL.equalsIgnoreCase(SignalStatus.SCHEDULED_PNL.getKey())) {
                    existingPNL = strategyRepository.findTodayPNLById(signalId.getStrategy().getId());
                    if (existingPNL == null) existingPNL = 0L;
                    strategyRepository.updateTodayPNLById(signalId.getStrategy().getId(), existingPNL + signalPNL);
                }

                logger.info("Exit PNL saved for strategyId : {}, StrategyPNL : {}, SignalPNL :{}", signalId.getStrategy().getId(),(existingPNL + signalPNL) ,signalPNL);
            }
            assignStrategyNonLivePNL(signalId.getStrategy().getId(), signalPNL / (double) AMOUNT_MULTIPLIER);
            return hasValidLegs;
        } catch (Exception e) {
            logger.error("error in ExitPNL setFinalPNL : {}, error: {}", signalId, e.getMessage());
        }
        return false;
    }

    private StrategyLegPNLDTO findExitLeg(List<StrategyLegPNLDTO> exitLegs, Long exchangeInstrumentId) {
        Iterator<StrategyLegPNLDTO> iterator = exitLegs.iterator();
        while (iterator.hasNext()) {
            StrategyLegPNLDTO exitLeg = iterator.next();
            if (exitLeg.getExchangeInstrumentId().equals(exchangeInstrumentId)) {
                iterator.remove();
                return exitLeg;
            }
        }
        return null;
    }

    private Map<Long, Long> mapToInstrumentIdAndPNL(List<Object[]> results) {
        return results.stream()
                .collect(Collectors.toMap(
                        row -> (Long) row[0],
                        row -> (Long) row[1],
                        (existing, replacement) -> replacement
                ));
    }

    public List<StrategyLegPNLDTO> mapToStrategyLegPNLDTOList(List<Object[]> results) {
        return results.stream()
                .map(row -> new StrategyLegPNLDTO(
                        getLong(row[0]),  // id
                        getLong(row[1]),  // ltp
                        getLong(row[2]),  // profitLoss
                        getLong(row[3]),  // currentIV
                        getLong(row[4]),  // currentDelta
                        (String) row[5],  // buySellFlag
                        getLong(row[6]),  // filledQuantity
                        getLong(row[7]),  // price
                        (String) row[8],  // name
                        (String) row[9],  // status
                        (String) row[10], // legType
                        getLong(row[11]), // lotSize
                        getLong(row[12]), // noOfLots
                        getLong(row[13]), // signalId
                        getLong(row[14]), // exchangeInstrumentId
                        getLong(row[15]), // executedPrice
                        getLong(row[16]), // constantIV
                        getLong(row[17]), // constantDelta
                        getLong(row[18]), // latestIndexPrice
                        getLong(row[19])  // baseIndexPrice
                ))
                .collect(Collectors.toList());
    }

    private Long getLong(Object obj) {
        return obj != null ? ((Number) obj).longValue() : null;
    }

    private double processPNL(StrategyLegPNLDTO strategyLeg, double exitPrice, Long entryPrice) {
        if (entryPrice != null) {
            if (strategyLeg.getBuySellFlag().equalsIgnoreCase(LegSide.BUY.getKey()))
                return (exitPrice - (entryPrice / (double) AMOUNT_MULTIPLIER)) * strategyLeg.getFilledQuantity();
            else
                return ((entryPrice / (double) AMOUNT_MULTIPLIER) - exitPrice) * strategyLeg.getFilledQuantity();
        }
        return 0;
    }

    @Transactional
    public void updateProfitLoss(Long signalId, Long exchangeInstrumentId, Long profitLoss, String legType) {
        Long latestId = strategyLegRepository.findLatestLegId(signalId, exchangeInstrumentId, legType);
        if (latestId != null) {
            strategyLegRepository.updateProfitLossById(latestId, profitLoss);
        }
    }

    public void assignStrategyNonLivePNL(Long strategyId, Double exitPnl) {
        try {
            if (!strategyNonLivePNL.containsKey(strategyId)) {
                return;
            }
            double existingPNL = strategyNonLivePNL.get(strategyId);
            exitPnl = existingPNL + exitPnl;
            strategyNonLivePNL.put(strategyId, exitPnl);
        } catch (Exception e) {
            logger.error("error in assigning strategy non live PNL for strategyId : {}, error: {}", strategyId, e.getMessage());
        }

    }


    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void saveLegsInDB(List<StrategyLegPNLDTO> savingStrategyLegsDTOList) {
        try {
//            allLiveSignals.forEach((signal) -> signalRepository.updateProfitLossAndIndexNowById(signal.getId(), signal.getProfitLoss(),signal.getLatestIndexPrice()));
            if (!savingStrategyLegsDTOList.isEmpty()) {
                for (StrategyLegPNLDTO dto : savingStrategyLegsDTOList) {
                    strategyLegRepository.updateRealtimeLegData(
                            dto.getId(), dto.getLtp(), dto.getProfitLoss(),
                            dto.getCurrentIV(), dto.getCurrentDelta());
                }
            }
        } catch (Exception e) {
            logger.error("error saving data in P&L socket " + e.getMessage());
        }
    }

    @Transactional
    public void saveSingleLegPnl(StrategyLeg exitLeg) {
        try {
            logger.info("saveSingleLegPnl: entered for signalId={}, exitLegId={}, exchangeInstrumentId={}, exitLegType={}, exitLegStatus={}",
                    exitLeg.getSignal() != null ? exitLeg.getSignal().getId() : null,
                    exitLeg.getId(),
                    exitLeg.getExchangeInstrumentId(),
                    exitLeg.getLegType(),
                    exitLeg.getStatus());
            List<Object[]> oldestOpenLegResponse = strategyLegRepository.findOldestOpenLeg(exitLeg.getSignal().getId(), exitLeg.getExchangeInstrumentId());
            List<StrategyLegPNLDTO> legs = mapToStrategyLegPNLDTOList(oldestOpenLegResponse);
            boolean hasValidLegs = false;


            for (StrategyLegPNLDTO leg : legs) {
                Long exitExecutedPrice = exitLeg.getExecutedPrice();
                Long entryExecutedPrice = leg.getExecutedPrice() != null ? leg.getExecutedPrice() : leg.getPrice();
                if (exitExecutedPrice != null && entryExecutedPrice != null && (leg.getProfitLoss() == null || leg.getProfitLoss() == 0)) {
                    long legPNL = (long) (processPNL(leg, exitExecutedPrice / (double) AMOUNT_MULTIPLIER, entryExecutedPrice) * AMOUNT_MULTIPLIER);
                    int rows = strategyLegRepository.updateProfitLossById(leg.getId(), legPNL);
                    hasValidLegs = true;
                    logger.info("saveSingleLegPnl: updated open-leg pnl for signalId={}, exchangeInstrumentId={}, openLegId={}, entryPx={}, exitPx={}, pnl={}",
                            exitLeg.getSignal().getId(),
                            exitLeg.getExchangeInstrumentId(),
                            leg.getId(),
                            entryExecutedPrice,
                            exitExecutedPrice,
                            legPNL);
                    logger.info("saveSingleLegPnl: updateProfitLossById affectedRows={} for openLegId={}", rows, leg.getId());
                }
            }
            if (!hasValidLegs) {
                logger.info("saveSingleLegPnl: no eligible open-leg row found for signalId={}, exchangeInstrumentId={} (expected status=exit with profit_loss null/0)",
                        exitLeg.getSignal().getId(), exitLeg.getExchangeInstrumentId());
            }
        } catch (Exception e) {
            logger.error("error in ExitPNL setFinalPNL, error: {}", e.getMessage());
        }
    }
}
