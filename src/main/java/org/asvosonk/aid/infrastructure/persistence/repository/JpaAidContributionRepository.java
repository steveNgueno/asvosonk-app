package org.asvosonk.aid.infrastructure.persistence.repository;

import lombok.RequiredArgsConstructor;
import org.asvosonk.aid.domain.model.AidContribution;
import org.asvosonk.aid.domain.repository.AidContributionRepository;
import org.asvosonk.aid.domain.valueobject.AidContributionStatus;
import org.asvosonk.aid.infrastructure.persistence.entity.AidContributionEntity;
import org.asvosonk.aid.infrastructure.persistence.mapper.AidContributionMapper;
import org.asvosonk.member.infrastructure.persistence.entity.MemberEntity;
import org.asvosonk.member.infrastructure.persistence.repository.SpringDataMemberRepository;
import org.asvosonk.session.infrastructure.persistence.repository.SpringDataMeetingSessionRepository;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

@Primary
@Repository
@RequiredArgsConstructor
public class JpaAidContributionRepository implements AidContributionRepository {

    private final SpringDataAidContributionRepository springData;
    private final SpringDataMemberRepository          memberSpringData;
    private final SpringDataAidRepository             aidSpringData;
    private final SpringDataMeetingSessionRepository  sessionSpringData;

    @Override
    public Optional<AidContribution> findById(Long id) {
        return springData.findById(id).map(AidContributionMapper::toDomain);
    }

    @Override
    public List<AidContribution> findByAidId(Long aidId) {
        return springData.findByAidIdOrderByMemberFullNameAsc(aidId).stream()
            .map(AidContributionMapper::toDomain)
            .toList();
    }

    @Override
    public List<AidContribution> findByMemberId(Long memberId) {
        return springData.findByMemberIdOrderByCreatedAtDesc(memberId).stream()
            .map(AidContributionMapper::toDomain)
            .toList();
    }

    @Override
    public Optional<AidContribution> findByAidIdAndMemberId(Long aidId, Long memberId) {
        return springData.findByAidIdAndMemberId(aidId, memberId)
            .map(AidContributionMapper::toDomain);
    }

    @Override
    public List<AidContribution> findByAidIdAndStatus(Long aidId, AidContributionStatus status) {
        return springData.findByAidIdAndStatus(aidId, status).stream()
            .map(AidContributionMapper::toDomain)
            .toList();
    }

    @Override
    public List<AidContribution> findOwedByMemberId(Long memberId) {
        return springData
            .findByMemberIdAndStatusOrderByCreatedAtAsc(memberId, AidContributionStatus.owed)
            .stream().map(AidContributionMapper::toDomain).toList();
    }

    @Override
    public BigDecimal totalPaidBySessionId(Long sessionId) {
        return springData.totalPaidBySessionId(sessionId);
    }

    @Override
    public AidContribution save(AidContribution contribution) {
        AidContributionEntity entity = AidContributionMapper.toEntity(contribution);
        if (entity.getAid() == null) {
            entity.setAid(aidSpringData.getReferenceById(contribution.getAidId()));
        }
        if (entity.getMember() == null) {
            MemberEntity member = memberSpringData.getReferenceById(contribution.getMemberId());
            entity.setMember(member);
        }
        if (entity.getSession() == null && contribution.getSessionId() != null) {
            entity.setSession(sessionSpringData.getReferenceById(contribution.getSessionId()));
        }
        return AidContributionMapper.toDomain(springData.save(entity));
    }
}
