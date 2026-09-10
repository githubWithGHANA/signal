package com.quantlab.signal.strategy;

import com.quantlab.common.entity.*;
import com.quantlab.common.repository.SignalRepository;
import com.quantlab.common.repository.StrategyRepository;
import com.quantlab.common.utils.staticstore.dropdownutils.*;
import com.quantlab.common.utils.staticstore.dropdownutils.Status;
import com.quantlab.signal.dto.SignalMapperDto;
import com.quantlab.signal.dto.redisDto.MarketData;
import com.quantlab.signal.dto.redisDto.MasterResponseFO;
import com.quantlab.signal.service.CugUsersService;
import com.quantlab.signal.service.GrpcService;
import com.quantlab.signal.service.StrategyService;
import com.quantlab.signal.service.redisService.SyntheticPriceRepository;
import com.quantlab.signal.service.redisService.TouchLineService;
import com.quantlab.signal.utils.*;
import com.quantlab.signal.web.dto.MarketLiveDto;
import com.quantlab.signal.web.service.MarketDataFetch;
import org.hibernate.Hibernate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.*;
import java.util.*;


@Service("DeltaNeutralStrategy")
public class DeltaNeutralStrategy implements StrategiesImplementation<DeltaNeutralStrategy> {

    private static final Logger logger = LoggerFactory.getLogger(DeltaNeutralStrategy.class);

    @Autowired
    private Delta delta;

    @Autowired
    private BalanceCalculator balanceCalculator;


    @Autowired
    private MarketDataFetch marketDataFetch;

    @Autowired
    private StrategyService strategyService;

    @Autowired
    private CommonUtils commonUtils;

    @Autowired
    private TouchLineService touchLineService;


    @Autowired
    private  SignalRepository signalRepository;


    @Autowired
    SignalService signalService;

    @Autowired
    GrpcService grpcService;

    @Autowired
    StrategyRepository strategyRepository;

    @Autowired
    SyntheticPriceRepository syntheticPriceRepository;

    @Autowired
    CugUsersService cugUsersService;


