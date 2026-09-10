package com.quantlab.signal.service;

import com.quantlab.common.entity.Order;
import com.quantlab.common.repository.OrderRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OrderPersistenceService {

    private final OrderRepository orderRepository;

    public OrderPersistenceService(OrderRepository orderRepository) {
        this.orderRepository = orderRepository;
    }


    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Order saveAndCommit(Order order) {
        return orderRepository.saveAndFlush(order);
    }
}
