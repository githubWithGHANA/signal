package com.quantlab.signal.service;

import com.quantlab.common.entity.*;
import com.quantlab.common.repository.*;
import com.quantlab.common.utils.staticstore.dropdownutils.*;
import com.quantlab.common.utils.staticstore.dropdownutils.Status;
import com.quantlab.signal.dto.TrOrdersDto;
import com.quantlab.signal.dto.TrPlaceOrderDto;
import com.quantlab.signal.dto.XtsOrdersDto;
import com.quantlab.signal.dto.XtsPlaceOrderDto;
import com.quantlab.signal.dto.redisDto.MasterResponseFO;
import com.quantlab.signal.grpcserver.OrderPlaceGrpc;
import com.quantlab.signal.utils.AuthUtils;
import com.quantlab.signal.utils.CommonUtils;
import com.quantlab.signal.web.service.MarketDataFetch;
import org.hibernate.Hibernate;
import org.modelmapper.ModelMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

import static com.quantlab.common.utils.staticstore.AppConstants.*;

@Service
public class GrpcService {
    private static final Logger logger = LoggerFactory.getLogger(GrpcService.class);

    @Autowired
    ModelMapper modelMapper;

    @Autowired
    SignalRepository signalRepository;

    @Autowired
    StrategyRepository strategyRepository;

    @Autowired
    OrderPlaceGrpc orderPlaceGrpc;

    @Autowired
    UserAuthConstantsRepository userAuthConstantsRepository;

    @Autowired
    private AppUserLogInfoRepository appUserLogInfoRepository;

    @Autowired
    StrategyLegRepository strategyLegRepository;

    @Autowired
    DeploymentErrorsRepository deploymentErrorsRepository;

    @Autowired
    GrpcErrorService grpcErrorService;

    @Autowired
    MarketDataFetch marketDataFetch;

    @Autowired
    CommonUtils commonUtils;

    @Async
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void sendSignal(Signal signal) {
        logger.info("Initiating sendSignal process for signal ID: {}", signal.getId());
        Hibernate.initialize(signal.getAppUser());
        UserAuthConstants userAuthConstants = userAuthConstantsRepository.findByAppUserUserId(signal.getAppUser().getUserId());

        if (userAuthConstants.getXtsClient()) {
            sendXtsSignal(signal, userAuthConstants);
        } else {
            if (userAuthConstants.getIsCugUser())
                sendTrNetMagicSignal(signal, userAuthConstants);
            else
                sendTrSTTSignal(signal, userAuthConstants);
        }
    }

    private void sendXtsSignal(Signal signal, UserAuthConstants userAuthConstants) {
        try {
            XtsPlaceOrderDto xtsPlaceOrderDto = createXtsPlaceOrderDto(signal, userAuthConstants);
            List<XtsOrdersDto> xtsOrders = createXtsOrders(signal, userAuthConstants);
            xtsPlaceOrderDto.setOrders(xtsOrders);

            com.market.proto.xts.PlaceOrderResponse res = orderPlaceGrpc.placeOrder(xtsPlaceOrderDto);
            logger.info("XTS Place order response received for signal ID: {}, Response: {}", signal.getId(), res);
            grpcErrorService.processGrpcResponse(res, signal);
        } catch (Exception e) {
            handleSignalError(e, signal, "XTS");
        }
    }

    private void sendTrNetMagicSignal(Signal signal, UserAuthConstants userAuthConstants) {
        try {
            TrPlaceOrderDto trPlaceOrderDto = createTrPlaceOrderDto(signal, userAuthConstants);
            List<TrOrdersDto> trOrders = createTrOrders(signal, userAuthConstants);
            trPlaceOrderDto.setOrders(trOrders);

            com.market.proto.tr.PlaceOrderResponse res = orderPlaceGrpc.placeOrderTRNetMagic(trPlaceOrderDto);
            logger.info("TR NetMagic Place order response received for signal ID: {}, strategyID: {} , Response: {}", signal.getId(),signal.getStrategy().getId(), res);
            grpcErrorService.processGrpcResponse(res, signal, trOrders);
        } catch (Exception e) {
            handleSignalError(e, signal, "TR");
        }
    }

