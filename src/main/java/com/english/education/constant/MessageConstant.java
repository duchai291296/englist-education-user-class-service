package com.english.education.constant;

public class MessageConstant {

    private MessageConstant() {
        // prevent instantiation
    }

    // Redis
    public static final String AUTH_TRY_AGAIN_LATER = "Auth try again later";
    public static final String SYSTEM_TEMPORARILY_UNAVAILABLE = "System temporarily unavailable";

    // Authentication
    public static final String USER_NAME_CANNOT_BE_NULL = "User name can not be null";
    public static final String PASSWORD_CANNOT_BE_NULL = "Password can not be null";
    public static final String FULL_NAME_CANNOT_BE_NULL = "Full name can not be null";
    public static final String DEVICE_TYPE_CANNOT_BE_NULL = "Device type can not be null";
    public static final String INVALID_DEVICE_TYPE = "Invalid device type";
    public static final String ROLE_CANNOT_BE_NULL = "Role can not be null";
    public static final String ROLE_CANNOT_BE_EMPTY = "Role can not be empty";
    public static final String BACK_TO_LOGIN = "Back to login";
    public static final String INVALID_TOKEN_VER = "Invalid token ver";


    // User
    public static final String USER_NOT_FOUND = "User not found";
    public static final String CREATE_ACCOUNT_SUCCESS = "Create account success";
    public static final String USER_IS_LOCKED = "User is locked";
    public static final String INVALID_USER_NAME_OR_PASSWORD = "Invalid user name or password";
    public static final String LOGOUT_SUCCESS = "Logout success";
    public static final String USER_ALREADY_LOCKED = "User already locked";
    public static final String USER_LOCKED_SUCCESS = "User locked success";

    // Token
    public static final String INVALID_REFRESH_TOKEN_FORMAT = "Invalid refresh token format";
    public static final String INVALID_ACCESS_TOKEN = "Invalid access token";
    public static final String REFRESH_TOKEN_NOT_FOUND = "Refresh token not found";
    public static final String REFRESH_TOKEN_CANNOT_BE_NULL = "Refresh token can not be null";
    public static final String REFRESH_TOKEN_REVOKED = "Refresh token revoked";
    public static final String REFRESH_TOKEN_EXPIRED = "Refresh token expired";
    public static final String REFRESH_TOKEN_REUSED = "Refresh token reused";
}
