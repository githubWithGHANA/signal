package com.quantlab.signal.service;

import com.market.proto.tr.OrderStatusFeed;
import com.quantlab.common.entity.*;
import com.quantlab.common.exception.custom.OrderLegNotFoundException;
import com.quantlab.common.exception.custom.SignalNotFoundException;
import com.quantlab.common.repository.*;
import com.quantlab.common.utils.staticstore.dropdownutils.*;
import com.quantlab.common.utils.staticstore.dropdownutils.Status;
import jakarta.annotation.PostConstruct;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.util.*;
import java.util.stream.Collectors;

import static com.quantlab.common.utils.staticstore.AppConstants.AMOUNT_MULTIPLIER;


@Service
public class OrderPlacedResponseMapper {


    private static final Logger logger = LogManager.getLogger(OrderPlacedResponseMapper.class);

    @Autowired
    OrderRepository orderRepository;

    @Autowired
    StrategyLegRepository strategyLegRepository;

    @Autowired
    DeploymentErrorsRepository deploymentErrorsRepository;

    @Autowired
    SignalRepository signalRepository;

    @Autowired
    StrategyRepository strategyRepository;

    @Autowired
    AppUserRepository appUserRepository;

    @Autowired
    OrderCommonService orderCommonService;

    @Autowired
    OrderPersistenceService orderPersistenceService;

    private static final ZoneId IST_ZONE = ZoneId.of("Asia/Kolkata");

