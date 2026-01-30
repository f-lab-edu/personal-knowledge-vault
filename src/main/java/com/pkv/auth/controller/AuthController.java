package com.pkv.auth.controller;

import com.pkv.auth.AuthConstants;
import com.pkv.auth.dto.MemberInfoResponse;
import com.pkv.auth.dto.TokenRefreshResult;
import com.pkv.auth.service.AuthService;
import com.pkv.auth.util.CookieUtil;
import com.pkv.common.dto.ApiResponse;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Arrays;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @GetMapping("/me")
    public ResponseEntity<ApiResponse<MemberInfoResponse>> getCurrentUser(
            @AuthenticationPrincipal Long memberId) {
        MemberInfoResponse response = authService.getCurrentMember(memberId);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @PostMapping("/logout")
    public ResponseEntity<ApiResponse<Void>> logout() {
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, CookieUtil.deleteCookie(AuthConstants.ACCESS_TOKEN_COOKIE, AuthConstants.ACCESS_TOKEN_PATH).toString())
                .header(HttpHeaders.SET_COOKIE, CookieUtil.deleteCookie(AuthConstants.REFRESH_TOKEN_COOKIE, AuthConstants.REFRESH_TOKEN_PATH).toString())
                .body(ApiResponse.success());
    }

    @PostMapping("/refresh")
    public ResponseEntity<ApiResponse<Void>> refresh(HttpServletRequest request) {
        String refreshToken = extractRefreshTokenFromCookie(request);
        TokenRefreshResult result = authService.refreshAccessToken(refreshToken);

        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE,
                        CookieUtil.createCookie(AuthConstants.ACCESS_TOKEN_COOKIE, result.accessToken(), result.maxAgeSeconds(), AuthConstants.ACCESS_TOKEN_PATH).toString())
                .body(ApiResponse.success());
    }

    private String extractRefreshTokenFromCookie(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return null;
        }
        return Arrays.stream(cookies)
                .filter(cookie -> AuthConstants.REFRESH_TOKEN_COOKIE.equals(cookie.getName()))
                .map(Cookie::getValue)
                .findFirst()
                .orElse(null);
    }
}
