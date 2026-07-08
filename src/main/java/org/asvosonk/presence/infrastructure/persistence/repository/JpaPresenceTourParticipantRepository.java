package org.asvosonk.presence.infrastructure.persistence.repository;

import lombok.RequiredArgsConstructor;
import org.asvosonk.presence.domain.model.PresenceTourParticipant;
import org.asvosonk.presence.domain.repository.PresenceTourParticipantRepository;
import org.asvosonk.presence.infrastructure.persistence.entity.PresenceTourParticipantEntity;
import org.asvosonk.presence.infrastructure.persistence.mapper.PresenceTourParticipantMapper;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Primary
@Repository
@RequiredArgsConstructor
public class JpaPresenceTourParticipantRepository implements PresenceTourParticipantRepository {

    private final SpringDataPresenceTourParticipantRepository springData;
    private static final PresenceTourParticipantMapper MAPPER = PresenceTourParticipantMapper.INSTANCE;

    @Override
    public PresenceTourParticipant save(PresenceTourParticipant participant) {
        PresenceTourParticipantEntity entity = MAPPER.toEntity(participant);
        if (participant.getId() != null) {
            entity.setId(participant.getId());
        }
        PresenceTourParticipantEntity saved = springData.save(entity);
        return MAPPER.toDomain(saved);
    }

    @Override
    public List<PresenceTourParticipant> findByTourId(Long tourId) {
        return springData.findByTourIdOrderByDrawOrder(tourId).stream()
            .map(MAPPER::toDomain)
            .collect(Collectors.toList());
    }

    @Override
    public List<PresenceTourParticipant> findByTourIdOrderByDrawOrder(Long tourId) {
        return springData.findByTourIdOrderByDrawOrder(tourId).stream()
            .map(MAPPER::toDomain)
            .collect(Collectors.toList());
    }

    @Override
    public Optional<PresenceTourParticipant> findByTourIdAndMemberId(Long tourId, Long memberId) {
        return springData.findByTourIdAndMemberId(tourId, memberId).map(MAPPER::toDomain);
    }

    @Override
    public Optional<PresenceTourParticipant> findNextBeneficiary(Long tourId) {
        return springData.findNextNotBenefited(tourId).map(MAPPER::toDomain);
    }

    @Override
    public long countByTourIdAndHasBenefited(Long tourId, boolean hasBenefited) {
        return springData.countByTourIdAndHasBenefited(tourId, hasBenefited);
    }
}
