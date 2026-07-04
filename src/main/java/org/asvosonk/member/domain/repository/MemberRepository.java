package org.asvosonk.member.domain.repository;

import org.asvosonk.member.domain.model.Member;
import org.asvosonk.member.domain.valueobject.MemberStatus;

import java.util.List;
import java.util.Optional;

public interface MemberRepository {

    Optional<Member> findById(Long id);

    List<Member> findAllByOrderByFullNameAsc();

    List<Member> findByStatusOrderByFullNameAsc(MemberStatus status);

    List<Member> findAllActive();

    boolean existsByFullNameIgnoreCase(String fullName);

    Member save(Member member);

    void delete(Member member);
}
