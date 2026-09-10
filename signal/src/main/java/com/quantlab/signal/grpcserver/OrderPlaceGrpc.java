package com.quantlab.signal.grpcserver;


import com.google.protobuf.BoolValue;
import com.market.proto.tr.OrderStatusFeedStreamServiceGrpc;
import com.market.proto.tr.PlaceOrder;
import com.quantlab.common.entity.TrSTTInteractiveLocation;
import com.quantlab.common.entity.XtsInteractiveLocation;
import com.quantlab.common.entity.Order;
import com.quantlab.common.entity.TrInteractiveLocation;
import com.quantlab.common.repository.OrderRepository;
import com.quantlab.signal.dto.TrOrdersDto;
import com.quantlab.signal.dto.TrPlaceOrderDto;
import com.quantlab.signal.dto.XtsOrdersDto;
import com.quantlab.signal.dto.XtsPlaceOrderDto;
import com.quantlab.signal.dto.redisDto.MarketData;
import com.quantlab.signal.service.OrderPlacedResponseMapper;
import com.quantlab.signal.service.OrderStatusFeedMapper;
import com.quantlab.signal.service.redisService.TouchLineService;
import com.quantlab.signal.utils.AuthUtils;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.stub.StreamObserver;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import net.devh.boot.grpc.client.inject.GrpcClient;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.hibernate.annotations.Type;
import org.modelmapper.ModelMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import static com.quantlab.signal.utils.StrategyConstants.DEFAULT_AMOUNT_INTERVAL;


@Service
public class OrderPlaceGrpc {
    private static final Logger logger = LogManager.getLogger(OrderPlaceGrpc.class);

    @Autowired
    ModelMapper modelMapper;

    @Autowired
    OrderRepository orderRepository;

    @Autowired
    OrderPlacedResponseMapper orderPlacedResponseMapper;

    @Autowired
    OrderStatusFeedMapper orderStatusFeedMapper;

    @Autowired
    TouchLineService touchLineService;

    // XTS gRPC clients
    @GrpcClient("grpc-quantlab-service-xts")
    private com.market.proto.xts.PlaceOrderServiceGrpc.PlaceOrderServiceBlockingStub xtsSynchronousClient;
    private com.market.proto.xts.OrderBookStreamServiceGrpc.OrderBookStreamServiceStub xtsAsyncStub;

    // TR gRPC clients
    @GrpcClient("grpc-quantlab-service-tr")
    private com.market.proto.tr.PlaceOrderServiceGrpc.PlaceOrderServiceBlockingStub trNetMagicSynchronousClient;

    @GrpcClient("grpc-quantlab-service-tr-stt")
    private com.market.proto.tr.PlaceOrderServiceGrpc.PlaceOrderServiceBlockingStub trSTTSynchronousClient;

    private OrderStatusFeedStreamServiceGrpc.OrderStatusFeedStreamServiceStub trNetMagicAsyncStub;

    private OrderStatusFeedStreamServiceGrpc.OrderStatusFeedStreamServiceStub trSTTAsyncStub;


    private XtsInteractiveLocation xtsConfig;
    private TrInteractiveLocation trConfig;
    private TrSTTInteractiveLocation trSTTConfig;
    private ManagedChannel xtsChannel;
    private ManagedChannel trChannel;
    private ManagedChannel trSTTChannel;
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();

    @Autowired
    public OrderPlaceGrpc(XtsInteractiveLocation xtsConfig, TrInteractiveLocation trConfig, TrSTTInteractiveLocation trSTTConfig) {
        this.xtsConfig = xtsConfig;
        this.trConfig = trConfig;
        this.trSTTConfig = trSTTConfig;
    }

