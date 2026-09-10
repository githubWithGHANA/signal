package com.quantlab.signal.service;


import com.quantlab.signal.dto.redisDto.OrderDetails;
import com.quantlab.signal.service.redisService.OrderDetailsRepository;
import org.checkerframework.checker.units.qual.A;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Map;

@Service
public class OrderStatusControlar {

    @Autowired
    OrderDetailsRepository orderDetailsRepository;

    public void checkOrderStatus() {
        // has to fetch the orders
        Map<String, OrderDetails> orders = orderDetailsRepository.findKeys("OD_*");
        if (!orders.isEmpty()) {

        }
    }
}
