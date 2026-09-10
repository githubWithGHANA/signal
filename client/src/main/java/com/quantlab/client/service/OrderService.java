package com.quantlab.client.service;

import com.quantlab.client.dto.OrderRequestDto;
import com.quantlab.client.dto.OrderTableResponseDto;
import com.quantlab.common.entity.AppUser;
import com.quantlab.common.entity.Order;
import com.quantlab.common.repository.AppUserRepository;
import com.quantlab.common.repository.OrderRepository;
import com.quantlab.common.repository.UserAdminRepository;
import com.quantlab.signal.service.AuthService;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.*;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import static com.quantlab.common.utils.staticstore.AppConstants.AMOUNT_MULTIPLIER;

@Service
@Transactional
public class OrderService {

    private static final Logger logger = LogManager.getLogger(OrderService.class);

    @Autowired
    OrderRepository orderRepository;

    @Autowired
    UserAdminRepository userAdminRepository;

    @Autowired
    AppUserRepository appUserRepository;

    @Autowired
    AuthService authService;

    @Transactional
    public List<OrderTableResponseDto> getTodayOrders(String clientId) {
        AppUser appUser = authService.getUserFromCLientId(clientId);
        logger.info("Fetching orders for user with ID: {}", appUser.getAppUserId());

        Instant startOfDay = LocalDate.now().atStartOfDay().toInstant(ZoneOffset.UTC);
        Instant endOfDay = startOfDay.plusSeconds(86400);

        List<Order> orderList = fetchOrders(appUser.getId(), startOfDay, endOfDay);
        return mapOrdersToDtos(orderList, appUser.getAppUserId(), "today");
    }

    @Transactional
    public List<OrderTableResponseDto> getAllOrders(String clientId) {
        AppUser appUser = authService.getUserFromCLientId(clientId);
        List<Order> orderList = orderRepository.findByAppUser(appUser);
        return mapOrdersToDtos(orderList, appUser.getAppUserId(), "all");
    }

    @Transactional
    public List<OrderTableResponseDto> getOrdersByCustomDay(String clientId, OrderRequestDto requestDto) {
        AppUser appUser = authService.getUserFromCLientId(clientId);
        logger.info("Fetching orders for user with ID: {} on custom day: {}", appUser.getAppUserId(), requestDto.getCustomDate());

        // Ignores time from the received input (ISO - 8601, UTC string)
        Instant customDayInstant = Instant.parse(requestDto.getCustomDate());
        LocalDate customDay = customDayInstant.atZone(ZoneOffset.UTC).toLocalDate();

        Instant startOfDay = customDay.atStartOfDay().toInstant(ZoneOffset.UTC);
        Instant endOfDay = startOfDay.plusSeconds(86400);

        List<Order> orderList = fetchOrders(appUser.getId(), startOfDay, endOfDay);
        return mapOrdersToDtos(orderList, appUser.getAppUserId(), customDay.toString());
    }

    private List<Order> fetchOrders(Long userId, Instant startOfDay, Instant endOfDay) {
        List<Order> orderList = orderRepository.findByAppUserIdAndDeployedOnToday(userId, startOfDay, endOfDay);
        if (orderList.isEmpty()) {
            logger.info("No orders found for user with ID: {}", userId);
        } else {
            logger.info("{} orders found for user with ID: {}", orderList.size(), userId);
        }
        return orderList;
    }

    private List<OrderTableResponseDto> mapOrdersToDtos(List<Order> orderList, Long userId, String context) {
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd-MMM-yyyy HH:mm:ss", Locale.ENGLISH);
        List<OrderTableResponseDto> res = new ArrayList<>();
        orderList.forEach(order -> {
            OrderTableResponseDto dto = new OrderTableResponseDto();
            dto.setOrderId(order.getId());
            dto.setStatus(order.getStatus());
            dto.setSName(order.getStrategy().getName());
            dto.setQuantity(order.getQuantity());
            dto.setPrice(order.getPrice()/ (AMOUNT_MULTIPLIER * 1.0));
            dto.setSNo(order.getAppOrderID());
            dto.setSymbol(order.getInstrumentName());
            if (order.getExchangeTransactTime() != null && !order.getExchangeTransactTime().isEmpty()) {
                logger.info("Mapping exchange transact time for order ID: {}  , exchangeTime : {}", order.getId(), order.getExchangeTransactTime());
                String timestamp = order.getExchangeTransactTime();
                Instant instant;
                try {
                    instant = Instant.parse(timestamp);
                } catch (Exception e) {
                    LocalDateTime ldt = LocalDateTime.parse(timestamp, formatter);
                    instant = ldt.atZone(ZoneId.systemDefault()).toInstant();
                }
                LocalDateTime dateTime = instant.atZone(ZoneId.systemDefault()).toLocalDateTime();
                dto.setDateTime(dateTime.format(DateTimeFormatter.ofPattern("dd-MMM-yyyy HH:mm:ss")));
            }
            res.add(dto);
        });
        return res;
    }
}
