package com.quantlab.signal.strategy;

import com.quantlab.common.entity.EntryDetails;
import com.quantlab.common.entity.ExitDetails;
import com.quantlab.common.entity.Signal;
import com.quantlab.common.entity.Strategy;
import com.quantlab.common.repository.SignalRepository;
import com.quantlab.common.repository.StrategyRepository;
import com.quantlab.common.utils.staticstore.dropdownutils.*;
import com.quantlab.signal.dto.HedgeData;
import com.quantlab.signal.dto.SignalMapperDto;
import com.quantlab.signal.dto.redisDto.MarketData;
import com.quantlab.signal.dto.redisDto.MasterResponseFO;
import com.quantlab.signal.service.CugUsersService;
import com.quantlab.signal.service.GrpcService;
import com.quantlab.signal.service.StrategyService;
import com.quantlab.signal.service.redisService.TouchLineService;
import com.quantlab.signal.utils.*;
import com.quantlab.signal.web.dto.MarketLiveDto;
import com.quantlab.signal.web.service.MarketDataFetch;
import org.hibernate.Hibernate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.*;
import java.util.*;

import static com.quantlab.common.utils.staticstore.AppConstants.*;

@Service("DeltaNeutralStrategyHedge")
public class DeltaNeutralStrategyHedge implements  StrategiesImplementation<DeltaNeutralStrategyHedge>{

    private static final Logger logger = LoggerFactory.getLogger(DeltaNeutralStrategyHedge.class);


    @Autowired
    private BalanceCalculator balanceCalculator;

    @Autowired
    private MarketDataFetch marketDataFetch;

    @Autowired
    private TouchLineService touchLineService;

    @Autowired
    private CommonUtils commonUtils;


    @Autowired
    private SignalService signalService;

    @Autowired
    private StrategyUtils strategyUtils;

    @Autowired
    private SignalRepository signalRepository;

    @Autowired
    private StrategyRepository strategyRepository;

    @Autowired
    private GrpcService grpcService;

    @Autowired
    private StrategyService strategyService;

    @Autowired
    CugUsersService cugUsersService;

