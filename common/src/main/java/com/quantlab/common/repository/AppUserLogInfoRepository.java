package com.quantlab.common.repository;

import com.quantlab.common.entity.AppUser;
import com.quantlab.common.entity.AppUserLogInfo;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface AppUserLogInfoRepository extends JpaRepository<AppUserLogInfo, Long> {
    List<AppUserLogInfo> findAllByAppUser_IdOrderByTokenGeneratedTimeDesc(Long id);

    Optional<AppUserLogInfo> findTopByAppUserOrderByLoggedinTimeDesc(AppUser appUser);

}
