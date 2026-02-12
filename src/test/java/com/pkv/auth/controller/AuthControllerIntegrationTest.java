package com.pkv.auth.controller;

import com.pkv.auth.jwt.JwtTokenProvider;
import com.pkv.member.domain.Member;
import com.pkv.member.repository.MemberRepository;
import com.pkv.source.service.EmbeddingJobProducer;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.store.embedding.EmbeddingStore;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
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

    @MockitoBean // 테스트 환경에 Kafka가 없으므로 Mock으로 대체
    private EmbeddingJobProducer embeddingJobProducer;

    @MockitoBean // 테스트 환경에 Qdrant가 없으므로 Mock으로 대체
    private EmbeddingStore<TextSegment> embeddingStore;

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
    @DisplayName("로그아웃 시 access_token과 refresh_token 쿠키 삭제 헤더가 반환된다")
    void logout_deletesCookies() throws Exception {
        var result = mockMvc.perform(post("/api/auth/logout")
                        .cookie(new Cookie("access_token", validAccessToken))
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(header().exists("Set-Cookie"))
                .andReturn();

        List<String> setCookieHeaders = result.getResponse().getHeaders("Set-Cookie");
        assertThat(setCookieHeaders).hasSize(2);
        assertThat(setCookieHeaders).anyMatch(h -> h.contains("access_token"));
        assertThat(setCookieHeaders).anyMatch(h -> h.contains("refresh_token"));
    }

    @Test
    @DisplayName("유효한 refresh token으로 /api/auth/refresh 호출 시 새 access token을 발급한다")
    void refresh_withValidRefreshToken_returnsNewAccessToken() throws Exception {
        String refreshToken = jwtTokenProvider.createRefreshToken(testMember.getId());

        mockMvc.perform(post("/api/auth/refresh")
                        .cookie(new Cookie("refresh_token", refreshToken))
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(header().exists("Set-Cookie"))
                .andExpect(header().string("Set-Cookie", org.hamcrest.Matchers.containsString("access_token")));
    }

    @Test
    @DisplayName("refresh token 없이 /api/auth/refresh 호출 시 A006 에러를 반환한다")
    void refresh_withoutRefreshToken_returnsNotFound() throws Exception {
        mockMvc.perform(post("/api/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("A006"));
    }

    @Test
    @DisplayName("유효하지 않은 refresh token으로 /api/auth/refresh 호출 시 A005 에러를 반환한다")
    void refresh_withInvalidRefreshToken_returnsExpired() throws Exception {
        mockMvc.perform(post("/api/auth/refresh")
                        .cookie(new Cookie("refresh_token", "invalid-token"))
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("A005"));
    }

    @Test
    @DisplayName("access token을 refresh token으로 사용 시 A004 에러를 반환한다")
    void refresh_withAccessTokenAsRefreshToken_returnsInvalidToken() throws Exception {
        mockMvc.perform(post("/api/auth/refresh")
                        .cookie(new Cookie("refresh_token", validAccessToken))
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("A004"));
    }
}
