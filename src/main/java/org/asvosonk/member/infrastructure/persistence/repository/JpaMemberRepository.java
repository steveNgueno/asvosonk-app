package org.asvosonk.member.infrastructure.persistence.repository;

import lombok.RequiredArgsConstructor;
import org.asvosonk.member.domain.model.Member;
import org.asvosonk.member.domain.repository.MemberRepository;
import org.asvosonk.member.domain.valueobject.MemberStatus;
import org.asvosonk.member.infrastructure.persistence.entity.MemberEntity;
import org.asvosonk.member.infrastructure.persistence.mapper.MemberEntityMapper;
import org.springframework.context.annotation.Primary;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Primary
@Repository
@RequiredArgsConstructor
public class JpaMemberRepository implements MemberRepository {

    private final SpringDataMemberRepository springData;

    @Override
    public Optional<Member> findById(Long id) {
        return springData.findById(id).map(MemberEntityMapper::toDomain);
    }

    @Override
    public List<Member> findAllByOrderByFullNameAsc() {
        return springData.findAllByOrderByFullNameAsc().stream()
            .map(MemberEntityMapper::toDomain)
            .collect(Collectors.toList());
    }

    @Override
    public List<Member> findByStatusOrderByFullNameAsc(MemberStatus status) {
        return springData.findByStatusOrderByFullNameAsc(status).stream()
            .map(MemberEntityMapper::toDomain)
            .collect(Collectors.toList());
    }

    @Override
    public List<Member> findAllActive() {
        return springData.findAllActive().stream()
            .map(MemberEntityMapper::toDomain)
            .collect(Collectors.toList());
    }

    @Override
    public boolean existsByFullNameIgnoreCase(String fullName) {
        return springData.existsByFullNameIgnoreCase(fullName);
    }

    @Override
    public Member save(Member member) {
        MemberEntity entity = MemberEntityMapper.toEntity(member);
        return MemberEntityMapper.toDomain(springData.save(entity));
    }

    @Override
    public void delete(Member member) {
        springData.deleteById(member.getId());
    }

    @org.springframework.stereotype.Repository
    interface SpringDataMemberRepository extends JpaRepository<MemberEntity, Long> {
        List<MemberEntity> findByStatusOrderByFullNameAsc(MemberStatus status);
        List<MemberEntity> findAllByOrderByFullNameAsc();

        @Query("SELECT m FROM MemberEntity m WHERE m.status = 'active' ORDER BY m.fullName ASC")
        List<MemberEntity> findAllActive();

        boolean existsByFullNameIgnoreCase(String fullName);
    }
}
