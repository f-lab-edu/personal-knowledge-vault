package com.pkv.auth.controller;

import com.pkv.auth.AuthConstants;
import com.pkv.auth.dto.MemberInfoResponse;
import com.pkv.auth.dto.TokenRefreshResult;
import com.pkv.auth.service.AuthService;
import com.pkv.auth.util.CookieUtil;
import com.pkv.common.dto.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
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

@Tag(name = "Auth", description = "인증 관련 API")
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @Operation(summary = "현재 사용자 정보 조회")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "조회 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "인증 실패")
    })
    @GetMapping("/me")
    public ResponseEntity<ApiResponse<MemberInfoResponse>> getCurrentUser(
            @AuthenticationPrincipal Long memberId) {
        MemberInfoResponse response = authService.getCurrentMember(memberId);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @Operation(summary = "로그아웃")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "로그아웃 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "인증 실패")
    })
    @PostMapping("/logout")
    public ResponseEntity<ApiResponse<Void>> logout() {
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, CookieUtil.deleteCookie(AuthConstants.ACCESS_TOKEN_COOKIE, AuthConstants.ACCESS_TOKEN_PATH).toString())
                .header(HttpHeaders.SET_COOKIE, CookieUtil.deleteCookie(AuthConstants.REFRESH_TOKEN_COOKIE, AuthConstants.REFRESH_TOKEN_PATH).toString())
                .body(ApiResponse.success());
    }

    @Operation(summary = "액세스 토큰 갱신")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "토큰 갱신 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "인증 실패")
    })
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