    private void sendTrSTTSignal(Signal signal, UserAuthConstants userAuthConstants) {
        try {
            TrPlaceOrderDto trPlaceOrderDto = createTrPlaceOrderDto(signal, userAuthConstants);
            List<TrOrdersDto> trOrders = createTrOrders(signal, userAuthConstants);
            trPlaceOrderDto.setOrders(trOrders);

            com.market.proto.tr.PlaceOrderResponse res = orderPlaceGrpc.placeOrderTRSTT(trPlaceOrderDto);
            logger.info("TR STT Place order response received for signal ID: {}, strategyID: {} , Response: {}", signal.getId(),signal.getStrategy().getId(), res);
            grpcErrorService.processGrpcResponse(res, signal, trOrders);
        } catch (Exception e) {
            handleSignalError(e, signal, "TR");
        }
    }

    private XtsPlaceOrderDto createXtsPlaceOrderDto(Signal signal, UserAuthConstants userAuthConstants) {
        XtsPlaceOrderDto dto = new XtsPlaceOrderDto();
        dto.setSignalID(signal.getId().toString());
        dto.setToken("");
        dto.setExitFlag(false);
        dto.setAppKey(userAuthConstants.getXtsAppKey());
        dto.setSecretKey(userAuthConstants.getXtsSecretKey());
        dto.setTenantID(userAuthConstants.getClientId());
        if(isNotAdjustmentOrder(signal))
            dto.setRequiredCapital((Long)(signal.getStrategy().getMinCapital() / AMOUNT_MULTIPLIER) * signal.getStrategy().getMultiplier());
        return dto;
    }

    private boolean isNotAdjustmentOrder(Signal signal) {
        for (StrategyLeg leg : signal.getSignalLegs()) {
            if (!(leg.getExchangeStatus().equalsIgnoreCase(LegExchangeStatus.CREATED.getKey())
                    || leg.getExchangeStatus().equalsIgnoreCase(LegExchangeStatus.ERROR_PLACING_ORDER.getKey())
                    || leg.getStatus().equalsIgnoreCase(LegStatus.TYPE_OPEN.getKey())
                    || leg.getStatus().equalsIgnoreCase(Status.ERROR.getKey()))) {
                return false;
            }
        }
        return true;
    }

    private TrPlaceOrderDto createTrPlaceOrderDto(Signal signal, UserAuthConstants userAuthConstants) {
        try {
            TrPlaceOrderDto dto = new TrPlaceOrderDto();

//            String trToken = AuthUtils.decryptData(userAuthConstants.getTrToken());
//            String userSession = AuthUtils.decryptData(userAuthConstants.getUserSessionId());
//            String jsessionId = AuthUtils.decryptData(userAuthConstants.getJsessionId());
            dto.setSignalID(signal.getId().toString());
            dto.setTrToken("");
            dto.setExitFlag(false);
            dto.setCugUser(userAuthConstants.getIsCugUser());
            dto.setUserSessionID("");
            dto.setJSessionID("");
            dto.setTenantID(userAuthConstants.getClientId());
            dto.setBrokerName(userAuthConstants.getBrokerName());
            dto.setBranchId(userAuthConstants.getBranchId());
            if (isNotAdjustmentOrder(signal))
                dto.setRequiredCapital((signal.getStrategy().getMinCapital() / AMOUNT_MULTIPLIER) * signal.getStrategy().getMultiplier());
            return dto;
        }catch (Exception e){
            logger.error("Error creating TrPlaceOrderDto: {}", e.getMessage());
            logger.info("Signal ID: {}, User ID: {} , JrToken: {} trToken: {}", signal.getId(), signal.getAppUser().getUserId() , userAuthConstants.getJsessionId(), userAuthConstants.getTrToken());
            throw new RuntimeException("Error creating TrPlaceOrderDto", e);
        }
    }

