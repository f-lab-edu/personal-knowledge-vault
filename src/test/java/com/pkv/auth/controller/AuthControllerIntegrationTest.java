package com.pkv.auth.controller;

import com.pkv.common.security.JwtTokenProvider;
import com.pkv.member.domain.Member;
import com.pkv.member.repository.MemberRepository;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class AuthControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private MemberRepository memberRepository;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    private Member testMember;
    private String validAccessToken;

    @BeforeEach
    void setUp() {
        testMember = memberRepository.save(Member.builder()
                .googleId("google-123")
                .email("test@example.com")
                .name("Test User")
                .build());

        validAccessToken = jwtTokenProvider.createAccessToken(testMember.getId(), testMember.getEmail());
    }

    @Test
    @DisplayName("유효한 토큰으로 /api/auth/me 호출 시 사용자 정보를 반환한다")
    void getCurrentUser_withValidToken_returnsUserInfo() throws Exception {
        mockMvc.perform(get("/api/auth/me")
                        .cookie(new Cookie("access_token", validAccessToken))
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value(testMember.getId()))
                .andExpect(jsonPath("$.data.email").value("test@example.com"))
                .andExpect(jsonPath("$.data.name").value("Test User"));
    }

    @Test
    @DisplayName("토큰 없이 /api/auth/me 호출 시 401 에러를 반환한다")
    void getCurrentUser_withoutToken_returnsUnauthorized() throws Exception {
        mockMvc.perform(get("/api/auth/me")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("A002"));
    }

    @Test
    @DisplayName("유효하지 않은 토큰으로 /api/auth/me 호출 시 401 에러를 반환한다")
    void getCurrentUser_withInvalidToken_returnsUnauthorized() throws Exception {
        mockMvc.perform(get("/api/auth/me")
                        .cookie(new Cookie("access_token", "invalid-token"))
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("A002"));
    }

    @Test
    @DisplayName("로그아웃 시 쿠키 삭제 헤더가 반환된다")
    void logout_deletesCookies() throws Exception {
        mockMvc.perform(post("/api/auth/logout")
                        .cookie(new Cookie("access_token", validAccessToken))
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(header().exists("Set-Cookie"));
    }
}
