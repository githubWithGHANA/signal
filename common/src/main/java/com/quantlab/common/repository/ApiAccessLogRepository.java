package com.quantlab.common.repository;

import com.quantlab.common.entity.ApiAccessLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ApiAccessLogRepository extends JpaRepository<ApiAccessLog, Long> {

}
