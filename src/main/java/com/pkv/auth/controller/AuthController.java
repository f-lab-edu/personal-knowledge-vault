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

    /**
     * 프론트엔드가 앱 로딩 시 로그인 여부를 확인하기 위해 호출한다.
     * 브라우저가 access_token 쿠키를 자동 전송하며, 유효하면 사용자 정보를 응답한다.
     * 401이 반환되면 프론트엔드가 refresh_token 쿠키로 토큰 갱신을 시도한다.
     */
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

    /**
     * 프론트엔드에서 로그아웃 호출할때 사용한다.
     * access_token과 refresh_token 쿠키를 삭제하여 인증 상태를 해제한다.
     */
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

    /**
     * 프론트엔드가 API 호출 중 401을 받으면 자동으로 호출하여 access_token을 재발급받는다.
     * refresh_token 쿠키를 검증한 뒤 새 access_token을 쿠키로 내려준다.
     * 갱신마저 실패하면 프론트엔드는 로그인 페이지로 이동시킨다.
     */
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
