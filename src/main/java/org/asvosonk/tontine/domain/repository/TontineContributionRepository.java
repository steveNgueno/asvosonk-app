package org.asvosonk.tontine.domain.repository;

import org.asvosonk.tontine.domain.model.TontineContribution;

import java.util.List;
import java.util.Optional;

public interface TontineContributionRepository {

    TontineContribution save(TontineContribution contribution);

    Optional<TontineContribution> findById(Long id);

    List<TontineContribution> findByTourIdOrderByCreatedAtDesc(Long tourId);

    List<TontineContribution> findByTourIdAndSessionId(Long tourId, Long sessionId);

    /** Toutes les cotisations saisies pendant une séance, tous tours confondus. */
    List<TontineContribution> findBySessionId(Long sessionId);

    List<TontineContribution> findByContributorIdAndTourId(Long contributorId, Long tourId);
}
