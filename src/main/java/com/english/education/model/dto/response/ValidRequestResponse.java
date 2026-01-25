package com.english.education.model.dto.response;

import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ValidRequestResponse {
    Integer userId;
    String deviceType;
    Long tokenVer;
    String tokenVerKey;
    String lockedKey;
}
