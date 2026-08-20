package org.asvosonk.member.application.usecase;

import lombok.RequiredArgsConstructor;
import org.asvosonk.member.domain.model.Member;
import org.asvosonk.member.domain.repository.MemberRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
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

    /**
     * Resolves several member names at once, for list/table screens.
     *
     * <p>Callers used to build these lookup maps with one {@code findById} per
     * row (a classic N+1: a 60-member sanction list issued 60 extra queries and
     * blew up with an exception as soon as one id no longer existed). A single
     * batched query is both faster and tolerant of missing ids — an unresolved
     * id simply renders as a placeholder instead of failing the whole page.
     */
    public Map<Long, String> findNamesByIds(Collection<Long> ids) {
        return memberRepository.findFullNamesByIds(ids);
    }

    public List<Member> search(String keyword) {
        // Échapper les caractères wildcard LIKE pour éviter des résultats inattendus
        String sanitized = keyword
            .replace("%", "\\%")
            .replace("_", "\\_");
        return memberRepository.search(sanitized);
    }
}
