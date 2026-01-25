package com.english.education.model.repository.refreshtoken;

import com.english.education.model.entity.RefreshToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {
    Optional<RefreshToken> findByTokenHash(String tokenHash);

    @Modifying
    @Query("""
        UPDATE RefreshToken rt
        SET rt.revoked = true,
            rt.revokedAt = CURRENT_TIMESTAMP
            WHERE rt.tokenHash = :hash
            AND rt.revoked = false
    """)
    int revokeIfNotRevoked(@Param("hash") String tokenHash);

    /**
     * Revoke active refresh token for a specific user and device.
     * Used to ensure only one active token per (userId, deviceType).
     *
     * @param userId user ID
     * @param deviceType device type (PC / MOBILE)
     */
    @Modifying
    @Query("""
        UPDATE RefreshToken rt
        SET rt.revoked = true,
            rt.revokedAt = CURRENT_TIMESTAMP
        WHERE rt.userId = :userId
        AND rt.deviceType = :deviceType
        AND rt.revoked = false
    """)
    void revokeActiveTokenByUserAndDevice(@Param("userId") Integer userId, @Param("deviceType") String deviceType);

    @Modifying
    @Query("""
        UPDATE RefreshToken rt
        SET rt.revoked = true,
            rt.revokedAt = CURRENT_TIMESTAMP
        WHERE rt.userId = :userId
        AND rt.revoked = false
    """)
    void revokeAllActiveTokensByUser(@Param("userId") Integer userId);
}
