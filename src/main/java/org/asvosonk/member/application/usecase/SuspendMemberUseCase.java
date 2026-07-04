package org.asvosonk.member.application.usecase;

import lombok.RequiredArgsConstructor;
import org.asvosonk.member.domain.model.Member;
import org.asvosonk.member.domain.repository.MemberRepository;
import org.asvosonk.member.domain.valueobject.MemberStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class SuspendMemberUseCase {

    private final MemberRepository memberRepository;

    @Transactional
    public Member changeStatus(Long id, MemberStatus newStatus) {
        Member member = memberRepository.findById(id)
            .orElseThrow(() -> new IllegalArgumentException("Membre introuvable : " + id));

        member.changeStatus(newStatus);
        return memberRepository.save(member);
    }

    @Transactional
    public Member suspend(Long id) {
        return changeStatus(id, MemberStatus.suspended);
    }

    @Transactional
    public Member reinstate(Long id) {
        return changeStatus(id, MemberStatus.active);
    }
}
