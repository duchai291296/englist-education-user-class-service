package com.english.education.event;

public record UserLockEvent(String tokenVerKeyPC, String tokenVerKeyMobile, String lockedKey) {
}
