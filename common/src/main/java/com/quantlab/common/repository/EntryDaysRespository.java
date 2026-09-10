package com.quantlab.common.repository;

import com.quantlab.common.entity.EntryDays;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface EntryDaysRespository extends JpaRepository<EntryDays, Long> {

    // Method to find entities where the id is in the specified list
    List<EntryDays> findByIdIn(List<Long> ids);
}
