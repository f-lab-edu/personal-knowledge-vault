package com.pkv.member.service;

import com.pkv.common.exception.ErrorCode;
import com.pkv.common.exception.PkvException;
import com.pkv.member.domain.Member;
import com.pkv.member.repository.MemberRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MemberService {

    private final MemberRepository memberRepository;

    @Transactional
    public void withdraw(Long memberId) {
        Member member = memberRepository.findByIdAndDeletedAtIsNull(memberId)
                .orElseThrow(() -> new PkvException(ErrorCode.MEMBER_NOT_FOUND));
        member.softDelete();
        log.info("Member withdrawn: id={}", memberId);
    }
}
