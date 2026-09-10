package com.quantlab.signal.service;

import com.quantlab.signal.dto.redisDto.OrderDetails;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class OrderStatusChecker implements Runnable {

    private  List<OrderDetails> orderDetails;
    @Override
    public void run() {

    }

    public void setOrders (List<OrderDetails> orders) {
        orderDetails = orders;
    }
}
