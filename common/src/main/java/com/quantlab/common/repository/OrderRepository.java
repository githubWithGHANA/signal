package com.quantlab.common.repository;

import com.quantlab.common.dao.ExecutedOrdersDao;
import com.quantlab.common.entity.AppUser;
import com.quantlab.common.entity.Order;
import com.quantlab.common.entity.Signal;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Repository
@EnableJpaRepositories
public interface OrderRepository extends JpaRepository<Order,Long> {

     Page<Order> findAllByOrderByUpdatedAtDesc(Pageable pageable);

     Page<Order> findAll(Specification<Order> spec, Pageable pageable);

     List<Order> findByAppUser(AppUser appUser);

     // Get today's orders for a specific user
     @Query("SELECT o FROM Order o WHERE o.userAdmin.id = :userId AND o.deployedOn >= :startOfDay AND o.deployedOn < :endOfDay")
     List<Order> findByUserAdminIdAndDeployedOnToday(
             @Param("userId") Long userId,
             @Param("startOfDay") Instant startOfDay,
             @Param("endOfDay") Instant endOfDay
     );

     @Query("SELECT o FROM Order o WHERE o.appUser.id = :userId AND o.deployedOn >= :startOfDay AND o.deployedOn < :endOfDay")
     List<Order> findByAppUserIdAndDeployedOnToday(
             @Param("userId") Long userId,
             @Param("startOfDay") Instant startOfDay,
             @Param("endOfDay") Instant endOfDay
     );

     Optional<Order> getByAppOrderID(String appOrderID);

     @Query("""
    SELECT new com.quantlab.common.dao.ExecutedOrdersDao(
        o.exchangeOrderId,
        o.instrumentName,
        o.orderSide,
        o.cumulativeQuantity,
        o.averageTradedPrice,
        o.appUser.tenentId,
        o.appUser.userName,
        o.strategy.name,
        o.exchangeTransactTime
    )
    FROM Order o
    WHERE o.status = 'complete'
      AND o.createdAt >= :startOfDay
""")
     List<ExecutedOrdersDao> findExecutedOrders(@Param("startOfDay") Instant startOfDay);

     Optional<Order> findTopBySignalIdOrderByCreatedAtDesc(Long signalId);

     long countBySignalId(Long signalId);

     Optional<Order> findByOrderUniqueIdentifier(String id);
}
