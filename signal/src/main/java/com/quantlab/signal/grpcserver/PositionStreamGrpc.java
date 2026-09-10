package com.quantlab.signal.grpcserver;

import com.market.proto.tr.PositionServiceGrpc;
import com.quantlab.common.entity.TrSTTInteractiveLocation;
import com.quantlab.common.entity.XtsInteractiveLocation;
import com.quantlab.common.entity.Position;
import com.quantlab.common.entity.TrInteractiveLocation;
import com.quantlab.common.repository.PositionRepository;
import com.quantlab.signal.service.PositionResponseMapper;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.stub.StreamObserver;
import jakarta.annotation.PostConstruct;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.socket.WebSocketSession;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;


@Service
public class PositionStreamGrpc {

    private static final Logger logger = LogManager.getLogger(PositionStreamGrpc.class);

    private ManagedChannel xtsChanel;
    private ManagedChannel trNetMagicChanel;
    private ManagedChannel trSTTChanel;

    private com.market.proto.xts.PositionServiceGrpc.PositionServiceStub xtsAsyncStub;

    private PositionServiceGrpc.PositionServiceStub trAsyncStub;
    private PositionServiceGrpc.PositionServiceStub trSTTAsyncStub;

    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();

    @Autowired
    private PositionRepository positionRepository;

    @Autowired
    private PositionResponseMapper positionResponseMapper;

    private final Map<String, WebSocketSession> userSessions = new ConcurrentHashMap<>();


    private final XtsInteractiveLocation xtsConfig;

    private final TrInteractiveLocation trNetMagicConfig;

    private final TrSTTInteractiveLocation trSTTConfig;

    @Autowired
    public PositionStreamGrpc(XtsInteractiveLocation xtsConfig , TrInteractiveLocation trConfig, TrSTTInteractiveLocation trSTTConfig) {
        this.xtsConfig = xtsConfig;
        this.trNetMagicConfig = trConfig;
        this.trSTTConfig = trSTTConfig;
    }

    @PostConstruct
    private void initGrpcChannels() {
        xtsChanel = ManagedChannelBuilder.forAddress(xtsConfig.getLocation(), xtsConfig.getPort())
                .usePlaintext()
                .build();
        xtsAsyncStub = com.market.proto.xts.PositionServiceGrpc.newStub(xtsChanel);
        logger.info("XTS gRPC channel initialized: {}:{}", xtsConfig.getLocation(), xtsConfig.getPort());

        trNetMagicChanel = ManagedChannelBuilder.forAddress(trNetMagicConfig.getLocation(), trNetMagicConfig.getPort())
                .usePlaintext()
                .build();
        trAsyncStub = PositionServiceGrpc.newStub(trNetMagicChanel);
        logger.info("TR gRPC channel initialized: {}:{}", trNetMagicConfig.getLocation(), trNetMagicConfig.getPort());

        trSTTChanel = ManagedChannelBuilder.forAddress(trSTTConfig.getLocation(), trSTTConfig.getPort())
                .usePlaintext()
                .build();
        trSTTAsyncStub = PositionServiceGrpc.newStub(trSTTChanel);
        logger.info("TR gRPC channel initialized: {}:{}", trNetMagicConfig.getLocation(), trNetMagicConfig.getPort());

    }

    public void startStreaming() {
        streamXtsPositionData();
        streamTrNetMagicPositionData();
        streamTRSTTPositionData();

    }

    public void streamXtsPositionData() {
        com.market.proto.xts.PositionStreamRequest request = com.market.proto.xts.PositionStreamRequest.newBuilder().build();
        logger.info("Starting XTS streamPositionData...");

        try {
            xtsAsyncStub.streamPositionData(request, new StreamObserver<com.market.proto.xts.PositionResponseStream>() {
                @Override
                public void onNext(com.market.proto.xts.PositionResponseStream response) {
                    Position position = positionResponseMapper.mapToXtsPosition(response);
                    logger.info("Received XTS Position response: " + position);
                    savePositionData(List.of(position));
                }

                @Override
                public void onError(Throwable t) {
                    logger.error("XTS Position streaming error: {}", t.getMessage());
                    retryXtsPositionStream();
                }

                @Override
                public void onCompleted() {
                    logger.info("XTS Position streaming completed.");
                    retryXtsPositionStream();
                }
            });
        } catch (Exception e) {
            logger.error("Exception in XTS position streaming", e);
        }
    }

    private void savePositionData(List<Position> position) {
        try {
            if (position != null) {
                positionRepository.saveAll(position);
            }
        } catch (RuntimeException e) {
            logger.error("Unable to store Position details: ", e);
        }
    }


    public void streamTrNetMagicPositionData() {
        com.market.proto.tr.PositionStreamRequest request = com.market.proto.tr.PositionStreamRequest.newBuilder().build();
        logger.info("Starting TR NetMagic streamPositionData...");

        try {
            trAsyncStub.streamPositionData(request, new StreamObserver<com.market.proto.tr.PositionResponseStream>() {
                @Override
                public void onNext(com.market.proto.tr.PositionResponseStream response) {
                    try {
                      //  logger.info("Received TR Position response: " + response);
                        List<Position> position = positionResponseMapper.mapToTrPosition(response);
                        savePositionData(position);
                    }catch (Exception e) {
                        logger.error("Error mapping TR NetMagic Position response: " + e.getMessage());
                        return;
                    }

                }

                @Override
                public void onError(Throwable t) {
                    logger.error("TR NetMagic Position streaming error: " + t.getMessage());

                    retryTrPositionStream();
                }

                @Override
                public void onCompleted() {
                    logger.info("TR NetMagic Position streaming completed.");

                    retryTrPositionStream();
                }
            });
        } catch (Exception e) {
            logger.error("Exception in TR NetMagic position streaming", e);
        }
    }

    public void streamTRSTTPositionData() {
        com.market.proto.tr.PositionStreamRequest request = com.market.proto.tr.PositionStreamRequest.newBuilder().build();
        logger.info("Starting TR STT streamPositionData...");
        try {
            trSTTAsyncStub.streamPositionData(request, new StreamObserver<com.market.proto.tr.PositionResponseStream>() {
                @Override
                public void onNext(com.market.proto.tr.PositionResponseStream response) {
                    try {
                          logger.info("Received TR Position response: " + response);
                        List<Position> position = positionResponseMapper.mapToTrPosition(response);
                        savePositionData(position);
                    }catch (Exception e) {
                        logger.error("Error mapping TR STT Position response: " + e.getMessage());
                        return;
                    }

                }

                @Override
                public void onError(Throwable t) {
                    logger.error("TR STT Position streaming error: {}", t.getMessage());
                    retryTrSTTPositionStream();
                }

                @Override
                public void onCompleted() {
                    logger.info("TR STT Position streaming completed.");
                    retryTrSTTPositionStream();
                }
            });
        } catch (Exception e) {
            logger.error("Exception in TR STT position streaming", e);
        }
    }


    private void retryXtsPositionStream() {
        scheduler.schedule(this::streamXtsPositionData, 2, TimeUnit.SECONDS);
    }

    private void retryTrPositionStream() {
        scheduler.schedule(this::streamTrNetMagicPositionData, 2, TimeUnit.SECONDS);
    }


    private void retryTrSTTPositionStream() {
        scheduler.schedule(this::streamTRSTTPositionData, 2, TimeUnit.SECONDS);
    }

}

