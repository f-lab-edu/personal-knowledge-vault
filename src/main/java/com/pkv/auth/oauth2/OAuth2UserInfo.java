package com.pkv.auth.oauth2;

import java.util.Map;

public record OAuth2UserInfo(
        String googleId,
        String email,
        String name
) {

    public static OAuth2UserInfo from(Map<String, Object> attributes) {
        return new OAuth2UserInfo(
                (String) attributes.get("sub"),
                (String) attributes.get("email"),
                (String) attributes.get("name")
        );
    }
}