    @Override
    public Signal runStrategy(Strategy strategy) {

        try{
            MarketLiveDto marketLive = marketDataFetch.getMarketData(strategy);
            String callkey = strategy.getUnderlying().getName().toUpperCase(Locale.ROOT) + commonUtils.getExpiryShotDateByIndex(strategy.getExpiry(),strategy.getUnderlying().getName(),OptionType.OPTION.getKey())  +"-"+ marketLive.getSyntheticAtm() + "CE";
            String putkey = strategy.getUnderlying().getName().toUpperCase(Locale.ROOT) + commonUtils.getExpiryShotDateByIndex(strategy.getExpiry(),strategy.getUnderlying().getName(),OptionType.OPTION.getKey())  +"-"+ marketLive.getSyntheticAtm() + "PE";
            logger.info(strategy.getUnderlying().getName().toUpperCase(Locale.ROOT) +"DATA = "+ commonUtils.getExpiryShotDateByIndex(strategy.getExpiry(),strategy.getUnderlying().getName(),OptionType.OPTION.getKey()) +"||"+ marketLive.getSyntheticAtm() + "CE");

            System.out.println("Keys for the Delta Neutral Hedge Strategy");
            MasterResponseFO callMaster = marketDataFetch.getMasterResponse(callkey);
            MasterResponseFO putMaster = marketDataFetch.getMasterResponse(putkey);
            MarketData ctouchlineResposne = touchLineService.getTouchLine(String.valueOf(callMaster.getExchangeInstrumentID()));
            MarketData ptouchlineResposne = touchLineService.getTouchLine(String.valueOf(putMaster.getExchangeInstrumentID()));

            Map<String, Integer> lotsSize = balanceCalculator.calculateBalanceDelta(ctouchlineResposne.getDelta() , ptouchlineResposne.getDelta(), strategy.getUnderlying().getName().toUpperCase(Locale.ROOT), true);
            logger.info("Lot Sizes: " + lotsSize);
            System.out.println("Lot Sizes: " + lotsSize);
            System.out.println("Market Live : " + marketLive.getSpotPrice());

            HedgeData callHedgeData = strategyUtils.getHedgeData(ctouchlineResposne.getLTP(),10, SegmentType.CE.getKey(),marketLive.getAtm(),strategy.getUnderlying().getName(),strategy, "LESS_THAN");
            HedgeData putHedgeData =  strategyUtils.getHedgeData(ctouchlineResposne.getLTP(),10, SegmentType.PE.getKey(),marketLive.getAtm(),strategy.getUnderlying().getName(),strategy, "LESS_THAN");


            logger.info(
                    "{} HEDGE DATA = {}||{}CE | Keys for Delta Neutral Hedge : {}{}-{}CE & {}{}-{}PE | Lot Sizes: {} | Market Live: {} | Synthetic Price: {} | signalCount: {}",
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
            signalMapperDto1.setMasterData(callMaster);
            signalMapperDto1.setBuySellFlag(LegSide.SELL.getKey());
            signalMapperDto1.setSegment("NSEFO");
            signalMapperDto1.setCategory(LegType.CALL.getKey());
            signalMapperDto1.setLots((long)lotsSize.get("call"));
            signalMapperDto1.setPositionType(strategy.getPositionType());
            signalMapperDto1.setLegType(LegType.OPEN.getKey());
            signalMapperDto1.setDerivativeType(OptionType.OPTION.getKey());
            signalMapperDto1.setQuantity((int) ((int)lotsSize.get("call")*callMaster.getLotSize()*strategy.getMultiplier()));
            signalMapperDto1.setLegName(callkey);
            signalMapperDto1.setOptionType(SegmentType.CE.getKey());
            signalMapperDto1.setName(callkey);

            SignalMapperDto signalMapperDto2 = new SignalMapperDto();
            signalMapperDto2.setMarketLiveDto(marketLive);
            signalMapperDto2.setTouchlineBinaryResponse(ptouchlineResposne);
            signalMapperDto2.setMasterData(putMaster);
            signalMapperDto2.setBuySellFlag(LegSide.SELL.getKey());
            signalMapperDto2.setSegment("NSEFO");
            signalMapperDto2.setCategory(LegType.PUT.getKey());
            signalMapperDto2.setLots((long)lotsSize.get(LegType.PUT.getKey()));
            signalMapperDto2.setPositionType(strategy.getPositionType());
            signalMapperDto2.setLegType(LegType.OPEN.getKey());
            signalMapperDto2.setDerivativeType(OptionType.OPTION.getKey());
            signalMapperDto2.setQuantity((int) ((int)lotsSize.get("put")*callMaster.getLotSize()*strategy.getMultiplier()));
            signalMapperDto2.setLegName(putkey);
            signalMapperDto2.setName(putkey);
            signalMapperDto2.setOptionType(SegmentType.PE.getKey());


            SignalMapperDto signalMapperDto3 = new SignalMapperDto();
            signalMapperDto3.setMarketLiveDto(marketLive);
            signalMapperDto3.setTouchlineBinaryResponse(callHedgeData.getLiveMarketData());
            signalMapperDto3.setMasterData(callHedgeData.getMasterData());
            signalMapperDto3.setBuySellFlag(LegSide.BUY.getKey());
            signalMapperDto3.setSegment("NSEFO");
            signalMapperDto3.setCategory(LegType.CALL.getKey());
            signalMapperDto3.setPositionType(strategy.getPositionType());
            signalMapperDto3.setLegType(LegType.OPEN.getKey());
            signalMapperDto3.setDerivativeType(OptionType.OPTION.getKey());

            logger.info("Running {} strategy with underlying: {}", strategy.getStrategyTag(), strategy.getUnderlying().getName());
            if (StrategyMapper.ULTA_DELTA_HEDGE.getKey().equalsIgnoreCase(strategy.getStrategyTag())) {
                signalMapperDto3.setLots((long)lotsSize.get(LegType.PUT.getKey()));
                signalMapperDto3.setQuantity((int) ((int)lotsSize.get("put")*callMaster.getLotSize()*strategy.getMultiplier()));
            } else {
                signalMapperDto3.setLots((long)lotsSize.get(LegType.CALL.getKey()));
                signalMapperDto3.setQuantity((int) ((int)lotsSize.get("call")*callMaster.getLotSize()*strategy.getMultiplier()));
            }

            signalMapperDto3.setLegName(callHedgeData.getFinalKey());
            signalMapperDto3.setOptionType(SegmentType.CE.getKey());
            signalMapperDto3.setName(callHedgeData.getFinalKey());

            SignalMapperDto signalMapperDto4 = new SignalMapperDto();
            signalMapperDto4.setMarketLiveDto(marketLive);
            signalMapperDto4.setTouchlineBinaryResponse(putHedgeData.getLiveMarketData());
            signalMapperDto4.setMasterData(putHedgeData.getMasterData());
            signalMapperDto4.setBuySellFlag(LegSide.BUY.getKey());
            signalMapperDto4.setSegment("NSEFO");
            signalMapperDto4.setCategory(LegType.PUT.getKey());
            signalMapperDto4.setPositionType(strategy.getPositionType());
            signalMapperDto4.setLegType(LegType.OPEN.getKey());
            signalMapperDto4.setDerivativeType(OptionType.OPTION.getKey());

            if (StrategyMapper.ULTA_DELTA_HEDGE.getKey().equalsIgnoreCase(strategy.getStrategyTag())) {
                signalMapperDto4.setLots((long)lotsSize.get(LegType.CALL.getKey()));
                signalMapperDto4.setQuantity((int) (lotsSize.get("call") *callMaster.getLotSize()*strategy.getMultiplier()));
            } else {
                signalMapperDto4.setLots((long)lotsSize.get(LegType.PUT.getKey()));
                signalMapperDto4.setQuantity((int) (lotsSize.get("put") *callMaster.getLotSize()*strategy.getMultiplier()));
            }

            signalMapperDto4.setLegName(putHedgeData.getFinalKey());
            signalMapperDto4.setName(putHedgeData.getFinalKey());
            signalMapperDto4.setOptionType(SegmentType.PE.getKey());

            signalMapperDto.add(signalMapperDto1);
            signalMapperDto.add(signalMapperDto3);
            signalMapperDto.add(signalMapperDto2);
            signalMapperDto.add(signalMapperDto4);

            Signal signals = signalService.createSignal(strategy , signalMapperDto );
            strategyRepository.updateSignalCount(strategy.getId());

            if (!strategy.getExecutionType().equalsIgnoreCase(ExecutionTypeMenu.PAPER_TRADING.getKey())
                    && (cugUsersService.isUserInCug(signals.getAppUser().getTenentId()))){
                grpcService.sendSignal(signals);
            }
            return signals;
        } catch (Exception e) {
            signalService.errorCreatingSignal(strategy, e);
            throw new RuntimeException(e.getMessage());
        }
    }
    @Override
    public void exitStrategy(Strategy strategy) {
        try{

        Signal newSignal = signalService.createExit(strategy);
            logger.info("Exit Signal Created");
            logger.info("Exit Signal ID : "+ newSignal.getId() + " , Signal Type : "+ newSignal.getExecutionType() + " , Signal Status : "+ newSignal.getStatus());
        if (newSignal != null && !strategy.getExecutionType().equalsIgnoreCase(ExecutionTypeMenu.PAPER_TRADING.getKey())
                && (cugUsersService.isUserInCug(newSignal.getAppUser().getTenentId()))){
            grpcService.sendExitSignal(newSignal);
        }
        }catch (Exception e){
            logger.error(e.getMessage());
            System.out.println(e.getMessage());
        }

    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
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
                        exitStrategy(strategy);
                    }
                }
            }
        }
    }

}
