package com.quantlab.common.repository;

import com.quantlab.common.entity.TokenLogInfo;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface TokenLogInfoRepository extends JpaRepository<TokenLogInfo, Long> {
}