    @PostConstruct
    private void initGrpcChannels() {
        // Initialize XTS channel
        logger.info("XTS Channel: {}:{}", xtsConfig.getLocation(), xtsConfig.getPort());
        xtsChannel = ManagedChannelBuilder.forAddress(xtsConfig.getLocation(), xtsConfig.getPort())
                .usePlaintext()
                .build();
        xtsAsyncStub = com.market.proto.xts.OrderBookStreamServiceGrpc.newStub(xtsChannel);

        // Initialize TR channel
        logger.info("TR Channel: {}:{}", trConfig.getLocation(), trConfig.getPort());
        trChannel = ManagedChannelBuilder.forAddress(trConfig.getLocation(), trConfig.getPort())
                .usePlaintext()
                .build();
        trNetMagicAsyncStub = com.market.proto.tr.OrderStatusFeedStreamServiceGrpc.newStub(trChannel);

        // Initialize TR STT channel
        logger.info("TR STT Channel: {}:{}", trSTTConfig.getLocation(), trSTTConfig.getPort());
        trSTTChannel = ManagedChannelBuilder.forAddress(trSTTConfig.getLocation(), trSTTConfig.getPort())
                .usePlaintext()
                .build();
        trSTTAsyncStub = com.market.proto.tr.OrderStatusFeedStreamServiceGrpc.newStub(trSTTChannel);
    }

    public com.market.proto.xts.PlaceOrderResponse placeOrder(XtsPlaceOrderDto request) {
        return placeXtsOrder(request);
    }

    public com.market.proto.tr.PlaceOrderResponse placeOrderTRNetMagic(TrPlaceOrderDto request) {
        return placeTrNetMagicOrder(request);
    }

    public com.market.proto.tr.PlaceOrderResponse placeOrderTRSTT(TrPlaceOrderDto request) {
        return placeTrSTTOrder(request);
    }

    private com.market.proto.xts.PlaceOrderResponse placeXtsOrder(XtsPlaceOrderDto request) {
        logger.info("Placing XTS order for signal ID: {}", request.getSignalID());
        com.market.proto.xts.PlaceOrderRequest placeRequest = com.market.proto.xts.PlaceOrderRequest.newBuilder()
                .setSignalID(request.getSignalID())
                .setTenantID(request.getTenantID())
                .setAppKey(request.getAppKey())
                .setSecretKey(request.getSecretKey())
                .setToken(request.getToken())
                .setExitFlag(BoolValue.of(request.getExitFlag()))
                .addAllOrders(mapXtsOrders(request.getOrders()))
                .setRequiredCapital(request.getRequiredCapital()!= null? Math.toIntExact(request.getRequiredCapital()) : 0)
                .build();

        try {
            return xtsSynchronousClient.placeOrder(placeRequest);
        } catch (Exception e) {
            logger.error("XTS order placement failed for signal ID: {}", request.getSignalID(), e);
            return null;
        }
    }

    private com.market.proto.tr.PlaceOrderResponse placeTrNetMagicOrder(TrPlaceOrderDto request) {
        logger.info("Placing TR order for signal ID: {}", request.getSignalID());

        com.market.proto.tr.PlaceOrderRequest.Builder builder = com.market.proto.tr.PlaceOrderRequest.newBuilder();


        if (request.getSignalID() != null)
            builder.setSignalID(request.getSignalID());

        if (request.getTenantID() != null)
            builder.setTenantID(request.getTenantID());

        if (request.getTrToken() != null)
            builder.setTrToken(request.getTrToken());

        if (request.getCugUser() != null)
            builder.setCugUser(BoolValue.of(request.getCugUser()));

        if (request.getUserSessionID() != null)
            builder.setUserSessionID(request.getUserSessionID());

        if (request.getJSessionID() != null)
            builder.setJSessionID(request.getJSessionID());

        if (request.getBranchId() != null)
            builder.setBranchId(request.getBranchId());

        if (request.getBrokerName() != null)
            builder.setBrokerName(request.getBrokerName());

        if (request.getExitFlag() != null)
            builder.setExitFlag(BoolValue.of(request.getExitFlag()));

        if (request.getOrders() != null)
            builder.addAllOrders(mapTrOrders(request.getOrders()));

        if (request.getRequiredCapital() != null)
            builder.setRequiredCapital(Math.toIntExact(request.getRequiredCapital()));

        try {
            return trNetMagicSynchronousClient.placeOrder(builder.build());
        } catch (Exception e) {
            logger.error("TR order placement failed for signal ID: {}", request.getSignalID(), e);
            return null;
        }
    }

