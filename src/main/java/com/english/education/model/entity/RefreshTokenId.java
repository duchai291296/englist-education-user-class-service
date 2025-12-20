package com.english.education.model.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.io.Serializable;

@Embeddable
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class RefreshTokenId implements Serializable {
    @Column(name = "user_id")
    private Integer userId;
    @Column(name = "device_type")
    private String deviceType;
}
