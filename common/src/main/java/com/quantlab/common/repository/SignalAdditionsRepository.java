package com.quantlab.common.repository;

import com.quantlab.common.entity.Signal;
import com.quantlab.common.entity.SignalAdditions;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface SignalAdditionsRepository  extends JpaRepository<SignalAdditions, Long> {

    Optional<SignalAdditions> findById(Long id);

}