    private com.market.proto.tr.PlaceOrderResponse placeTrSTTOrder(TrPlaceOrderDto request) {
        logger.info("Placing TR STT order for signal ID: {}", request.getSignalID());

        com.market.proto.tr.PlaceOrderRequest.Builder builder = com.market.proto.tr.PlaceOrderRequest.newBuilder();


        if (request.getSignalID() != null)
            builder.setSignalID(request.getSignalID());

        if (request.getTenantID() != null)
            builder.setTenantID(request.getTenantID());

        if (request.getTrToken() != null)
            builder.setTrToken(request.getTrToken());

        if (request.getCugUser() != null)
            builder.setCugUser(BoolValue.of(request.getCugUser()));

        if (request.getUserSessionID() != null)
            builder.setUserSessionID(request.getUserSessionID());

        if (request.getJSessionID() != null)
            builder.setJSessionID(request.getJSessionID());

        if (request.getBranchId() != null)
            builder.setBranchId(request.getBranchId());

        if (request.getBrokerName() != null)
            builder.setBrokerName(request.getBrokerName());

        if (request.getExitFlag() != null)
            builder.setExitFlag(BoolValue.of(request.getExitFlag()));

        if (request.getOrders() != null)
            builder.addAllOrders(mapTrOrders(request.getOrders()));

        if (request.getRequiredCapital() != null)
            builder.setRequiredCapital(Math.toIntExact(request.getRequiredCapital()));

        try {
            return trSTTSynchronousClient.placeOrder(builder.build());
        } catch (Exception e) {
            logger.error("TR order placement failed for signal ID: {}", request.getSignalID(), e);
            return null;
        }
    }

    private List<com.market.proto.xts.PlaceOrder> mapXtsOrders(List<XtsOrdersDto> orders) {
        return orders.stream().map(dto -> {
            com.market.proto.xts.PlaceOrder.Builder builder = com.market.proto.xts.PlaceOrder.newBuilder()
                    .setExchangeSegment(dto.getExchangeSegment())
                    .setExchangeInstrumentId(dto.getExchangeInstrumentId())
                    .setOrderType(dto.getOrderType())
                    .setClientID(dto.getClientID())
                    .setUserID(dto.getUserID())
                    .setOrderSide(dto.getOrderSide())
                    .setTimeInForce(dto.getTimeInForce())
                    .setLimitPrice(dto.getLimitPrice())
                    .setOrderUniqueIdentifier(dto.getOrderUniqueIdentifier())
                    .setProductType(dto.getProductType())
                    .setNoLots(dto.getNoLots())
                    .setLotSize(dto.getLotSize())
                    .setMultiply(dto.getMultiply() < 0 ? 1 : dto.getMultiply());

            if (dto.getAlgoID() != null) builder.setAlgoID(dto.getAlgoID());
            if (dto.getAlgoCategory() != null) builder.setAlgoCategory(dto.getAlgoCategory());

            // UAT specific adjustment (remove for production)
            long limitPriceForUatOnly = (long) (dto.getLimitPrice());
            builder.setLimitPrice(limitPriceForUatOnly);

            return builder.build();
        }).toList();
    }

