package org.asvosonk.member.application.usecase;

import lombok.RequiredArgsConstructor;
import org.asvosonk.member.domain.model.Member;
import org.asvosonk.member.domain.repository.MemberRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class DeleteMemberUseCase {

    private final MemberRepository memberRepository;

    @Transactional
    public void execute(Long id) {
        Member member = memberRepository.findById(id)
            .orElseThrow(() -> new IllegalArgumentException("Membre introuvable : " + id));
        memberRepository.delete(member);
    }
}
