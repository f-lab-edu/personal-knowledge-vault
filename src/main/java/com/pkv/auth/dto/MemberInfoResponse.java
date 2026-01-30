package com.pkv.auth.dto;

import com.pkv.member.domain.Member;

public record MemberInfoResponse(
        Long id,
        String email,
        String name
) {
    public static MemberInfoResponse from(Member member) {
        return new MemberInfoResponse(
                member.getId(),
                member.getEmail(),
                member.getName()
        );
    }
}
