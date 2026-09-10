package com.quantlab.signal.service;

import com.google.protobuf.BoolValue;
import com.market.proto.xts.MarginCheckLogin;
import com.market.proto.xts.MarginCheckResponse;
import com.market.proto.xts.MarginCheckServiceGrpc;
import com.quantlab.common.entity.TrInteractiveLocation;
import com.quantlab.common.entity.UserAuthConstants;
import com.quantlab.common.entity.XtsInteractiveLocation;
import com.quantlab.signal.grpcserver.OrderPlaceGrpc;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import jakarta.annotation.PreDestroy;
import lombok.extern.flogger.Flogger;
import net.devh.boot.grpc.client.inject.GrpcClient;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class LoginMarginCheckService {
    private static final Logger logger = LogManager.getLogger(LoginMarginCheckService.class);

    private final MarginCheckServiceGrpc.MarginCheckServiceBlockingStub marginCheckStubXTS;
    private final com.market.proto.tr.MarginCheckServiceGrpc.MarginCheckServiceBlockingStub marginCheckStubTR;
    @GrpcClient("grpc-quantlab-service-tr-stt")
    private com.market.proto.tr.MarginCheckServiceGrpc.MarginCheckServiceBlockingStub marginCheckStubTRSTT;

    private final ManagedChannel xtsChannel;
    private final ManagedChannel trChannel;


    @Autowired
    public LoginMarginCheckService(XtsInteractiveLocation xtsConfig, TrInteractiveLocation trConfig) {
        this.xtsChannel = ManagedChannelBuilder.forAddress(xtsConfig.getLocation(), xtsConfig.getPort())
                .usePlaintext()
                .build();
        this.trChannel = ManagedChannelBuilder.forAddress(trConfig.getLocation(), trConfig.getPort())
                .usePlaintext()
                .build();;
        this.marginCheckStubXTS = MarginCheckServiceGrpc.newBlockingStub(xtsChannel);
        this.marginCheckStubTR =  com.market.proto.tr.MarginCheckServiceGrpc.newBlockingStub(trChannel);
    }

    public boolean sendXTSMarginLogin(UserAuthConstants userAuthConstants) {
        try {
            MarginCheckLogin request = MarginCheckLogin.newBuilder()
                    .setClientID(userAuthConstants.getClientId())
                    .setAppKey(userAuthConstants.getXtsAppKey())
                    .setSecretKey(userAuthConstants.getXtsSecretKey())
                    .build();
            logger.info("######## Sending XTS Margin Login for Client ID: {}", request.toString());

            MarginCheckResponse response = marginCheckStubXTS.loginMarginCheck(request);
            return response.getSuccess();
        } catch (Exception e) {
            logger.error("######## error Sending XTS Margin Login for Client ID: {}", e.getMessage());
            return false;
        }
    }

    public boolean sendTRNMMarginLogin(UserAuthConstants userAuthConstants) {
        try {
            com.market.proto.tr.MarginCheckLogin request = com.market.proto.tr.MarginCheckLogin.newBuilder()
                    .setClientID(userAuthConstants.getClientId())
                    .setCugUser(BoolValue.of(userAuthConstants.getIsCugUser()))
                    .setUserSessionID(userAuthConstants.getUserSessionId())
//                    .setBranchId(userAuthConstants.getBranchId())
//                    .setBrokerName(userAuthConstants.getBrokerName())
                    .build();
            logger.info("######## Sending TR NM Margin Login for Client ID: {}", request.toString());
            com.market.proto.tr.MarginCheckResponse response = marginCheckStubTR.loginMarginCheck(request);
            return response.getSuccess();
        } catch (Exception e) {
            logger.error("######## error Sending TR NM Margin Login for Client ID: {}", e.getMessage());
            return false;
        }
    }

    public boolean sendTRSTTMarginLogin(UserAuthConstants userAuthConstants) {
        try {
            com.market.proto.tr.MarginCheckLogin request = com.market.proto.tr.MarginCheckLogin.newBuilder()
                    .setClientID(userAuthConstants.getClientId())
                    .setCugUser(BoolValue.of(userAuthConstants.getIsCugUser()))
                    .setUserSessionID(userAuthConstants.getUserSessionId())
                    .build();
            logger.info("######## Sending TR STT Margin Login for Client ID: {}", request.toString());
            com.market.proto.tr.MarginCheckResponse response = marginCheckStubTRSTT.loginMarginCheck(request);
            return response.getSuccess();
        } catch (Exception e) {
            logger.error("######## error Sending TR STT Margin Login for Client ID: {}", e.getMessage());
            return false;
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
    }
}
