package com.english.education.model.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
public class RefreshToken {
    @Id
    private Integer userId;

    @Column(nullable = false)
    private String tokenHash;

    private LocalDateTime expiresAt;
    private boolean revoked;
    private LocalDateTime revokedAt;

}
