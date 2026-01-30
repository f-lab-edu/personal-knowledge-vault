package com.pkv.auth.dto;

public record TokenRefreshResult(
        String accessToken,
        long maxAgeSeconds
) {}
