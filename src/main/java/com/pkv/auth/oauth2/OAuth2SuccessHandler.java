package com.pkv.auth.oauth2;

import com.pkv.common.security.JwtTokenProvider;
import com.pkv.common.util.CookieUtil;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
public class OAuth2SuccessHandler extends SimpleUrlAuthenticationSuccessHandler {

    private final JwtTokenProvider jwtTokenProvider;
    private final String frontendUrl;

    public OAuth2SuccessHandler(
            JwtTokenProvider jwtTokenProvider,
            @Value("${app.frontend-url:http://localhost:3000}") String frontendUrl) {
        this.jwtTokenProvider = jwtTokenProvider;
        this.frontendUrl = frontendUrl;
    }

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response,
                                        Authentication authentication) throws IOException {
        OAuth2User oAuth2User = (OAuth2User) authentication.getPrincipal();

        Long memberId = oAuth2User.getAttribute("memberId");
        String email = oAuth2User.getAttribute("email");

        String accessToken = jwtTokenProvider.createAccessToken(memberId, email);
        String refreshToken = jwtTokenProvider.createRefreshToken(memberId);

        long accessMaxAgeSeconds = jwtTokenProvider.getAccessTokenExpiry() / 1000;
        long refreshMaxAgeSeconds = jwtTokenProvider.getRefreshTokenExpiry() / 1000;

        response.addHeader(HttpHeaders.SET_COOKIE,
                CookieUtil.createCookie("access_token", accessToken, accessMaxAgeSeconds).toString());
        response.addHeader(HttpHeaders.SET_COOKIE,
                CookieUtil.createCookie("refresh_token", refreshToken, refreshMaxAgeSeconds).toString());

        getRedirectStrategy().sendRedirect(request, response, frontendUrl);
    }
}
