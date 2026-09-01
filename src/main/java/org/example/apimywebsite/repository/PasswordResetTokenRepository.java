package org.example.apimywebsite.repository;


import org.example.apimywebsite.api.model.PasswordResetToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Optional;

public interface PasswordResetTokenRepository extends JpaRepository<PasswordResetToken, Long> {
    Optional<PasswordResetToken> findByTokenHashAndExpiresAtAfterAndUsedAtIsNull(String tokenHash, LocalDateTime now);
    void deleteByUserId(Integer userId);

    // COR-003 fix: atomic single-use claim. The WHERE clause (usedAt IS NULL) is re-evaluated by
    // the database against the current committed row, so under concurrent calls for the same
    // tokenHash only one UPDATE can match and return 1 - the loser's WHERE no longer matches once
    // the winner's row lock releases, so it deterministically returns 0. This is what makes the
    // token single-use under a race, not the earlier plain SELECT.
    @Modifying(clearAutomatically = true)
    @Query("UPDATE PasswordResetToken t SET t.usedAt = :now WHERE t.tokenHash = :tokenHash AND t.usedAt IS NULL AND t.expiresAt > :now")
    int markUsedIfUnexpiredAndUnused(@Param("tokenHash") String tokenHash, @Param("now") LocalDateTime now);
}
