package com.quantlab.common.repository;

import com.quantlab.common.entity.ExitDetails;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ExitDetailsRepository extends JpaRepository<ExitDetails, Long> {

    java.util.Optional<ExitDetails> findByStrategy_Id(Long strategyId);

    // New: batch fetch
    List<ExitDetails> findAllByStrategy_IdIn(List<Long> strategyIds);
}
