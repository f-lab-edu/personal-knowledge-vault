package com.pkv.member.repository;

import com.pkv.member.domain.Member;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface MemberRepository extends JpaRepository<Member, Long> {

    boolean existsByIdAndDeletedAtIsNull(Long id);

    // 삭제되지 않은 사용자만 조회 (기존 findById 대체)
    Optional<Member> findByIdAndDeletedAtIsNull(Long id);

    // 삭제 여부 상관없이 조회 (OAuth2 로그인용)
    Optional<Member> findByGoogleId(String googleId);
}