    private List<XtsOrdersDto> createXtsOrders(Signal signal, UserAuthConstants userAuthConstants) {
        Hibernate.initialize(signal.getStrategy());
        Strategy strategy = signal.getStrategy();

        return signal.getSignalLegs().stream().filter(dao->dao.getExchangeStatus().equalsIgnoreCase(LegExchangeStatus.CREATED.getKey())
                && dao.getLegType().equalsIgnoreCase(LegType.OPEN.getKey())).map(dto -> {

                XtsOrdersDto order = new XtsOrdersDto();
                order.setClientID(userAuthConstants.getClientId());
                order.setUserID(userAuthConstants.getClientId());
                order.setExchangeSegment(dto.getSegment());
                order.setExchangeInstrumentId(dto.getExchangeInstrumentId().intValue());
                order.setOrderUniqueIdentifier("QO_" + signal.getId() + "_" + dto.getId().toString());
                order.setOrderType("LIMIT");
                order.setOrderSide(dto.getBuySellFlag().toUpperCase());
                order.setTimeInForce("DAY");
                int quantity = (int) (dto.getLotSize() * dto.getNoOfLots());
                order.setOrderQuantity(quantity);
                order.setNoLots(Math.toIntExact(dto.getNoOfLots()));
                order.setLotSize(dto.getLotSize().intValue());
                order.setMultiply(strategy.getMultiplier().intValue());
                order.setProductType(strategy.getPositionType().equalsIgnoreCase("Intraday") ? "MIS" : "NRML");
                order.setLimitPrice(dto.getPrice());
                order.setAlgoID(strategy.getAlgoId());
                order.setAlgoCategory(strategy.getAlgoCategory());

                return order;
        }).toList();
    }

    private List<TrOrdersDto> createTrOrders(Signal signal, UserAuthConstants userAuthConstants) {
        Hibernate.initialize(signal.getStrategy());
        Strategy strategy = signal.getStrategy();

        Optional<AppUserLogInfo> latestLogInfoOpt = appUserLogInfoRepository.findTopByAppUserOrderByLoggedinTimeDesc(userAuthConstants.getAppUser());

        return signal.getSignalLegs().stream().filter(dao->((dao.getExchangeStatus().equalsIgnoreCase(LegExchangeStatus.CREATED.getKey())))
                && dao.getLegType().equalsIgnoreCase(LegType.OPEN.getKey())).map(dto -> {
            TrOrdersDto order = new TrOrdersDto();

            String tSym = commonUtils.getTrTradingSymbol(dto.getName());

            logger.info("Creating TR order for signal ID: {}, strategy ID: {}, leg ID: {} trading symbol: {}", signal.getId(), strategy.getId(),dto.getId() ,  tSym);
           // logger.info("Creating TR order for signal ID: {}, strategy ID: {}, trading symbol: {}", signal.getId(), strategy.getId(), tSym);
            // Map common fields
            order.setUserId(userAuthConstants.getClientId());
            order.setAccountId(userAuthConstants.getClientId());
            order.setProductAlias(userAuthConstants.getProductAlias());
            order.setOrderUniqueIdentifier("QO_" + signal.getId() + "_" + dto.getId().toString());
            order.setUniqueKey("QO_" + signal.getId() + "_" + dto.getId().toString());
            order.setTransactionType("BUY".equalsIgnoreCase(dto.getBuySellFlag()) ? "B" : "S");
            order.setPriceType("L");

            int quantity = (int) (dto.getLotSize() * dto.getNoOfLots());
            order.setQuantity(quantity);
            order.setNoLots(Math.toIntExact(dto.getNoOfLots()));
            order.setLotSize(dto.getLotSize().intValue());
            order.setMultiply(strategy.getMultiplier().intValue());

            // Set values from AppUserLogInfo
            latestLogInfoOpt.ifPresent(logInfo -> {
                order.setUserAgent(logInfo.getUserAgent());
                order.setIpAddress(logInfo.getMechineId());
            });

            MasterResponseFO master = marketDataFetch.getMasterResponse(dto.getName());
            order.setDateDays("NA");
//            order.setExchange(dto.getSegment());
            String exchange = dto.getSegment();
            logger.info("Setting exchange for order: {}, trading symbol: {} , master name : {}", exchange, tSym , master.getName());
            if (exchange != null) {
                if (exchange.startsWith("NSE")) {
                    logger.info("Setting exchange to NFO for trading symbol: {}", tSym);
                    order.setExchange("NFO");
                    order.setTradingSymbol(tSym);
                } else if (exchange.startsWith("BSE")) {
                    logger.info("Setting exchange to BFO for trading symbol: {}", master.getName());
                    order.setExchange("BFO");
                    order.setTradingSymbol(master.getName());
                } else {
                    logger.info("Setting exchange to NFO last else block for trading symbol: {}", tSym);
                    order.setExchange("NFO");
                    order.setTradingSymbol(tSym);
                }
            }

            order.setProductCode(strategy.getPositionType().equalsIgnoreCase("Intraday") ? "MIS" : "NRML");
            order.setPrice(dto.getPrice());
            order.setSegment("FO");
            order.setRetention("DAY");
            order.setMarketProtection("NA");
            order.setDisclosedQuantity(0);
            order.setMinimumQuantity(0);
            order.setPositionSquareOffFlag("N");
            order.setAfterMarketOrder("NO");
            order.setTriggerPrice(dto.getPrice());
            order.setOrderSource("MOB");
            order.setUserTag("NA");
            order.setCriteriaAttribute("NO");
            order.setRemarks("NA");
            order.setTokenNo(String.valueOf(master.getExchangeInstrumentID()));

            if (strategy.getAlgoId() != null) order.setExchangeAlgoId(strategy.getAlgoId());
            if (strategy.getAlgoCategory() != null) order.setExchangeAlgoCategory(strategy.getAlgoCategory());

            logger.info("TR order created for signal ID: {}, strategy ID: {}, trading symbol: {} , old trading symbol: {}", signal.getId(), strategy.getId(), order.getTradingSymbol() , tSym);

            logger.info("Order created: {}", order);
            return order;
        }).toList();
    }


