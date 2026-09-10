package com.quantlab.common.repository;

import com.quantlab.common.entity.Underlying;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface UnderlyingRespository extends JpaRepository<Underlying, Long> {
    Optional<Underlying> findByName(String name);
}

