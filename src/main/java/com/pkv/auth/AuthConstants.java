package com.pkv.auth;

import lombok.experimental.UtilityClass;

@UtilityClass
public class AuthConstants {
    public static final String ACCESS_TOKEN_COOKIE = "access_token";
    public static final String REFRESH_TOKEN_COOKIE = "refresh_token";
    public static final String ACCESS_TOKEN_PATH = "/api";
    public static final String REFRESH_TOKEN_PATH = "/api/auth";
}
