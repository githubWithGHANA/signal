package com.quantlab.signal.service;

import com.quantlab.signal.grpcserver.OrderPlaceGrpc;
import com.quantlab.signal.grpcserver.PositionStreamGrpc;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class WebSocketService {

    @Autowired
    PositionStreamGrpc positionStreamGrpc;

    @Autowired
    OrderPlaceGrpc orderPlaceGrpc;

    @PostConstruct
    public void init() {
        // this is for test
        sendMessage("hello");
    }

    public String sendMessage(String message) {

        orderPlaceGrpc.startStreamingOrdersPlaced();
        positionStreamGrpc.startStreaming();
        return "null";
    }
}
