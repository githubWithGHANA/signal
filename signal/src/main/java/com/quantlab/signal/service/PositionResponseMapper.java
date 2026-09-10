package com.quantlab.signal.service;


import com.market.proto.tr.PositionResponse;
import com.quantlab.common.entity.Position;
import com.quantlab.common.entity.UserAuthConstants;
import com.quantlab.common.repository.PositionRepository;
import com.quantlab.common.repository.StrategyLegRepository;
import com.quantlab.common.repository.UserAuthConstantsRepository;
import com.quantlab.common.utils.staticstore.dropdownutils.ExecutionTypeMenu;
import com.quantlab.signal.dto.redisDto.MasterResponseFO;
import com.quantlab.signal.service.redisService.TouchLineService;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.transaction.Transactional;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static com.quantlab.common.utils.staticstore.AppConstants.AMOUNT_MULTIPLIER;

@Service
@Transactional
public class PositionResponseMapper {


    private static final Logger logger = LogManager.getLogger(PositionResponseMapper.class);

    @PersistenceContext
    private EntityManager entityManager;

    @Autowired
    PositionRepository positionRepository;

    @Autowired
    TouchLineService touchLineService;

    @Autowired
    StrategyLegRepository strategyLegRepository;

    @Autowired
    UserAuthConstantsRepository userAuthConstantsRepository;

    public  Position mapToXtsPosition(com.market.proto.xts.PositionResponseStream responseStream) {


        Instant startOfDay = LocalDate.now().atStartOfDay().toInstant(ZoneOffset.UTC);
        Instant endOfDay = startOfDay.plusSeconds(86400);
        int id = responseStream.getExchangeInstrumentID();
        Optional<UserAuthConstants> userAuthConstants = null;
        if (!responseStream.getLoginID().isEmpty()) {
            userAuthConstants = userAuthConstantsRepository.findByClientId(responseStream.getLoginID());
        }
        if (userAuthConstants == null || !userAuthConstants.isPresent())
            return null;

        List<Position> positionsList = positionRepository.findByAppUserIdAndDeployedOnTodayAndExchangeInstrumentId(
                userAuthConstants.get().getAppUser().getId(),
                startOfDay,
                endOfDay,
                String.valueOf(id)
                );

        Position position = positionsList.isEmpty()? new Position(): positionsList.get(0);
        position.setExchangeSegment(responseStream.getExchangeSegment());
        position.setExchangeInstrumentId(String.valueOf(responseStream.getExchangeInstrumentID()));

        position.setAppUser(userAuthConstants.get().getAppUser());
        position.setUserAdmin(userAuthConstants.get().getAppUser().getAdmin());
        position.setDeployedOn(Instant.now());

        position.setPostionId(responseStream.getUniqueKey()); //currently no positionId from response
        position.setExecutionType(ExecutionTypeMenu.LIVE_TRADING.getKey());
        position.setProductType(responseStream.getProductType());
        position.setLongPosition(responseStream.getLongPosition());
        position.setShortPosition(responseStream.getShortPosition());
        position.setNetPosition(responseStream.getNetPosition());
        long mediator = (long) ((Float.parseFloat(responseStream.getBuyAveragePrice())) * AMOUNT_MULTIPLIER);
        position.setBuyAveragePrice(Long.toString(mediator));
        mediator = (long) ((Float.parseFloat(responseStream.getSellAveragePrice())) * AMOUNT_MULTIPLIER);
        position.setAverageSellPrice(Long.toString(mediator));
        position.setBuyValue(responseStream.getBuyValue());
        position.setSellValue(responseStream.getSellValue());
        position.setNetValue(responseStream.getNetValue());
        mediator = (long) ((Float.parseFloat(responseStream.getUnrealizedMTM())) * AMOUNT_MULTIPLIER);
        position.setUnrealizedMTM(Long.toString(mediator));
        mediator = (long) ((Float.parseFloat(responseStream.getRealizedMTM())) * AMOUNT_MULTIPLIER);
        position.setRealizedMTM(Long.toString(mediator));
        mediator = (long) ((Float.parseFloat(responseStream.getMtm())) * AMOUNT_MULTIPLIER);
        position.setMtm(Long.toString(mediator));
        mediator = (long) ((Float.parseFloat(responseStream.getBep())) * AMOUNT_MULTIPLIER);
        position.setBep(Long.toString(mediator));
        mediator = (long) ((Float.parseFloat(responseStream.getSumOfTradedQuantityAndPriceBuy())) * AMOUNT_MULTIPLIER);
        position.setSumOfTradedQuantityAndPriceBuy(Long.toString(mediator));
        mediator = (long) ((Float.parseFloat(responseStream.getSumOfTradedQuantityAndPriceSell())) * AMOUNT_MULTIPLIER);
        position.setSumOfTradedQuantityAndPriceSell(Long.toString(mediator));
        position.setUniqueKey(responseStream.getUniqueKey());
        position.setMessageCode(responseStream.getMessageCode());
        position.setMessageVersion(responseStream.getMessageVersion());
        position.setTokenID(responseStream.getTokenID());
        position.setApplicationType(responseStream.getApplicationType());
        updateLegs(position, startOfDay, endOfDay);
        positionUpdate(position);
        return position;
    }

