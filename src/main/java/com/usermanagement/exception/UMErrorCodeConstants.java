package com.usermanagement.exception;

/**
 * Error codes for request validation in the controller (same as bm-usermanagement-v4).
 */
public final class UMErrorCodeConstants {

    public static final String MISSING_CONFIGURATION = "UM101";
    public static final String MISSING_USER_REALM = "UM102";
    public static final String MISSING_CLIENT_ID = "UM103";
    public static final String MISSING_CLIENT_SECRET = "UM104";
    public static final String MISSING_ROLE_NAME = "UM105";
    public static final String MISSING_REFRESH_TOKEN = "UM106";
    public static final String MISSING_USER_DETAILS = "UM107";
    public static final String MISSING_USER_IDS = "UM108";
    public static final String MISSING_ROLE_DETAILS = "UM109";
    public static final String MISSING_USER_ID = "UM110";
    public static final String MISSING_USER_STATUS = "UM111";
    public static final String MISSING_USER_EMAIL = "UM113";
    public static final String ADMIN_CLIENT_LOGIN_FAILED = "UM114";
    public static final String ADMIN_CLIENT_FORBIDDEN = "UM115";
    public static final String ADMIN_TOKEN_REJECTED = "UM118";
    public static final String ANONYMOUS_CLIENT_LOGIN_FAILED = "UM119";
    public static final String MISSING_USER_NAME = "UM116";
    public static final String MISSING_USER_ROLES = "UM117";

    private UMErrorCodeConstants() {
    }
}