    private void handleSignalError(Exception e, Signal signal, String protocol) {
        String errorMessage = e.getMessage() != null ? e.getMessage() : RUN_TIME_EXCEPTION + ": error while Sending " + protocol + " Signal";
        logger.error("Error while sending {} Signal: {}", protocol, e.getMessage());
        logger.error("Error in signal {}: {}", signal.getId(), errorMessage);
        grpcErrorService.placingOrderLogs(ERROR_PLACING_ORDER_DESCRIPTION, signal, Status.ERROR.getKey());
    }

    @Async
    public void sendExitSignal(Signal signal) {
        logger.info("Initiating sendExitSignal process for signal ID: {}", signal.getId());
        Hibernate.initialize(signal.getAppUser());
        UserAuthConstants userAuthConstants = userAuthConstantsRepository.findByAppUserUserId(signal.getAppUser().getUserId());

        if (userAuthConstants.getXtsClient()) {
            sendXtsExitSignal(signal, userAuthConstants);
        } else {
            sendTrExitSignal(signal, userAuthConstants);
        }
        updateSignalLegs(signal);
    }

    @Transactional
    public void updateSignalLegs(Signal signal) {
        signal.getSignalLegs().stream()
                .filter(leg -> Status.PENDING.getKey().equalsIgnoreCase(leg.getStatus()))
                .forEach(leg -> {
                    leg.setStatus(Status.PENDING.getKey());
                    strategyLegRepository.save(leg);
                });

    }

    private void sendXtsExitSignal(Signal signal, UserAuthConstants userAuthConstants) {
        try {
            XtsPlaceOrderDto xtsPlaceOrderDto = createXtsExitOrderDto(signal, userAuthConstants);
            List<XtsOrdersDto> xtsOrders = createXtsExitOrders(signal, userAuthConstants);
            xtsPlaceOrderDto.setOrders(xtsOrders);

            com.market.proto.xts.PlaceOrderResponse res = orderPlaceGrpc.placeOrder(xtsPlaceOrderDto);
            logger.info("XTS Exit order response received for signal ID: {}, Response: {}", signal.getId(), res);
            grpcErrorService.processGrpcResponse(res, signal);
        } catch (Exception e) {
            handleSignalError(e, signal, "XTS Exit");
        }
    }

    private void sendTrExitSignal(Signal signal, UserAuthConstants userAuthConstants) {
        try {
            TrPlaceOrderDto trPlaceOrderDto = createTrExitOrderDto(signal, userAuthConstants);
            List<TrOrdersDto> trOrders = createTrExitOrders(signal, userAuthConstants);
            trPlaceOrderDto.setOrders(trOrders);
            com.market.proto.tr.PlaceOrderResponse res = null;
            if (userAuthConstants.getIsCugUser())
                res = orderPlaceGrpc.placeOrderTRNetMagic(trPlaceOrderDto);
            else
                res = orderPlaceGrpc.placeOrderTRSTT(trPlaceOrderDto);
            logger.info("TR Exit order response received for signal ID: {}, Response: {}", signal.getId(), res);
            grpcErrorService.processGrpcResponse(res, signal, trOrders);
        } catch (Exception e) {
            handleSignalError(e, signal, "TR Exit");
        }
    }

