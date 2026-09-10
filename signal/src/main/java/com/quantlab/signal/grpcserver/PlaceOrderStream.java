package com.quantlab.signal.grpcserver;

import net.devh.boot.grpc.client.inject.GrpcClient;
import org.modelmapper.ModelMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class PlaceOrderStream {
    @Autowired
    ModelMapper modelMapper;

    @GrpcClient("grpc-quantlab-service-stream")
    com.market.proto.xts.PlaceOrderServiceGrpc.PlaceOrderServiceBlockingStub synchronousClient;

}
