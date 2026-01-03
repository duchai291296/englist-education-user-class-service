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
    public static final String USER_ID_CANNOT_BE_NULL = "User id can not be null";
    public static final String PASSWORD_CANNOT_BE_NULL = "Password can not be null";
    public static final String FULL_NAME_CANNOT_BE_NULL = "Full name can not be null";
    public static final String DEVICE_TYPE_CANNOT_BE_NULL = "Device type can not be null";
    public static final String INVALID_DEVICE_TYPE = "Invalid device type";
    public static final String INVALID_EMAIL = "Invalid email";
    public static final String ROLE_CANNOT_BE_NULL = "Role can not be null";
    public static final String ROLE_CANNOT_BE_EMPTY = "Role can not be empty";
    public static final String INVALID_ROLE = "Invalid role";
    public static final String BACK_TO_LOGIN = "Back to login";
    public static final String INVALID_TOKEN_VER = "Invalid token ver";


    // User
    public static final String USER_NOT_FOUND = "User not found";
    public static final String CREATE_ACCOUNT_SUCCESS = "Create account success";
    public static final String USER_IS_LOCKED = "User is locked";
    public static final String INVALID_USER_NAME_OR_PASSWORD = "Invalid user name or password";
    public static final String LOGOUT_SUCCESS = "Logout success";
    public static final String USER_ALREADY_LOCKED = "User already locked";
    public static final String USER_ALREADY_UNLOCKED = "User already unlocked";
    public static final String USER_LOCKED_SUCCESS = "User locked success";
    public static final String USER_UNLOCKED_SUCCESS = "User unlocked success";
    public static final String USER_UPDATE_SUCCESS = "User update success";
    public static final String CAN_NOT_UPDATE_THIS_USER = "Can not update this user";
    public static final String CONFIRM_PASSWORD_NOT_MATCH = "Confirm password not match";
    public static final String CONFIRM_PASSWORD_CANNOT_BE_NULL = "Confirm password can not be null";
    public static final String OLD_PASSWORD_CANNOT_BE_NULL = "Old password can not be null";
    public static final String THE_OLD_PASSWORD_IS_INCORRECT = "The old password is incorrect";
    public static final String CHANGE_PASSWORD_SUCCESS = "Change password success";
    public static final String IS_DELETE_CANNOT_BE_NULL = "Is delete can not be null";
    public static final String CHANGE_AVATAR_SUCCESS = "Change avatar success";
    public static final String USER_ID_REQUIRED = "user id is required";
    public static final String USER_ID_NOT_ALLOWED = "user id is not allowed to be null";
    public static final String INVALID_AVATAR_ACTION = "Invalid avatar action";
    public static final String CANNOT_DELETE_ADMIN = "Can not delete admin";
    public static final String DELETE_USER_SUCCESS = "Delete user success";

    // Token
    public static final String INVALID_REFRESH_TOKEN_FORMAT = "Invalid refresh token format";
    public static final String INVALID_ACCESS_TOKEN = "Invalid access token";
    public static final String REFRESH_TOKEN_NOT_FOUND = "Refresh token not found";
    public static final String REFRESH_TOKEN_CANNOT_BE_NULL = "Refresh token can not be null";
    public static final String REFRESH_TOKEN_REVOKED = "Refresh token revoked";
    public static final String REFRESH_TOKEN_EXPIRED = "Refresh token expired";
    public static final String REFRESH_TOKEN_REUSED = "Refresh token reused";

    // Image/Cloudinary
    public static final String IMAGE_FILE_REQUIRED = "Image file is required";
    public static final String IMAGE_INVALID_FORMAT = "Invalid image format. Only image files are allowed";
    public static final String IMAGE_FILE_TOO_LARGE = "Image file is too large. Maximum size is 10MB";
    public static final String IMAGE_UPLOAD_FAILED = "Failed to upload image";
    public static final String IMAGE_PUBLIC_ID_REQUIRED = "Image public ID is required";
    public static final String IMAGE_DELETE_SUCCESS = "Image deleted successfully";
    public static final String IMAGE_DELETE_FAILED = "Failed to delete image";
}