    private XtsPlaceOrderDto createXtsExitOrderDto(Signal signal, UserAuthConstants userAuthConstants) {
        XtsPlaceOrderDto dto = new XtsPlaceOrderDto();
        dto.setSignalID(signal.getId().toString());
        dto.setToken("");
        dto.setExitFlag(true);
        dto.setAppKey(userAuthConstants.getXtsAppKey());
        dto.setSecretKey(userAuthConstants.getXtsSecretKey());
        dto.setTenantID(userAuthConstants.getClientId());
        return dto;
    }

    private TrPlaceOrderDto createTrExitOrderDto(Signal signal, UserAuthConstants userAuthConstants) throws Exception {
        try {
            TrPlaceOrderDto dto = new TrPlaceOrderDto();

//            String trToken = AuthUtils.decryptData(userAuthConstants.getTrToken());
//            String userSession = AuthUtils.decryptData(userAuthConstants.getUserSessionId());
//            String jsessionId = AuthUtils.decryptData(userAuthConstants.getJsessionId());
            dto.setSignalID(signal.getId().toString());
            dto.setTrToken("");
            dto.setExitFlag(true);
            dto.setCugUser(userAuthConstants.getIsCugUser());
            dto.setUserSessionID("");
            dto.setJSessionID("");
            dto.setTenantID(userAuthConstants.getClientId());
            dto.setBrokerName(userAuthConstants.getBrokerName());
            dto.setBranchId(userAuthConstants.getBranchId());
            return dto;
        }catch (Exception e) {
            logger.error("Error creating TrPlaceOrderDto for exit order: {}", e.getMessage());
            logger.info("Signal ID: {}, User ID: {} , JrToken: {} trToken: {}", signal.getId(), signal.getAppUser().getUserId(), userAuthConstants.getJsessionId(), userAuthConstants.getTrToken());
            throw new Exception("Error creating TrPlaceOrderDto for exit order", e);
        }
    }

    private List<XtsOrdersDto> createXtsExitOrders(Signal signal, UserAuthConstants userAuthConstants) {
        Hibernate.initialize(signal.getStrategy());
        Strategy strategy = signal.getStrategy();

        return signal.getSignalLegs().stream()
                .filter(dto -> (dto.getLegType().equalsIgnoreCase(LegStatus.EXIT.getKey())
                        && (dto.getExchangeStatus().equalsIgnoreCase(LegExchangeStatus.CREATED.getKey()))))
                .map(dto -> {
                    XtsOrdersDto order = new XtsOrdersDto();
                    String buySellFlag = dto.getBuySellFlag().equalsIgnoreCase(OrderTypeMenu.BUY.getKey()) ? OrderTypeMenu.SELL.getKey().toUpperCase() : OrderTypeMenu.BUY.getKey().toUpperCase();

                    order.setClientID(userAuthConstants.getClientId());
                    order.setUserID(userAuthConstants.getClientId());
                    order.setExchangeSegment(dto.getSegment());
                    order.setExchangeInstrumentId(dto.getExchangeInstrumentId().intValue());
                    order.setOrderUniqueIdentifier("QO_"+signal.getId()+"_"+dto.getId().toString());
                    order.setOrderType("LIMIT");
                    order.setOrderSide(buySellFlag);
                    order.setTimeInForce("DAY");

                    int quantity = (int) (dto.getLotSize()*dto.getNoOfLots());
                    order.setOrderQuantity(quantity);
                    order.setNoLots(Math.toIntExact(dto.getNoOfLots()));
                    order.setLotSize(dto.getLotSize().intValue());
                    order.setMultiply(signal.getMultiplier().intValue());
                    order.setProductType(signal.getPositionType().equalsIgnoreCase("Intraday") ? "MIS" :  "NRML");
                    order.setLimitPrice(dto.getPrice());
                    order.setAlgoID(strategy.getAlgoId());
                    order.setAlgoCategory(strategy.getAlgoCategory());

                    return order;
                })
                .toList();
    }

