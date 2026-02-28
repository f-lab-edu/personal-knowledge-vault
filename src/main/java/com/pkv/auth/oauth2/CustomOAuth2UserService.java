package com.pkv.auth.oauth2;

import com.pkv.member.domain.Member;
import com.pkv.member.repository.MemberRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class CustomOAuth2UserService extends DefaultOAuth2UserService {

    private final MemberRepository memberRepository;

    @Override
    @Transactional
    public OAuth2User loadUser(OAuth2UserRequest userRequest) throws OAuth2AuthenticationException {
        OAuth2User oAuth2User = super.loadUser(userRequest);
        Map<String, Object> oauthAttributes = oAuth2User.getAttributes();

        OAuth2UserInfo userInfo = OAuth2UserInfo.from(oauthAttributes);

        Member member = memberRepository.findByGoogleId(userInfo.googleId())
                .map(existingMember -> {
                    existingMember.restoreIfDeleted();
                    existingMember.updateProfile(userInfo.email(), userInfo.name());
                    return existingMember;
                })
                .orElseGet(() -> memberRepository.save(
                        Member.builder()
                                .googleId(userInfo.googleId())
                                .email(userInfo.email())
                                .name(userInfo.name())
                                .build()
                ));

        Map<String, Object> attributes = new HashMap<>(oauthAttributes);
        attributes.put("memberId", member.getId());

        return new DefaultOAuth2User(
                Collections.singleton(new SimpleGrantedAuthority("ROLE_USER")),
                attributes,
                "sub"
        );
    }
}
