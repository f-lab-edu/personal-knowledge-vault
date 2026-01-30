package com.pkv.auth.controller;

import com.pkv.auth.AuthConstants;
import com.pkv.auth.dto.MemberInfoResponse;
import com.pkv.common.dto.ApiResponse;
import com.pkv.common.exception.PkvException;
import com.pkv.common.exception.ErrorCode;
import com.pkv.auth.jwt.JwtTokenProvider;
import com.pkv.auth.util.CookieUtil;
import com.pkv.member.domain.Member;
import com.pkv.member.repository.MemberRepository;
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

    private final MemberRepository memberRepository;
    private final JwtTokenProvider jwtTokenProvider;

    @GetMapping("/me")
    public ResponseEntity<ApiResponse<MemberInfoResponse>> getCurrentUser(
            @AuthenticationPrincipal Long memberId) {
        Member member = memberRepository.findById(memberId)
                .orElseThrow(() -> new IllegalStateException("Member not found: " + memberId));

        return ResponseEntity.ok(ApiResponse.success(MemberInfoResponse.from(member)));
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

        if (refreshToken == null) {
            throw new PkvException(ErrorCode.REFRESH_TOKEN_NOT_FOUND);
        }

        if (!jwtTokenProvider.validateToken(refreshToken)) {
            throw new PkvException(ErrorCode.REFRESH_TOKEN_EXPIRED);
        }

        if (!jwtTokenProvider.validateRefreshToken(refreshToken)) {
            throw new PkvException(ErrorCode.INVALID_TOKEN);
        }

        Long memberId = jwtTokenProvider.getMemberId(refreshToken);
        Member member = memberRepository.findById(memberId)
                .orElseThrow(() -> new PkvException(ErrorCode.INVALID_TOKEN));

        String newAccessToken = jwtTokenProvider.createAccessToken(memberId, member.getEmail());
        long maxAgeSeconds = jwtTokenProvider.getAccessTokenExpiry() / 1000;

        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE,
                        CookieUtil.createCookie(AuthConstants.ACCESS_TOKEN_COOKIE, newAccessToken, maxAgeSeconds, AuthConstants.ACCESS_TOKEN_PATH).toString())
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
