package org.asvosonk.sanction.domain.repository;

import org.asvosonk.sanction.domain.model.Sanction;
import org.asvosonk.sanction.domain.valueobject.SanctionOrigin;
import org.asvosonk.sanction.domain.valueobject.SanctionStatus;

import java.util.List;
import java.util.Optional;

public interface SanctionRepository {

    Optional<Sanction> findById(Long id);

    List<Sanction> findAll();

    List<Sanction> findByMemberIdOrderBySanctionDateDesc(Long memberId);

    List<Sanction> findByStatusOrderBySanctionDateDesc(SanctionStatus status);

    List<Sanction> findByMemberIdAndStatusOrderBySanctionDateDesc(Long memberId, SanctionStatus status);

    Optional<Sanction> findByOriginAndReferenceId(SanctionOrigin origin, Long referenceId);

    void deleteById(Long id);

    Sanction save(Sanction sanction);
}