    DateTimeFormatter formatter =
            new DateTimeFormatterBuilder()
                    .appendPattern("dd-MM-yyyy HH:mm:ss")
                    .optionalStart()
                    .appendPattern(".SSS")
                    .optionalEnd()
                    .toFormatter();


    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Order mapToOrder(com.market.proto.xts.OrderBookResponseStream responseStream) {
        Order order = new Order();
        StrategyLeg strategyLeg = null;
        try {
            Optional<Order> existingOrder = orderRepository.getByAppOrderID(responseStream.getAppOrderID());
            if (existingOrder.isPresent())
                order = existingOrder.get(); //makes the objects have same address
            Signal orderSignal = new Signal();

            order.setLoginID(responseStream.getLoginID());
            order.setAppOrderID(responseStream.getAppOrderID());
            order.setOrderReferenceID(responseStream.getOrderReferenceID());
            order.setGeneratedBy(responseStream.getGeneratedBy());
            order.setExecutionType(responseStream.getOrderType());
            order.setExchangeOrderId(responseStream.getExchangeOrderID());
            order.setOrderCategoryType(responseStream.getOrderCategoryType());
            order.setOrderSide(responseStream.getOrderSide());
            order.setOrderType(responseStream.getOrderType());
            order.setProductType(responseStream.getProductType());
            order.setTimeInForce(responseStream.getTimeInForce());
            String avgPriceStr = responseStream.getOrderAverageTradedPrice();
            double avgPrice = 0.0;
            if (avgPriceStr != null && !avgPriceStr.isBlank()) {
                try {
                    avgPrice = Double.parseDouble(avgPriceStr);
                } catch (NumberFormatException e) {
                    logger.warn("Invalid orderAverageTradedPrice '{}' for appOrderID {}", avgPriceStr, responseStream.getAppOrderID());
                }
            }else
                avgPrice = responseStream.getOrderPrice();
            order.setPrice((long) (avgPrice * AMOUNT_MULTIPLIER));
            order.setQuantity((long) responseStream.getOrderQuantity());
            order.setOrderStopPrice((long) responseStream.getOrderStopPrice());
            order.setStatus(responseStream.getOrderStatus());
            order.setAverageTradedPrice(responseStream.getOrderAverageTradedPrice());
            order.setLeavesQuantity((long) responseStream.getLeavesQuantity());
            order.setCumulativeQuantity((long) responseStream.getCumulativeQuantity());
            order.setDisclosed_uantity((long) responseStream.getOrderDisclosedQuantity());
            order.setGeneratedDateTime(responseStream.getOrderGeneratedDateTime());
            order.setExchangeTransactTime(responseStream.getExchangeTransactTime());
            order.setLastUpdateTime(responseStream.getLastUpdateDateTime());
            order.setExpiryDate(responseStream.getOrderExpiryDate());
            order.setCancelRejectReason(responseStream.getCancelRejectReason());
            order.setOrderUniqueIdentifier(responseStream.getOrderUniqueIdentifier());
            order.setLegStatus(responseStream.getOrderLegStatus());
            order.setMessageCode((long) responseStream.getMessageCode());
            order.setMessageVersion(String.valueOf(responseStream.getMessageVersion()));
            order.setTokenID(String.valueOf(responseStream.getTokenID()));
            order.setApplicationType((long) responseStream.getApplicationType());
            order.setUniqueKey(responseStream.getUniqueKey());
            order.setExchangeInstrumentId((long) responseStream.getExchangeInstrumentID());
            order.setDeployedOn(parseExchangeTransactTime(responseStream.getExchangeTransactTime(), responseStream.getAppOrderID()));
            order.setExchangeSegment(responseStream.getExchangeSegment());
            if (!responseStream.getOrderUniqueIdentifier().isEmpty()){
                order.setUniqueKey(responseStream.getUniqueKey());
                //the response is expected to be QO_signalId_strategyLegId
                String[] parts = responseStream.getOrderUniqueIdentifier().split("_");
//                Arrays.stream(parts).forEach(System.out::println);
                String signalId = parts[1];
                String legId = parts[2];
                Long l1 =Long.parseLong(signalId.trim());
                Long l2 =Long.parseLong(legId.trim());
                Optional<Signal> orderSignalOptional =  signalRepository.findById(l1);
                Optional<StrategyLeg> orderLeg =  strategyLegRepository.findById(l2);
                if(orderSignalOptional.isEmpty()){
                    throw new SignalNotFoundException("Signal not found for the given signal id : "+ responseStream.getOrderUniqueIdentifier());
                }
                if(orderLeg.isEmpty()){
                    throw new OrderLegNotFoundException("Strategy leg not found for the given leg id : "+ responseStream.getOrderUniqueIdentifier());
                }
                orderSignal = orderSignalOptional.get();
                strategyLeg = orderLeg.get();
                order.setSignal(orderSignal);
                order.setAppUser(orderSignal.getAppUser());
                order.setStrategy(orderSignal.getStrategy());
                order.setUserAdmin(orderSignal.getAppUser().getAdmin());
                order.setUnderlying(orderSignal.getStrategy().getUnderlying().getName());
                order.setInstrumentName(orderLeg.get().getName());
                order.setSourceType(OmsType.OMS_XTS.getOmsType());
            }
            order = orderPersistenceService.saveAndCommit(order);

            if (order.getId() != null) {
                Optional<Order> reloaded = orderRepository.findById(order.getId());
                if (reloaded.isPresent()) {
                    order = reloaded.get();
                }
            }
            logger.info("XTS order ={} , legID = {}, StrategyID = {}",
                    order.toString(),
                    strategyLeg != null ? strategyLeg.getId() : "null",
                    orderSignal.getStrategy() != null ? orderSignal.getStrategy().getId() : "null");

            orderCommonService.processStrategyBasedOnOrder(order, strategyLeg, orderSignal);
            return order;
        } catch (RuntimeException e) {
            logger.error("XTS orderFeed unable to OrderPlacedResponseMapper {}", e.getMessage());
//           e.printStackTrace();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        return null;
    }

    private Instant parseExchangeTransactTime(String exchangeTransactTime, String appOrderId) {
        if (exchangeTransactTime == null || exchangeTransactTime.isBlank()) {
            logger.warn("Missing exchangeTransactTime for appOrderID {}, using current time", appOrderId);
            return Instant.now();
        }
        try {
            return LocalDateTime.parse(exchangeTransactTime, formatter)
                    .atZone(IST_ZONE)
                    .toInstant();
        } catch (RuntimeException e) {
            logger.warn("Invalid exchangeTransactTime '{}' for appOrderID {}, using current time", exchangeTransactTime, appOrderId);
            return Instant.now();
        }
    }
}
