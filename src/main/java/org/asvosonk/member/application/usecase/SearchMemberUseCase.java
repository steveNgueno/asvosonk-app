package org.asvosonk.member.application.usecase;

import lombok.RequiredArgsConstructor;
import org.asvosonk.member.domain.model.Member;
import org.asvosonk.member.domain.repository.MemberRepository;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class SearchMemberUseCase {

    private final MemberRepository memberRepository;

    public Member findById(Long id) {
        return memberRepository.findById(id)
            .orElseThrow(() -> new IllegalArgumentException("Membre introuvable : " + id));
    }

    public List<Member> findAll() {
        return memberRepository.findAllByOrderByFullNameAsc();
    }

    public List<Member> findAllActive() {
        return memberRepository.findAllActive();
    }
}
