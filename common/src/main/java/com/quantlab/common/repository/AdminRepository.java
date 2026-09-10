package com.quantlab.common.repository;

import com.quantlab.common.entity.UserAdmin;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface AdminRepository extends JpaRepository<UserAdmin, Long> {

    UserAdmin findById(long id);

    Optional<UserAdmin> findByEmail(String email);

}
