package com.quantlab.common.repository;

import com.quantlab.common.entity.UserAdmin;
import com.quantlab.common.entity.UserAuthConstants;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface UserAdminRepository extends JpaRepository<UserAdmin,Long> {

}
