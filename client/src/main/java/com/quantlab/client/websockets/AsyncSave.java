package com.quantlab.client.websockets;

import com.quantlab.common.dto.SignalPNLDTO;
import com.quantlab.common.dto.StrategyLegPNLDTO;
import com.quantlab.common.repository.SignalRepository;
import com.quantlab.common.repository.StrategyLegRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class AsyncSave {
    private static final Logger logger = LoggerFactory.getLogger(AsyncSave.class);


    @Autowired
    StrategyLegRepository strategyLegRepository;

    @Autowired
    SignalRepository signalRepository;


    @Async
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void saveLegsInDB(List<StrategyLegPNLDTO> savingStrategyLegsDTOList, List<SignalPNLDTO> allLiveSignals) {
        try {
            allLiveSignals.forEach((signal) -> signalRepository.updateProfitLossAndIndexNowById(signal.getId(), signal.getProfitLoss(),signal.getLatestIndexPrice()));
            if (!savingStrategyLegsDTOList.isEmpty()) {
                for (StrategyLegPNLDTO dto: savingStrategyLegsDTOList) {
                    strategyLegRepository.updateRealtimeLegData(
                            dto.getId(), dto.getLtp(), dto.getProfitLoss(),
                            dto.getCurrentIV(), dto.getCurrentDelta());
                }
            }
        } catch (Exception e) {
            logger.error("error saving data in P&L socket "+e.getMessage());
        }
    }
}
