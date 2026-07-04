package org.asvosonk.member.application.usecase;

import lombok.RequiredArgsConstructor;
import org.asvosonk.member.domain.model.Member;
import org.asvosonk.member.domain.repository.MemberRepository;
import org.asvosonk.member.presentation.request.MemberRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class UpdateMemberUseCase {

    private final MemberRepository memberRepository;

    @Transactional
    public Member execute(Long id, MemberRequest request) {
        Member member = memberRepository.findById(id)
            .orElseThrow(() -> new IllegalArgumentException("Membre introuvable : " + id));

        member.updateProfile(
            request.getFullName().trim(),
            request.getPhone() != null ? request.getPhone().trim() : null,
            request.getJoinDate(),
            request.isResident()
        );
        return memberRepository.save(member);
    }
}
