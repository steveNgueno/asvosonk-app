package org.asvosonk.sanction.infrastructure.persistence.repository;

import lombok.RequiredArgsConstructor;
import org.asvosonk.member.infrastructure.persistence.entity.MemberEntity;
import org.asvosonk.member.infrastructure.persistence.repository.SpringDataMemberRepository;
import org.asvosonk.sanction.domain.model.Sanction;
import org.asvosonk.sanction.domain.repository.SanctionRepository;
import org.asvosonk.sanction.domain.valueobject.SanctionOrigin;
import org.asvosonk.sanction.domain.valueobject.SanctionStatus;
import org.asvosonk.sanction.infrastructure.persistence.entity.SanctionEntity;
import org.asvosonk.sanction.infrastructure.persistence.mapper.SanctionMapper;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Primary
@Repository
@RequiredArgsConstructor
public class JpaSanctionRepository implements SanctionRepository {

    private final SanctionJpaRepository springData;
    private final SpringDataMemberRepository memberSpringData;

    @Override
    public Optional<Sanction> findById(Long id) {
        return springData.findById(id).map(SanctionMapper::toDomain);
    }

    @Override
    public List<Sanction> findAll() {
        return springData.findAll().stream()
            .map(SanctionMapper::toDomain)
            .collect(Collectors.toList());
    }

    @Override
    public List<Sanction> findByMemberIdOrderBySanctionDateDesc(Long memberId) {
        return springData.findByMemberIdOrderBySanctionDateDesc(memberId).stream()
            .map(SanctionMapper::toDomain)
            .collect(Collectors.toList());
    }

    @Override
    public List<Sanction> findByStatusOrderBySanctionDateDesc(SanctionStatus status) {
        return springData.findByStatusOrderBySanctionDateDesc(status).stream()
            .map(SanctionMapper::toDomain)
            .collect(Collectors.toList());
    }

    @Override
    public List<Sanction> findByMemberIdAndStatusOrderBySanctionDateDesc(Long memberId, SanctionStatus status) {
        return springData.findByMemberIdAndStatusOrderBySanctionDateDesc(memberId, status).stream()
            .map(SanctionMapper::toDomain)
            .collect(Collectors.toList());
    }

    @Override
    public Optional<Sanction> findByOriginAndReferenceId(SanctionOrigin origin, Long referenceId) {
        return springData.findByOriginAndReferenceId(origin, referenceId)
            .map(SanctionMapper::toDomain);
    }

    @Override
    public void deleteById(Long id) {
        springData.deleteById(id);
    }

    @Override
    public Sanction save(Sanction sanction) {
        SanctionEntity entity = SanctionMapper.toEntity(sanction);
        entity.setMember(memberSpringData.getReferenceById(sanction.getMemberId()));
        return SanctionMapper.toDomain(springData.save(entity));
    }
}
