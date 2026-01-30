package com.pkv.auth.controller;

import com.pkv.auth.dto.MemberInfoResponse;
import com.pkv.common.dto.ApiResponse;
import com.pkv.common.util.CookieUtil;
import com.pkv.member.domain.Member;
import com.pkv.member.repository.MemberRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private static final String ACCESS_TOKEN_COOKIE = "access_token";
    private static final String REFRESH_TOKEN_COOKIE = "refresh_token";

    private final MemberRepository memberRepository;

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
                .header(HttpHeaders.SET_COOKIE, CookieUtil.deleteCookie(ACCESS_TOKEN_COOKIE).toString())
                .header(HttpHeaders.SET_COOKIE, CookieUtil.deleteCookie(REFRESH_TOKEN_COOKIE).toString())
                .body(ApiResponse.success());
    }
}
