package com.english.education.constant;

public class MessageConstant {

    private MessageConstant() {
        // prevent instantiation
    }

    // Authentication
    public static final String USER_NAME_CANNOT_BE_NULL = "User name can not be null";
    public static final String PASSWORD_CANNOT_BE_NULL = "Password can not be null";
    public static final String FULL_NAME_CANNOT_BE_NULL = "Full name can not be null";
    public static final String ROLE_CANNOT_BE_NULL = "Role can not be null";
    public static final String ROLE_CANNOT_BE_EMPTY = "Role can not be empty";
    public static final String AUTH_STATE_CORRUPTED = "Auth state corrupted";
    public static final String AUTH_TRY_AGAIN_LATER = "Auth try again later";


    // User
    public static final String USER_NOT_FOUND = "User not found";
    public static final String CREATE_ACCOUNT_SUCCESS = "Create account success";
    public static final String USER_IS_LOCKED = "User is locked";
    public static final String INVALID_USER_NAME_OR_PASSWORD = "Invalid user name or password";
}
