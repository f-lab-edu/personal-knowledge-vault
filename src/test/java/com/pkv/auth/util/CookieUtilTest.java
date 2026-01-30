package com.pkv.auth.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseCookie;

import static org.assertj.core.api.Assertions.assertThat;

class CookieUtilTest {

    @Test
    @DisplayName("쿠키 생성 시 HttpOnly, Secure, SameSite=Lax 속성이 설정된다")
    void createCookie_setsSecurityAttributes() {
        ResponseCookie cookie = CookieUtil.createCookie("testCookie", "testValue", 3600);

        assertThat(cookie.getName()).isEqualTo("testCookie");
        assertThat(cookie.getValue()).isEqualTo("testValue");
        assertThat(cookie.getMaxAge().getSeconds()).isEqualTo(3600);
        assertThat(cookie.isHttpOnly()).isTrue();
        assertThat(cookie.isSecure()).isTrue();
        assertThat(cookie.getSameSite()).isEqualTo("Lax");
        assertThat(cookie.getPath()).isEqualTo("/");
    }
}
