package org.asvosonk.aid.infrastructure.persistence.repository;

import lombok.RequiredArgsConstructor;
import org.asvosonk.aid.domain.model.Aid;
import org.asvosonk.aid.domain.repository.AidRepository;
import org.asvosonk.aid.domain.valueobject.AidStatus;
import org.asvosonk.aid.domain.valueobject.AidType;
import org.asvosonk.aid.infrastructure.persistence.entity.AidEntity;
import org.asvosonk.aid.infrastructure.persistence.mapper.AidMapper;
import org.asvosonk.member.infrastructure.persistence.entity.MemberEntity;
import org.asvosonk.member.infrastructure.persistence.repository.SpringDataMemberRepository;
import org.asvosonk.session.infrastructure.persistence.repository.SpringDataMeetingSessionRepository;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Primary
@Repository
@RequiredArgsConstructor
public class JpaAidRepository implements AidRepository {

    private final SpringDataAidRepository springData;
    private final SpringDataMemberRepository memberSpringData;
    private final SpringDataMeetingSessionRepository sessionSpringData;

    @Override
    public Optional<Aid> findById(Long id) {
        return springData.findById(id).map(AidMapper::toDomain);
    }

    @Override
    public List<Aid> findAll() {
        return springData.findAllByOrderByAidDateDescIdDesc().stream()
            .map(AidMapper::toDomain)
            .toList();
    }

    @Override
    public List<Aid> findByStatus(AidStatus status) {
        return springData.findByStatusOrderByAidDateDescIdDesc(status).stream()
            .map(AidMapper::toDomain)
            .toList();
    }

    @Override
    public List<Aid> findByBeneficiaryId(Long beneficiaryId) {
        return springData.findByBeneficiaryIdOrderByAidDateDesc(beneficiaryId).stream()
            .map(AidMapper::toDomain)
            .toList();
    }

    @Override
    public List<Aid> findByType(AidType type) {
        return springData.findByTypeOrderByAidDateDesc(type).stream()
            .map(AidMapper::toDomain)
            .toList();
    }

    @Override
    public List<Aid> findByBeneficiaryIdAndStatus(Long beneficiaryId, AidStatus status) {
        return springData.findByBeneficiaryIdAndStatusOrderByAidDateDesc(beneficiaryId, status)
            .stream().map(AidMapper::toDomain).toList();
    }

    @Override
    public List<Aid> findCurrentAids() {
        return springData.findByStatusOrderByAidDateAsc(AidStatus.in_progress).stream()
            .map(AidMapper::toDomain)
            .toList();
    }

    @Override
    public Aid save(Aid aid) {
        AidEntity entity = AidMapper.toEntity(aid);
        entity.setBeneficiary(memberSpringData.getReferenceById(aid.getBeneficiaryId()));
        if (aid.getSessionId() != null) {
            entity.setSession(sessionSpringData.getReferenceById(aid.getSessionId()));
        }
        return AidMapper.toDomain(springData.save(entity));
    }
}