    private List<com.market.proto.tr.PlaceOrder> mapTrOrders(List<TrOrdersDto> orders) {
        try {

            return orders.stream().map(dto -> {
                logger.info("order socket processing data to map : {}",dto);
                return PlaceOrder.newBuilder()
                        .setProductAlias(dto.getProductAlias()==null? "NA" : dto.getProductAlias())
                        .setUserId(dto.getUserId())
                        .setAccountId(dto.getAccountId())
                        .setTradingSymbol(dto.getTradingSymbol())
                        .setExchange(dto.getExchange())
                        .setTransactionType(dto.getTransactionType())
                        .setRetention(dto.getRetention())
                        .setPriceType(dto.getPriceType())
                        .setQuantity(dto.getQuantity())
                        .setDisclosedQuantity(dto.getDisclosedQuantity())
                        .setMarketProtection(dto.getMarketProtection())
                        .setPrice(dto.getPrice())
                        .setTriggerPrice(dto.getTriggerPrice())
                        .setProductCode(dto.getProductCode())
                        .setDateDays(dto.getDateDays())
                        .setAfterMarketOrder(dto.getAfterMarketOrder())
                        .setPositionSquareOffFlag(dto.getPositionSquareOffFlag())
                        .setMinimumQuantity(dto.getMinimumQuantity())
                        .setBrokerClient(dto.getBrokerClient())
                        .setNaicCode(dto.getExchange().equalsIgnoreCase("BSEFO")? "88" : "00")
                        .setOrderSource(dto.getOrderSource())
                        .setUserTag(dto.getUserTag())
                        .setExchangeAlgoId(dto.getExchangeAlgoId() == null ? "NA" : dto.getExchangeAlgoId())
                        .setExchangeAlgoCategory(dto.getExchangeAlgoCategory() == null ? "NA" : dto.getExchangeAlgoCategory())
                        .setRemarks(dto.getRemarks() == null ? "NA" : dto.getRemarks())
                        .setCriteriaAttribute(dto.getCriteriaAttribute())
                        .setUniqueKey(dto.getUniqueKey())
                        .setChannel(dto.getChannel() == null ? "NA" : dto.getChannel())
                        .setIpAddress(dto.getIpAddress())
                        .setUserAgent(dto.getUserAgent())
                        .setAppInstallId(dto.getAppInstallId() == null ? "NA" : dto.getAppInstallId())
                        .setAuctionNumber(dto.getAuctionNumber() == null ? "NA" : dto.getAuctionNumber())
                        .setCtclId(dto.getCtclId() == null ? "NA" : dto.getCtclId())
                        .setNoLots(dto.getNoLots())
                        .setLotSize(dto.getLotSize())
                        .setMultiply(dto.getMultiply())
                        .setSegment(dto.getSegment())
                        .setOrderUniqueIdentifier(dto.getOrderUniqueIdentifier())
                        .setTokenNo(dto.getTokenNo())
                        .build();
            }).toList();
        }catch (Exception e){
            logger.error("Error in mapping TR orders: ", e);
        }
        return null;
    }

    public void startStreamingOrdersPlaced() {
        streamXtsOrderPlaceData();
        streamTrNetMagicOrderPlaceData();
        streamTrSTTOrderPlaceData();
    }


    public void streamXtsOrderPlaceData() {
        com.market.proto.xts.OrderBookStreamRequest request = com.market.proto.xts.OrderBookStreamRequest.newBuilder().build();
        logger.info("Starting XTS streamOrderPlaceData...");
        try {
            xtsAsyncStub.streamOrderBookData(request, new StreamObserver<com.market.proto.xts.OrderBookResponseStream>() {
                @Override
                public void onNext(com.market.proto.xts.OrderBookResponseStream orderBookResponseStream) {
                    logger.info("Received XTS order book response: {} " ,orderBookResponseStream);
                    Order placedOrderData = orderPlacedResponseMapper.mapToOrder(orderBookResponseStream);
                    storeOrderEntity(placedOrderData);
                }

                @Override
                public void onError(Throwable throwable) {
                    logger.error("Error during XTS streaming: {}", throwable.getMessage());
                    retryXtsOrderStream();
                }

                @Override
                public void onCompleted() {
                   logger.info("XTS streaming completed.");
                    retryXtsOrderStream();
                }
            });
        } catch (Exception e) {
            logger.error("Exception in XTS streaming", e);
        }
    }

