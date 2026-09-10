package com.quantlab.common.repository;

import com.quantlab.common.entity.UserRole;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface userRoleRepository extends JpaRepository<UserRole,Long> {
}
