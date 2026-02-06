package com.pkv.auth.dto;

import com.pkv.member.domain.Member;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "회원 정보 응답")
public record MemberInfoResponse(
        @Schema(description = "회원 ID", example = "1") Long id,
        @Schema(description = "이메일", example = "user@example.com") String email,
        @Schema(description = "이름", example = "홍길동") String name
) {
    public static MemberInfoResponse from(Member member) {
        return new MemberInfoResponse(
                member.getId(),
                member.getEmail(),
                member.getName()
        );
    }
}
