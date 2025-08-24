package org.example.apimywebsite.repository;


import org.example.apimywebsite.api.model.PasswordResetToken;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.Optional;

public interface PasswordResetTokenRepository extends JpaRepository<PasswordResetToken, Long> {
    Optional<PasswordResetToken> findByTokenHashAndExpiresAtAfterAndUsedAtIsNull(String tokenHash, LocalDateTime now);
    void deleteByUserId(Integer userId);
}