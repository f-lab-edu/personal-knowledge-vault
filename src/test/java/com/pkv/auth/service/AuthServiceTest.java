package com.pkv.auth.service;

import com.pkv.auth.dto.MemberInfoResponse;
import com.pkv.auth.dto.TokenRefreshResult;
import com.pkv.auth.jwt.JwtTokenProvider;
import com.pkv.common.exception.ErrorCode;
import com.pkv.common.exception.PkvException;
import com.pkv.member.domain.Member;
import com.pkv.member.repository.MemberRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private MemberRepository memberRepository;

    @Mock
    private JwtTokenProvider jwtTokenProvider;

    @InjectMocks
    private AuthService authService;

    private Member testMember;

    @BeforeEach
    void setUp() {
        testMember = Member.builder()
                .googleId("google-123")
                .email("test@example.com")
                .name("Test User")
                .build();
    }

    @Nested
    @DisplayName("getCurrentMember")
    class GetCurrentMember {

        @Test
        @DisplayName("회원 ID로 회원 정보를 조회한다")
        void success() {
            // given
            Long memberId = 1L;
            given(memberRepository.findByIdAndDeletedAtIsNull(memberId)).willReturn(Optional.of(testMember));

            // when
            MemberInfoResponse response = authService.getCurrentMember(memberId);

            // then
            assertThat(response.email()).isEqualTo("test@example.com");
            assertThat(response.name()).isEqualTo("Test User");
        }

        @Test
        @DisplayName("존재하지 않는 회원 ID로 조회 시 예외가 발생한다")
        void memberNotFound() {
            // given
            Long memberId = 999L;
            given(memberRepository.findByIdAndDeletedAtIsNull(memberId)).willReturn(Optional.empty());

            // when & then
            assertThatThrownBy(() -> authService.getCurrentMember(memberId))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("Member not found");
        }
    }

    @Nested
    @DisplayName("refreshAccessToken")
    class RefreshAccessToken {

        @Test
        @DisplayName("유효한 리프레시 토큰으로 새 액세스 토큰을 발급한다")
        void success() {
            // given
            String refreshToken = "valid-refresh-token";
            Long memberId = 1L;
            String newAccessToken = "new-access-token";
            long accessTokenExpiry = 3600000L;

            given(jwtTokenProvider.validateToken(refreshToken)).willReturn(true);
            given(jwtTokenProvider.validateRefreshToken(refreshToken)).willReturn(true);
            given(jwtTokenProvider.getMemberId(refreshToken)).willReturn(memberId);
            given(memberRepository.findByIdAndDeletedAtIsNull(memberId)).willReturn(Optional.of(testMember));
            given(jwtTokenProvider.createAccessToken(memberId, testMember.getEmail())).willReturn(newAccessToken);
            given(jwtTokenProvider.getAccessTokenExpiry()).willReturn(accessTokenExpiry);

            // when
            TokenRefreshResult result = authService.refreshAccessToken(refreshToken);

            // then
            assertThat(result.accessToken()).isEqualTo(newAccessToken);
            assertThat(result.maxAgeSeconds()).isEqualTo(3600L);
        }

        @Test
        @DisplayName("리프레시 토큰이 null이면 REFRESH_TOKEN_NOT_FOUND 예외가 발생한다")
        void refreshTokenNull() {
            // when & then
            assertThatThrownBy(() -> authService.refreshAccessToken(null))
                    .isInstanceOf(PkvException.class)
                    .satisfies(e -> assertThat(((PkvException) e).getErrorCode()).isEqualTo(ErrorCode.REFRESH_TOKEN_NOT_FOUND));
        }

        @Test
        @DisplayName("유효하지 않은 리프레시 토큰이면 REFRESH_TOKEN_EXPIRED 예외가 발생한다")
        void invalidToken() {
            // given
            String refreshToken = "invalid-token";
            given(jwtTokenProvider.validateToken(refreshToken)).willReturn(false);

            // when & then
            assertThatThrownBy(() -> authService.refreshAccessToken(refreshToken))
                    .isInstanceOf(PkvException.class)
                    .satisfies(e -> assertThat(((PkvException) e).getErrorCode()).isEqualTo(ErrorCode.REFRESH_TOKEN_EXPIRED));
        }

        @Test
        @DisplayName("액세스 토큰을 리프레시 토큰으로 사용하면 INVALID_TOKEN 예외가 발생한다")
        void accessTokenUsedAsRefreshToken() {
            // given
            String accessToken = "access-token";
            given(jwtTokenProvider.validateToken(accessToken)).willReturn(true);
            given(jwtTokenProvider.validateRefreshToken(accessToken)).willReturn(false);

            // when & then
            assertThatThrownBy(() -> authService.refreshAccessToken(accessToken))
                    .isInstanceOf(PkvException.class)
                    .satisfies(e -> assertThat(((PkvException) e).getErrorCode()).isEqualTo(ErrorCode.INVALID_TOKEN));
        }
    }
}
