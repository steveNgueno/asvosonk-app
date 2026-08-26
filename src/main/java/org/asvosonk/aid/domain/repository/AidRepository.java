package org.asvosonk.aid.domain.repository;

import org.asvosonk.aid.domain.model.Aid;
import org.asvosonk.aid.domain.valueobject.AidStatus;
import org.asvosonk.aid.domain.valueobject.AidType;

import java.util.List;
import java.util.Optional;

public interface AidRepository {

    Optional<Aid> findById(Long id);

    List<Aid> findAll();

    List<Aid> findByStatus(AidStatus status);

    List<Aid> findByBeneficiaryId(Long beneficiaryId);

    List<Aid> findByType(AidType type);

    List<Aid> findByBeneficiaryIdAndStatus(Long beneficiaryId, AidStatus status);

    /** Aides encore d'actualité (au moins une part à recouvrir). */
    List<Aid> findCurrentAids();

    Aid save(Aid aid);
}
