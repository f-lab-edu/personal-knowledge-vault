package com.pkv.auth.service;

import com.pkv.auth.dto.MemberInfoResponse;
import com.pkv.auth.dto.TokenRefreshResult;
import com.pkv.auth.jwt.JwtTokenProvider;
import com.pkv.common.exception.ErrorCode;
import com.pkv.common.exception.PkvException;
import com.pkv.member.domain.Member;
import com.pkv.member.repository.MemberRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AuthService {

    private final MemberRepository memberRepository;
    private final JwtTokenProvider jwtTokenProvider;

    public MemberInfoResponse getCurrentMember(Long memberId) {
        Member member = memberRepository.findByIdAndDeletedAtIsNull(memberId)
                .orElseThrow(() -> new IllegalStateException("Member not found: " + memberId));
        return MemberInfoResponse.from(member);
    }

    public TokenRefreshResult refreshAccessToken(String refreshToken) {
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
        Member member = memberRepository.findByIdAndDeletedAtIsNull(memberId)
                .orElseThrow(() -> new PkvException(ErrorCode.INVALID_TOKEN));

        String newAccessToken = jwtTokenProvider.createAccessToken(memberId, member.getEmail());
        long maxAgeSeconds = jwtTokenProvider.getAccessTokenExpiry() / 1000;

        return new TokenRefreshResult(newAccessToken, maxAgeSeconds);
    }
}
