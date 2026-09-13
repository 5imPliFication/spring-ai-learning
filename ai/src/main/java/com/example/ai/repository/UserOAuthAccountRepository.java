package com.example.ai.repository;

import com.example.ai.entity.UserOAuthAccount;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UserOAuthAccountRepository extends JpaRepository<UserOAuthAccount, Long> {
    Optional<UserOAuthAccount> findByUserId(String userId);
    boolean existsByUserId(String userId);
    void deleteByUserId(String userId);
}