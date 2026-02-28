package com.pkv.member.controller;

import com.pkv.auth.AuthConstants;
import com.pkv.auth.util.CookieUtil;
import com.pkv.common.dto.ApiResponse;
import com.pkv.member.service.MemberService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Member", description = "회원 관련 API")
@RestController
@RequestMapping("/api/members")
@RequiredArgsConstructor
public class MemberController {

    private final MemberService memberService;

    /**
     * 프론트엔드에서 회원 탈퇴 시도 시 호출한다.
     * 회원 정보를 soft-delete 처리하고, access_token과 refresh_token 쿠키를 삭제한다.
     * 프론트엔드는 로그아웃과 동일하게 인증 상태를 초기화하고 로그인 페이지로 이동한다.
     */
    @Operation(summary = "회원 탈퇴")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "탈퇴 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "인증 실패")
    })
    @DeleteMapping("/me")
    public ResponseEntity<ApiResponse<Void>> withdraw(@AuthenticationPrincipal Long memberId) {
        memberService.withdraw(memberId);
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, CookieUtil.deleteCookie(AuthConstants.ACCESS_TOKEN_COOKIE, AuthConstants.ACCESS_TOKEN_PATH).toString())
                .header(HttpHeaders.SET_COOKIE, CookieUtil.deleteCookie(AuthConstants.REFRESH_TOKEN_COOKIE, AuthConstants.REFRESH_TOKEN_PATH).toString())
                .body(ApiResponse.success());
    }
}
