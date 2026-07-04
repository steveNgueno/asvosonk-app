package org.asvosonk.tontine.infrastructure.persistence.repository;

import lombok.RequiredArgsConstructor;
import org.asvosonk.tontine.domain.model.TontineParticipant;
import org.asvosonk.tontine.domain.repository.TontineParticipantRepository;
import org.asvosonk.tontine.infrastructure.persistence.entity.TontineParticipantEntity;
import org.asvosonk.tontine.infrastructure.persistence.mapper.TontineParticipantMapper;
import org.springframework.context.annotation.Primary;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Primary
@Repository
@RequiredArgsConstructor
public class JpaTontineParticipantRepository implements TontineParticipantRepository {

    private final SpringDataTontineParticipantRepository springData;
    private static final TontineParticipantMapper MAPPER = TontineParticipantMapper.INSTANCE;

    @Override
    public TontineParticipant save(TontineParticipant participant) {
        TontineParticipantEntity entity = MAPPER.toEntity(participant);
        if (participant.getId() != null) {
            entity.setId(participant.getId());
        }
        TontineParticipantEntity saved = springData.save(entity);
        return MAPPER.toDomain(saved);
    }

    @Override
    public List<TontineParticipant> findByTourId(Long tourId) {
        return springData.findByTourIdOrderByDrawOrder(tourId).stream()
            .map(MAPPER::toDomain)
            .collect(Collectors.toList());
    }

    @Override
    public List<TontineParticipant> findByTourIdOrderByDrawOrder(Long tourId) {
        return springData.findByTourIdOrderByDrawOrder(tourId).stream()
            .map(MAPPER::toDomain)
            .collect(Collectors.toList());
    }

    @Override
    public Optional<TontineParticipant> findByTourIdAndMemberId(Long tourId, Long memberId) {
        return springData.findByTourIdAndMemberId(tourId, memberId).map(MAPPER::toDomain);
    }

    @org.springframework.stereotype.Repository
    interface SpringDataTontineParticipantRepository extends JpaRepository<TontineParticipantEntity, Long> {
        List<TontineParticipantEntity> findByTourIdOrderByDrawOrder(Long tourId);
        Optional<TontineParticipantEntity> findByTourIdAndMemberId(Long tourId, Long memberId);
    }
}