    private void updateLegs(Position position, Instant startTime, Instant endTime) {
        Long netQuantity = position.getNetPosition() != null ? position.getNetPosition().longValue() : 0L;
        int updatedLegsCount = strategyLegRepository.updatePositionQuantity(netQuantity,
                Long.valueOf(position.getExchangeInstrumentId()),
                startTime,
                endTime,
                position.getAppUser().getId());
        logger.info("updatedLegsCount for position quantity = "+updatedLegsCount);
    }

    private void positionUpdate(Position position) {
        MasterResponseFO responseFO = touchLineService.getMaster_(position.getExchangeInstrumentId());
        if (responseFO!= null) {
            position.setInstrumentName(responseFO.getName());
            position.setStrikePrice(responseFO.getStrikePrice());
            position.setContractExpiration(responseFO.getContractExpiration());
        }
    }



    public List<Position> mapToTrPosition(com.market.proto.tr.PositionResponseStream response) {
       // logger.info("Mapping TR Position Response to Position Entity for response: " + response);

        if (response == null) {
            logger.error("Received null PositionResponseStream from TR");
            return null;
        }
        Instant startOfDay = LocalDate.now().atStartOfDay().toInstant(ZoneOffset.UTC);
        Instant endOfDay = startOfDay.plusSeconds(86400);

        List<PositionResponse> listPositions = response.getPositionsList();

        Optional<UserAuthConstants> userAuthConstants;
        if (!response.getUserId().isEmpty()) {
            userAuthConstants = userAuthConstantsRepository.findByClientId(response.getUserId());
        } else {
            userAuthConstants = null;
        }
        if (userAuthConstants == null || !userAuthConstants.isPresent())
            return null;

        return listPositions.stream().map(
        responseStream -> {
                    int id = Integer.parseInt(responseStream.getToken());

                    List<Position> positionsList = positionRepository.findByAppUserIdAndDeployedOnTodayAndExchangeInstrumentId(
                            userAuthConstants.get().getAppUser().getId(),
                            startOfDay,
                            endOfDay,
                            String.valueOf(id)
                    );

                    Position position = positionsList.isEmpty()? new Position(): positionsList.get(0);
                    position.setExchangeSegment(responseStream.getExchange());
                    position.setExchangeInstrumentId(responseStream.getToken());

                    position.setAppUser(userAuthConstants.get().getAppUser());
                    position.setUserAdmin(userAuthConstants.get().getAppUser().getAdmin());
                    position.setDeployedOn(Instant.now());

                    position.setPostionId(responseStream.getUniqueKey()); //currently no positionId from response
                    position.setExecutionType(ExecutionTypeMenu.LIVE_TRADING.getKey());
                    position.setProductType(responseStream.getType());
                    position.setLongPosition(Double.valueOf(responseStream.getBqty()));
                    position.setShortPosition(Double.valueOf(responseStream.getSqty()));
                    position.setNetPosition(Double.valueOf(responseStream.getNetqty()));
                    long mediator = (long) ((Float.parseFloat(responseStream.getBuyavgprc().replaceAll(",",""))) * AMOUNT_MULTIPLIER);
                    position.setBuyAveragePrice(Long.toString(mediator));
                    mediator = (long) ((Float.parseFloat(responseStream.getSellavgprc().replaceAll(",",""))) * AMOUNT_MULTIPLIER);
                    position.setAverageSellPrice(Long.toString(mediator));
                    position.setBuyValue(responseStream.getFillbuyamt().replaceAll(",",""));
                    position.setSellValue(responseStream.getFillsellamt().replaceAll(",",""));
                    position.setNetValue(responseStream.getNetamt());
                    mediator = (long) ((Float.parseFloat(responseStream.getOvrlRlzdMtoM().replaceAll(",",""))) * AMOUNT_MULTIPLIER);
                    position.setUnrealizedMTM(Long.toString(mediator));
                    mediator = (long) ((Float.parseFloat(responseStream.getOvrlUnRlzdMtoM().replaceAll(",",""))) * AMOUNT_MULTIPLIER);
                    position.setRealizedMTM(Long.toString(mediator));
                    mediator = (long) ((Float.parseFloat(responseStream.getOverallMtoM().replaceAll(",",""))) * AMOUNT_MULTIPLIER);
                    position.setMtm(Long.toString(mediator));
                    mediator = (long) ((Float.parseFloat(responseStream.getBep().replaceAll(",",""))) * AMOUNT_MULTIPLIER);
                    position.setBep(Long.toString(mediator));
//                    mediator = (long) ((Float.parseFloat(responseStream.getSumOfTradedQuantityAndPriceBuy())) * AMOUNT_MULTIPLIER);
//                    position.setSumOfTradedQuantityAndPriceBuy(Long.toString(mediator));
//                    mediator = (long) ((Float.parseFloat(responseStream.getSumOfTradedQuantityAndPriceSell())) * AMOUNT_MULTIPLIER);
//                    position.setSumOfTradedQuantityAndPriceSell(Long.toString(mediator));
                    position.setUniqueKey(responseStream.getUniqueKey());
//                    position.setMessageCode(Integer.valueOf(responseStream.getPcode()));
//                    position.setMessageVersion(responseStream.getMessageVersion());
//                    position.setTokenID(responseStream.getTokenID());
//                    position.setApplicationType(responseStream.getApplicationType());
                    updateLegs(position, startOfDay, endOfDay);
                    positionUpdate(position);
                    return position;

                }
        ).toList();

    }

}
