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
    public java.util.Map<Long, String> findFullNamesByIds(java.util.Collection<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return java.util.Map.of();
        }
        java.util.Map<Long, String> names = new java.util.HashMap<>();
        for (Object[] row : springData.findIdAndFullNameByIds(new java.util.HashSet<>(ids))) {
            names.put((Long) row[0], (String) row[1]);
        }
        return names;
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
    public List<Member> search(String keyword) {
        return springData.searchByKeyword(keyword).stream()
            .map(MemberEntityMapper::toDomain)
            .collect(Collectors.toList());
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

}
