package org.asvosonk.tontine.infrastructure.persistence.repository;

import lombok.RequiredArgsConstructor;
import org.asvosonk.tontine.domain.model.TontineContribution;
import org.asvosonk.tontine.domain.repository.TontineContributionRepository;
import org.asvosonk.tontine.infrastructure.persistence.entity.TontineContributionEntity;
import org.asvosonk.tontine.infrastructure.persistence.mapper.TontineContributionMapper;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Primary
@Repository
@RequiredArgsConstructor
public class JpaTontineContributionRepository implements TontineContributionRepository {

    private final SpringDataTontineContributionRepository springData;
    private static final TontineContributionMapper MAPPER = TontineContributionMapper.INSTANCE;

    @Override
    public TontineContribution save(TontineContribution contribution) {
        TontineContributionEntity entity = MAPPER.toEntity(contribution);
        if (contribution.getId() != null) {
            entity.setId(contribution.getId());
        }
        TontineContributionEntity saved = springData.save(entity);
        return MAPPER.toDomain(saved);
    }

    @Override
    public Optional<TontineContribution> findById(Long id) {
        return springData.findById(id).map(MAPPER::toDomain);
    }

    @Override
    public List<TontineContribution> findByTourIdOrderByCreatedAtDesc(Long tourId) {
        return springData.findByTourIdOrderByCreatedAtDesc(tourId).stream()
            .map(MAPPER::toDomain)
            .collect(Collectors.toList());
    }

    @Override
    public List<TontineContribution> findByTourIdAndSessionId(Long tourId, Long sessionId) {
        return springData.findByTourIdAndSessionId(tourId, sessionId).stream()
            .map(MAPPER::toDomain)
            .collect(Collectors.toList());
    }

    @Override
    public List<TontineContribution> findBySessionId(Long sessionId) {
        return springData.findBySessionIdOrderByIdAsc(sessionId).stream()
            .map(MAPPER::toDomain)
            .collect(Collectors.toList());
    }

    @Override
    public List<TontineContribution> findByContributorIdAndTourId(Long contributorId, Long tourId) {
        return springData.findByContributorIdAndTourId(contributorId, tourId).stream()
            .map(MAPPER::toDomain)
            .collect(Collectors.toList());
    }
}
