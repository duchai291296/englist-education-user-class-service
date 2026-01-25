package com.english.education.model.entity;

import com.english.education.model.enums.Status;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
@Builder
@Table(name = "user_sessions", uniqueConstraints = {
        @UniqueConstraint(name = "uk_user_service", columnNames = {"user_id", "device_type"})
})
public class UserSession {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Integer userId;

    @Column(name = "device_type", nullable = false, length = 10)
    private String deviceType;

    @Column(name = "token_version", nullable = false)
    private Long tokenVersion;

    @Column(name = "status", nullable = false, length = 20)
    @Enumerated(EnumType.STRING)
    private Status status;

    @Column(name = "last_login")
    private LocalDateTime lastLogin;

    @Column(name = "created_at", updatable = false, insertable = false)
    private LocalDateTime createdAt;
}