    @Override
    public Signal runStrategy(Strategy strategy) {
        LocalDate now = LocalDate.now();
        Hibernate.initialize(strategy.getUnderlying());


        try {
            MarketLiveDto marketLive = marketDataFetch.getMarketData(strategy);
            String callkey = strategy.getUnderlying().getName().toUpperCase(Locale.ROOT) + commonUtils.getExpiryShotDateByIndex(strategy.getExpiry(),strategy.getUnderlying().getName(),OptionType.OPTION.getKey())  +"-"+ marketLive.getSyntheticAtm() + "CE";
            String putkey = strategy.getUnderlying().getName().toUpperCase(Locale.ROOT) + commonUtils.getExpiryShotDateByIndex(strategy.getExpiry(),strategy.getUnderlying().getName(),OptionType.OPTION.getKey())  +"-"+ marketLive.getSyntheticAtm() + "PE";
            MasterResponseFO callMaster = marketDataFetch.getMasterResponse(callkey);
            MasterResponseFO putMaster = marketDataFetch.getMasterResponse(putkey);
            MarketData ctouchlineResposne = touchLineService.getTouchLine(String.valueOf(callMaster.getExchangeInstrumentID()));
            MarketData ptouchlineResposne = touchLineService.getTouchLine(String.valueOf(putMaster.getExchangeInstrumentID()));
            Map<String, Integer> lotsSize = balanceCalculator.calculateBalanceDelta(ctouchlineResposne.getDelta() , ptouchlineResposne.getDelta(), strategy.getUnderlying().getName().toUpperCase(Locale.ROOT), false);
            logger.info(
                    "{}DATA = {}||{}CE | Keys for Delta Neutral: {}{}-{}CE & {}{}-{}PE | Lot Sizes: {} | Market Live: {} | Synthetic Price: {} | signalCount: {}",
                    strategy.getUnderlying().getName().toUpperCase(Locale.ROOT),
                    commonUtils.getExpiryShotDateByIndex(strategy.getExpiry(), strategy.getUnderlying().getName(), OptionType.OPTION.getKey()),
                    marketLive.getSyntheticAtm(),
                    strategy.getUnderlying().getName().toUpperCase(Locale.ROOT),
                    marketLive.getSyntheticAtm(),
                    commonUtils.getExpiryShotDateByIndex(strategy.getExpiry(), strategy.getUnderlying().getName(), OptionType.OPTION.getKey()),
                    strategy.getUnderlying().getName().toUpperCase(Locale.ROOT),
                    marketLive.getSyntheticAtm(),
                    commonUtils.getExpiryShotDateByIndex(strategy.getExpiry(), strategy.getUnderlying().getName(), OptionType.OPTION.getKey()),
                    lotsSize,
                    marketLive.getSpotPrice(),
                    marketLive.getSyntheticPrice(),
                    strategy.getSignalCount()
            );

            List<SignalMapperDto> signalMapperDto = new ArrayList<>();
            SignalMapperDto signalMapperDto1 = new SignalMapperDto();
            signalMapperDto1.setMarketLiveDto(marketLive);
            signalMapperDto1.setTouchlineBinaryResponse(ctouchlineResposne);
            signalMapperDto1.setLegName(callkey);
            signalMapperDto1.setMasterData(callMaster);
            signalMapperDto1.setBuySellFlag(LegSide.SELL.getKey());
            signalMapperDto1.setSegment("NSEFO");
            signalMapperDto1.setCategory(LegType.CALL.getKey());
            signalMapperDto1.setOptionType(SegmentType.CE.getKey());
            signalMapperDto1.setPositionType(strategy.getPositionType());
            signalMapperDto1.setLots((long) lotsSize.get("call"));
            signalMapperDto1.setLegType(LegType.OPEN.getKey());
            signalMapperDto1.setDerivativeType(OptionType.OPTION.getKey());
            signalMapperDto1.setQuantity((int) ((int)lotsSize.get("call")*callMaster.getLotSize()*strategy.getMultiplier()));

            SignalMapperDto signalMapperDto2 = new SignalMapperDto();
            signalMapperDto2.setMarketLiveDto(marketLive);
            signalMapperDto2.setTouchlineBinaryResponse(ptouchlineResposne);
            signalMapperDto2.setMasterData(putMaster);
            signalMapperDto2.setLegName(putkey);
            signalMapperDto2.setBuySellFlag(LegSide.SELL.getKey());
            signalMapperDto2.setSegment("NSEFO");
            signalMapperDto2.setCategory(LegType.PUT.getKey());
            signalMapperDto2.setPositionType(strategy.getPositionType());
            signalMapperDto2.setLots((long) lotsSize.get("put"));
            signalMapperDto2.setLegType(LegType.OPEN.getKey());
            signalMapperDto2.setOptionType(SegmentType.PE.getKey());
            signalMapperDto2.setDerivativeType(OptionType.OPTION.getKey());
            signalMapperDto2.setQuantity((int) ((int)lotsSize.get("put")*putMaster.getLotSize()*strategy.getMultiplier()));

            signalMapperDto.add(signalMapperDto1);
            signalMapperDto.add(signalMapperDto2);

            Signal signals = signalService.createSignal(strategy, signalMapperDto);
            strategyRepository.updateSignalCount(strategy.getId());
            if (!strategy.getExecutionType().equalsIgnoreCase(ExecutionTypeMenu.PAPER_TRADING.getKey())
                    && (cugUsersService.isUserInCug(signals.getAppUser().getTenentId()))){
                grpcService.sendSignal(signals);
            }
            return signals;
        } catch (Exception e) {
            signalService.errorCreatingSignal(strategy, e);
            throw new RuntimeException(e);
        }
    }

    @Override
    public void exitStrategy(Strategy strategy) {

        try {
            logger.info("Exiting strategy: {}", strategy.getId());
            // has to place logger
            Signal newSignal = signalService.createExit(strategy);
            if (newSignal != null && !strategy.getExecutionType().equalsIgnoreCase(ExecutionTypeMenu.PAPER_TRADING.getKey())
                    && (cugUsersService.isUserInCug(newSignal.getAppUser().getTenentId()))){
                grpcService.sendExitSignal(newSignal);
                logger.info("Exit signal sent for strategy: {}", strategy.getId());
            } else {
                logger.info("Exit skipped for strategy: {} Paper Trading or No Signal Created", strategy.getId());
                // has to place the logger
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW , isolation = Isolation.READ_COMMITTED)
    public void check(Strategy strategy) {
        if (strategyService.inHouseEntryCheck(strategy)) {
            logger.info("InHouse Entry Check Passed for strategy: {}", strategy.getId());
            this.runStrategy(strategy);
        } else if (strategy.getStatus().equalsIgnoreCase(Status.LIVE.getKey())) {
            if (strategy.getManualExitType().equalsIgnoreCase(ManualExit.ENABLED.getKey())) {
                this.exitStrategy(strategy);
            }else {
                Long topSignalId = signalRepository.findTopIdByStrategyIdAndStatusOrderByCreatedAtDesc(strategy.getId(), SignalStatus.LIVE.getKey());
                if (topSignalId != null) {
                    boolean check = strategyService.deltaNeutralExitCheckBySignalId(strategy.getUnderlying().getName(), topSignalId, strategy);
                    if (check) {
                        this.exitStrategy(strategy);
                    }
                }
            }
        }
    }
}
