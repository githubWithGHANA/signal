package com.quantlab.common.repository;

import com.quantlab.common.entity.AppUser;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.Set;

@Repository
public interface AppUserRepository extends JpaRepository<AppUser, Long> {
    List<AppUser> findAllByUserIdIn(Set<String> userIds);
    Optional<AppUser> findByUserAccountId(String userAccountId);
    Optional<AppUser> findById(Long userId);
    Optional<AppUser> findByTenentId(String userId);
    Optional<AppUser> findByUserRole_id(Long userId);

}
