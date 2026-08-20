package org.asvosonk.member.infrastructure.persistence.repository;

import org.asvosonk.member.domain.valueobject.MemberStatus;
import org.asvosonk.member.infrastructure.persistence.entity.MemberEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface SpringDataMemberRepository extends JpaRepository<MemberEntity, Long> {
    List<MemberEntity> findByStatusOrderByFullNameAsc(MemberStatus status);
    List<MemberEntity> findAllByOrderByFullNameAsc();

    @Query("SELECT m FROM MemberEntity m WHERE m.status = 'active' ORDER BY m.fullName ASC")
    List<MemberEntity> findAllActive();

    @Query("SELECT m FROM MemberEntity m WHERE LOWER(m.fullName) LIKE LOWER(CONCAT('%', :keyword, '%')) OR LOWER(m.phone) LIKE LOWER(CONCAT('%', :keyword, '%')) ORDER BY m.fullName ASC")
    List<MemberEntity> searchByKeyword(String keyword);

    /**
     * Projection used to resolve a batch of member names in one round-trip,
     * instead of one findById per row while rendering a list.
     */
    @Query("SELECT m.id, m.fullName FROM MemberEntity m WHERE m.id IN :ids")
    List<Object[]> findIdAndFullNameByIds(java.util.Collection<Long> ids);

    boolean existsByFullNameIgnoreCase(String fullName);
}
