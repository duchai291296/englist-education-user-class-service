package com.english.education.model.repository.usersession;

import com.english.education.model.entity.UserSession;
import com.english.education.model.enums.Status;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface UserSessionRepository extends JpaRepository<UserSession, Long> {
    /**
     * Find user session by userId and deviceType.
     *
     * @param userId user identifier
     * @param deviceType device type (PC or MOBILE)
     * @return Optional UserSession
     */
    Optional<UserSession> findByUserIdAndDeviceType(Integer userId, String deviceType);

    /**
     * Atomically increment token version for a user session.
     * Returns the number of rows affected.
     *
     * @param userId user identifier
     * @param deviceType device type
     * @return number of rows affected
     */
    @Modifying(clearAutomatically = true)
    @Query("""
        UPDATE UserSession us
        SET us.tokenVersion = us.tokenVersion + 1,
            us.lastLogin = CURRENT_TIMESTAMP
        WHERE us.userId = :userId
        AND us.deviceType = :deviceType
    """)
    int incrementTokenVersion(@Param("userId") Integer userId, @Param("deviceType") String deviceType);

    /**
     * Upsert (insert or update) user session and atomically increment token version.
     * <p>
     * This method performs an atomic operation:
     * - If session exists: increment token_version and update last_login
     * - If session doesn't exist: create new session with token_version = 1
     * <p>
     * This prevents race conditions when multiple concurrent login requests occur.
     *
     * @param userId user identifier
     * @param deviceType device type (PC or MOBILE)
     * @param status user status (ACTIVE or INACTIVE)
     * @return the new token version after increment
     */
    @Modifying(clearAutomatically = true)
    @Query(value = """
                INSERT INTO user_sessions (user_id, device_type, token_version, status, last_login, created_at)
                VALUES (:userId, :deviceType, 1, :status, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                ON DUPLICATE KEY UPDATE
                    token_version = token_version + 1,
                    last_login = CURRENT_TIMESTAMP,
                    status = :status
            """, nativeQuery = true)
    int upsertAndIncrementTokenVersion(
            @Param("userId") Integer userId,
            @Param("deviceType") String deviceType,
            @Param("status") String status
    );

    /**
     * Update session status (for lock/unlock operations).
     *
     * @param userId user identifier
     * @param status new status
     */
    @Modifying
    @Query("""
        UPDATE UserSession us
        SET us.status = :status
        WHERE us.userId = :userId
    """)
    void updateStatusByUserId(@Param("userId") Integer userId, @Param("status") Status status);

    @Modifying
    @Query("""
        UPDATE UserSession us
        SET us.tokenVersion = us.tokenVersion + 1,
            us.lastLogin = CURRENT_TIMESTAMP
        WHERE us.userId = :userId
    """)
    int incrementTokenVersionByUserId(@Param("userId") Integer userId);

}
