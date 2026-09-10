package com.quantlab.common.repository;

import com.quantlab.common.entity.EntryDetails;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface EntryDetailsRepository extends JpaRepository<EntryDetails, Long> {

    java.util.Optional<EntryDetails> findByStrategy_Id(Long strategyId);

    // New: batch fetch
    List<EntryDetails> findAllByStrategy_IdIn(List<Long> strategyIds);
}
