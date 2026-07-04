package org.asvosonk.sanction.domain.repository;

import org.asvosonk.sanction.domain.model.Sanction;
import org.asvosonk.sanction.domain.valueobject.SanctionStatus;

import java.util.List;
import java.util.Optional;

public interface SanctionRepository {

    Optional<Sanction> findById(Long id);

    List<Sanction> findByMemberIdOrderBySanctionDateDesc(Long memberId);

    List<Sanction> findByStatusOrderBySanctionDateDesc(SanctionStatus status);

    List<Sanction> findByMemberIdAndStatusOrderBySanctionDateDesc(Long memberId, SanctionStatus status);

    Sanction save(Sanction sanction);
}