    public void streamTrNetMagicOrderPlaceData() {
        com.market.proto.tr.OrderStatusFeedStreamRequest request =
                com.market.proto.tr.OrderStatusFeedStreamRequest.newBuilder().build();

        logger.info("Starting TR NetMagic streamOrderPlaceData...");
        try {
            trNetMagicAsyncStub.streamOrderStatusFeed(request, new StreamObserver<com.market.proto.tr.OrderStatusFeed>() {
                @Override
                public void onNext(com.market.proto.tr.OrderStatusFeed orderStatusFeed) {
                    logger.info("Received TR NetMagic order status feed: {}", orderStatusFeed.toString());
                    try {
                        Order placedOrderData = orderStatusFeedMapper.mapToOrder(orderStatusFeed);
                        if (placedOrderData != null) {
                            storeOrderEntity(placedOrderData);
                        }
                    } catch (Exception e) {
                        logger.error("Error processing TR NetMagic order status feed :  {}", e.getMessage());
                    }
                }

                @Override
                public void onError(Throwable throwable) {
                    logger.error("Error during TR NetMagic streaming: {}", throwable.getMessage());
                    retryTrOrderStream();
                }

                @Override
                public void onCompleted() {
                    logger.info("TR NetMagic streaming completed.");
                    retryTrOrderStream();
                }
            });
        } catch (Exception e) {
            logger.error("Exception in TR NetMagic streaming: {}", e.getMessage());
        }
    }

    public void streamTrSTTOrderPlaceData() {
        com.market.proto.tr.OrderStatusFeedStreamRequest request =
                com.market.proto.tr.OrderStatusFeedStreamRequest.newBuilder().build();

        logger.info("Starting TR STT streamOrderPlaceData...");
        try {
            // Use the TR STT async stub so we connect to the STT service (was mistakenly using trAsyncStub)
            trSTTAsyncStub.streamOrderStatusFeed(request, new StreamObserver<com.market.proto.tr.OrderStatusFeed>() {
                @Override
                public void onNext(com.market.proto.tr.OrderStatusFeed orderStatusFeed) {
                    logger.info("Received TR STT order status feed: {}", orderStatusFeed.toString());
                    try {
                        Order placedOrderData = orderStatusFeedMapper.mapToOrder(orderStatusFeed);
                        if (placedOrderData != null) {
                            storeOrderEntity(placedOrderData);
                        }
                    } catch (Exception e) {
                        logger.error("Error processing TR STT order status feed :  {}", e.getMessage());
                    }
                }

                @Override
                public void onError(Throwable throwable) {
                    logger.error("Error during TR STT streaming: {}", throwable.getMessage());
                    retryTrSTTOrderStream();
                }

                @Override
                public void onCompleted() {
                    logger.info("TR STT streaming completed.");
                    retryTrSTTOrderStream();
                }
            });
        } catch (Exception e) {
            logger.error("Exception in TR STT streaming: {}", e.getMessage());
        }
    }


    private void retryXtsOrderStream() {
        scheduler.schedule(this::streamXtsOrderPlaceData, 2, TimeUnit.SECONDS);
    }

    private void retryTrOrderStream() {
        scheduler.schedule(this::streamTrNetMagicOrderPlaceData, 2, TimeUnit.SECONDS);
    }

    private void retryTrSTTOrderStream() {
        scheduler.schedule(this::streamTrSTTOrderPlaceData, 2, TimeUnit.SECONDS);
    }

    @Transactional
    public void storeOrderEntity(Order placedOrderData) {
        if (placedOrderData == null) {
            logger.warn("Skipping order save because mapped order is null");
            return;
        }
        try {
            orderRepository.save(placedOrderData);
        } catch (RuntimeException e) {
            logger.error("Unable to store the order details: ", e);
        }
    }

    @PreDestroy
    public void shutdown() {
        if (xtsChannel != null) {
            xtsChannel.shutdown();
        }
        if (trChannel != null) {
            trChannel.shutdown();
        }
        if (trSTTChannel != null) {
            trSTTChannel.shutdown();
        }
        scheduler.shutdown();
    }
}