    private List<TrOrdersDto> createTrExitOrders(Signal signal, UserAuthConstants userAuthConstants) {
        Hibernate.initialize(signal.getStrategy());
        Strategy strategy = signal.getStrategy();
//test null response
        Optional<AppUserLogInfo> latestLogInfoOpt = appUserLogInfoRepository.findTopByAppUserOrderByLoggedinTimeDesc(userAuthConstants.getAppUser());

        signal.getSignalLegs().stream().forEach(order -> {
            logger.info("Signal Leg: {}, Leg Type: {}, Exchange Status: {}", order.getId(), order.getLegType(), order.getExchangeStatus());
        });

        return signal.getSignalLegs().stream()
                .filter(dto -> dto.getLegType().equalsIgnoreCase(LegStatus.EXIT.getKey()) && dto.getExchangeStatus().equalsIgnoreCase(LegExchangeStatus.CREATED.getKey()))
                .map(dto -> {
                    TrOrdersDto order = new TrOrdersDto();
                    String buySellFlag = dto.getBuySellFlag().equalsIgnoreCase(OrderTypeMenu.BUY.getKey()) ? OrderTypeMenu.SELL.getKey().toUpperCase() : OrderTypeMenu.BUY.getKey().toUpperCase();
                    String tSym = commonUtils.getTrTradingSymbol(dto.getName());

                    logger.info("Creating TR order for signal ID: {}, strategy ID: {}, leg ID: {} trading symbol: {}", signal.getId(), strategy.getId(),dto.getId() ,  tSym);
                    // logger.info("Creating TR order for signal ID: {}, strategy ID: {}, trading symbol: {}", signal.getId(), strategy.getId(), tSym);
                    // Map common fields
                    order.setUserId(userAuthConstants.getClientId());
                    order.setAccountId(userAuthConstants.getClientId());
                    order.setProductAlias(userAuthConstants.getProductAlias());
                    order.setOrderUniqueIdentifier("QO_" + signal.getId() + "_" + dto.getId().toString());
                    order.setUniqueKey("QO_" + signal.getId() + "_" + dto.getId().toString());
                    order.setTransactionType("BUY".equalsIgnoreCase(buySellFlag) ? "B" : "S");
                    order.setPriceType("L");

                    int quantity = (int) (dto.getLotSize() * dto.getNoOfLots());
                    order.setQuantity(quantity);
                    order.setNoLots(Math.toIntExact(dto.getNoOfLots()));
                    order.setLotSize(dto.getLotSize().intValue());
                    order.setMultiply(strategy.getMultiplier().intValue());

                    // Set values from AppUserLogInfo
                    latestLogInfoOpt.ifPresent(logInfo -> {
                        order.setUserAgent(logInfo.getUserAgent());
                        order.setIpAddress(logInfo.getMechineId());
                    });

                    MasterResponseFO master = marketDataFetch.getMasterResponse(dto.getName());
                    order.setDateDays("NA");
//            order.setExchange(dto.getSegment());
                    String exchange = dto.getSegment();
                    logger.info("Setting exchange for order: {}, trading symbol: {} , master name : {}", exchange, tSym , master.getName());
                    if (exchange != null) {
                        if (exchange.startsWith("NSE")) {
                            logger.info("Setting exchange to NFO for trading symbol: {}", tSym);
                            order.setExchange("NFO");
                            order.setTradingSymbol(tSym);
                        } else if (exchange.startsWith("BSE")) {
                            logger.info("Setting exchange to BFO for trading symbol: {}", master.getName());
                            order.setExchange("BFO");
                            order.setTradingSymbol(master.getName());
                        } else {
                            logger.info("Setting exchange to NFO last else block for trading symbol: {}", tSym);
                            order.setExchange("NFO");
                            order.setTradingSymbol(tSym);
                        }
                    }

                    order.setProductCode(strategy.getPositionType().equalsIgnoreCase("Intraday") ? "MIS" : "NRML");
                    order.setPrice(dto.getPrice());
                    order.setSegment("FO");
                    order.setRetention("DAY");
                    order.setMarketProtection("NA");
                    order.setDisclosedQuantity(0);
                    order.setMinimumQuantity(0);
                    order.setPositionSquareOffFlag("N");
                    order.setAfterMarketOrder("NO");
                    order.setTriggerPrice(dto.getPrice());
                    order.setOrderSource("MOB");
                    order.setUserTag("NA");
                    order.setCriteriaAttribute("NO");
                    order.setRemarks("NA");
                    order.setTokenNo(String.valueOf(master.getExchangeInstrumentID()));

                    if (strategy.getAlgoId() != null) order.setExchangeAlgoId(strategy.getAlgoId());
                    if (strategy.getAlgoCategory() != null) order.setExchangeAlgoCategory(strategy.getAlgoCategory());

                    logger.info("TR order created for signal ID: {}, strategy ID: {}, trading symbol: {} , old trading symbol: {}", signal.getId(), strategy.getId(), order.getTradingSymbol() , tSym);

                    logger.info("Order created: {}", order);
                    return order;
                })
                .toList();
    }
}
