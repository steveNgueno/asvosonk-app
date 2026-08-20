package org.asvosonk.member.domain.repository;

import org.asvosonk.member.domain.model.Member;
import org.asvosonk.member.domain.valueobject.MemberStatus;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public interface MemberRepository {

    Optional<Member> findById(Long id);

    /**
     * Full names of the given members, keyed by id, in a single query.
     * Ids with no matching member are simply absent from the result.
     */
    Map<Long, String> findFullNamesByIds(Collection<Long> ids);

    List<Member> findAllByOrderByFullNameAsc();

    List<Member> findByStatusOrderByFullNameAsc(MemberStatus status);

    List<Member> findAllActive();

    List<Member> search(String keyword);

    boolean existsByFullNameIgnoreCase(String fullName);

    Member save(Member member);

    void delete(Member member);
}
