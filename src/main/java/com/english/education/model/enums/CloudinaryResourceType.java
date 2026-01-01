package com.english.education.model.enums;

public enum CloudinaryResourceType {
    IMAGE("image"),
    VIDEO("video"),
    RAW("raw");

    private final String value;

    CloudinaryResourceType(String value) {
        this.value = value;
    }

    public String value() {
        return value;
    }

}
