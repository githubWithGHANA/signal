package com.quantlab.signal.service;

import com.quantlab.common.entity.*;
import com.quantlab.common.exception.custom.OrderLegNotFoundException;
import com.quantlab.common.exception.custom.SignalNotFoundException;
import com.quantlab.common.repository.*;
import com.quantlab.common.utils.staticstore.dropdownutils.*;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;

import static com.quantlab.common.utils.staticstore.AppConstants.AMOUNT_MULTIPLIER;

@Service
public class OrderStatusFeedMapper {

    private static final Logger logger = LogManager.getLogger(OrderStatusFeedMapper.class);

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
    OrderPersistenceService orderPersistenceService;

    @Autowired
    OrderCommonService orderCommonService;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Order mapToOrder(com.market.proto.tr.OrderStatusFeed orderStatusFeed) {
        Order order = new Order();
        StrategyLeg strategyLeg = null;
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd-MMM-yyyy HH:mm:ss", Locale.ENGLISH);

        try {
            // Try to find existing order by guiOrderId (similar to appOrderID in XTS)
            Optional<Order> existingOrder = orderRepository.getByAppOrderID(orderStatusFeed.getNestOrderNumber());
            if (existingOrder.isPresent()) {
                order = existingOrder.get();
            }
            Signal orderSignal = new Signal();

            // Map basic order fields
            order.setAppOrderID(orderStatusFeed.getNestOrderNumber());
            order.setExchangeOrderId(orderStatusFeed.getExchangeOrderId());
            order.setOrderSide(mapTransactionTypeToOrderSide(orderStatusFeed.getTransactionType()));
            order.setOrderType(orderStatusFeed.getPriceType());
            order.setProductType(orderStatusFeed.getProductCode());
            order.setPrice((long) ((orderStatusFeed.getTradePrice() != 0? orderStatusFeed.getTradePrice() : orderStatusFeed.getPrice()) * AMOUNT_MULTIPLIER));
            order.setQuantity((long) orderStatusFeed.getQuantity());
            orderStatusFeed.getPriceType();
            order.setExecutionType(orderStatusFeed.getPriceType().equalsIgnoreCase("L") ? "Limit" : "Market");
//            order.setTriggerPrice((long) orderStatusFeed.getTriggerPrice());
            order.setStatus(orderStatusFeed.getOrderStatus());
            order.setAverageTradedPrice(String.valueOf(orderStatusFeed.getTradePrice()));
            order.setLeavesQuantity((long) orderStatusFeed.getUnfilledQuantity());
            order.setCumulativeQuantity((long) orderStatusFeed.getFilledQuantity());
            order.setDisclosed_uantity((long) orderStatusFeed.getDisclosedQuantity());
            order.setExchangeTransactTime(orderStatusFeed.getExchangeTimestamp());
            order.setLastUpdateTime(orderStatusFeed.getExchangeTimestamp());
            order.setOrderUniqueIdentifier(orderStatusFeed.getOrderUniqueIdentifier());
            order.setMessageCode(Optional.of(orderStatusFeed.getSsboe()).filter(s ->!s.isEmpty()).map(Long::parseLong).orElse(0L));
            order.setUniqueKey(orderStatusFeed.getUniqueKey());
            order.setCancelRejectReason(orderStatusFeed.getTxt());
            order.setExchangeSegment(orderStatusFeed.getE()); // Need to change
            order.setDeployedOn(Optional.of(orderStatusFeed.getExchangeTimestamp())
                    .filter(s -> !s.isBlank())
                    .map(s -> LocalDateTime.parse(s, formatter).atZone(ZoneId.of("Asia/Kolkata")).toInstant())
                    .orElse(Instant.now()));

            order.setExchangeInstrumentId(Optional.of(orderStatusFeed.getTokenOrSymbol())
                    .filter(s -> !s.isBlank())
                    .map(Long::parseLong)
                    .orElse(0L));
                 // Map instrument details
            order.setInstrumentName(orderStatusFeed.getTradingSymbol());
            order.setTokenID(orderStatusFeed.getToken());

            if (order.getMessageCode() == 11221122L) {
                order.setCancelRejectReason("Required margin not available, available margin: " + orderStatusFeed.getAvailableCash()
                                + ", required margin: " + orderStatusFeed.getOrderMargin()+ ", additional margin required: " + orderStatusFeed.getInsufficientFund());
            }
            // Handle order unique identifier to link with strategy/signal
            if (!orderStatusFeed.getOrderUniqueIdentifier().isEmpty()) {
                order.setUniqueKey(orderStatusFeed.getUniqueKey());
                // Parse the identifier (assuming similar format: QO_signalId_strategyLegId)
                String[] parts = orderStatusFeed.getOrderUniqueIdentifier().split("_");
                if (parts.length >= 3) {
                    String signalId = parts[1];
                    String legId = parts[2];
                    Long l1 = Long.parseLong(signalId.trim());
                    Long l2 = Long.parseLong(legId.trim());

                    Optional<Signal> orderSignalOptional = signalRepository.findById(l1);
                    Optional<StrategyLeg> orderLeg = strategyLegRepository.findById(l2);

                    if (orderSignalOptional.isEmpty()) {
                        throw new SignalNotFoundException("Signal not found for the given signal id: " +
                                orderStatusFeed.getOrderUniqueIdentifier());
                    }
                    if (orderLeg.isEmpty()) {
                        throw new OrderLegNotFoundException("Strategy leg not found for the given leg id: " +
                                orderStatusFeed.getOrderUniqueIdentifier());
                    }
                    orderSignal = orderSignalOptional.get();
                    strategyLeg = orderLeg.get();
                    order.setSignal(orderSignal);
                    order.setAppUser(orderSignal.getAppUser());
                    order.setStrategy(orderSignal.getStrategy());
                    order.setUserAdmin(orderSignal.getAppUser().getAdmin());
                    order.setUnderlying(orderSignal.getStrategy().getUnderlying().getName());
                    order.setInstrumentName(strategyLeg.getName());
                    order.setSourceType(OmsType.OMS_TR.getOmsType());
                }
            }
            order = orderPersistenceService.saveAndCommit(order);

            if (order.getId() != null) {
                Optional<Order> reloaded = orderRepository.findById(order.getId());
                if (reloaded.isPresent()) {
                    order = reloaded.get();
                }
            }
            logger.info("TR order ={} , legID = {}, StrategyID = {}",
                    order.toString(),
                    strategyLeg != null ? strategyLeg.getId() : "null",
                    orderSignal.getStrategy() != null ? orderSignal.getStrategy().getId() : "null");

            orderCommonService.processStrategyBasedOnOrder(order, strategyLeg, orderSignal);
            return order;

        } catch (RuntimeException e) {
            logger.error("Unable to map OrderStatusFeed: " + e.getMessage(), e);
        } catch (Exception e) {
            logger.error("Unexpected error mapping OrderStatusFeed", e);
            throw new RuntimeException(e);
        }
        return null;
    }

    private String mapTransactionTypeToOrderSide(String transactionType) {
        if (transactionType == null) return "";
        return transactionType.equalsIgnoreCase("B") ? "BUY" : "SELL";
    }
}
