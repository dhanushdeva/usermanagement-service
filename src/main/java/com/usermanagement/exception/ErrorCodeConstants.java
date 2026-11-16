package com.usermanagement.exception;

/**
 * Error codes and messages raised by the identity provider layer (same as bm-usermanagement-v4).
 */
public final class ErrorCodeConstants {

    public static final String CONNECTION_TIMEOUT = "UM001";
    public static final String INVALID_CONFIG = "UM002";
    public static final String UNAUTHORIZED = "UM003";
    public static final String USER_NOT_FOUND = "UM004";
    public static final String USER_EXISTS = "UM005";
    public static final String ROLE_EXISTS = "UM006";
    public static final String ROLE_NOT_FOUND = "UM007";
    public static final String REALM_NOT_FOUND = "UM008";
    public static final String INVALID_CREDENTIALS = "UM009";
    public static final String INVALID_CLIENT_ID = "UM010";
    public static final String MISSING_SMTP_CONFIG = "UM112";
    public static final String UNHANDLED = "UM999";

    public static final String MSG_CONNECTION_TIMEOUT = "Connection timed out with keycloak";
    public static final String MSG_INVALID_CONFIG = "Invalid Keycloak Configuration Found";
    public static final String MSG_INVALID_CLIENT_ID = "Invalid Client ID";
    public static final String MSG_INVALID_CREDENTIALS = "Invalid Credentials";
    public static final String MSG_UNAUTHORIZED_KEYCLOAK = "Keycloak credentials are not authorized to perform this action";
    public static final String MSG_USER_NOT_FOUND = "User not found";
    public static final String MSG_ROLE_NOT_FOUND = "Role not found";
    public static final String MSG_REALM_NOT_FOUND = "Realm not found";
    public static final String MSG_ROLE_EXISTS = "Role already exists";
    public static final String MSG_USER_EXISTS = "User already exists";
    public static final String MSG_UNHANDLED = "Unhandled Exception Occurred";
    public static final String MSG_SMPT_CONFIG = "Please configure SMTP and enable email verification";

    private ErrorCodeConstants() {
    }
}
