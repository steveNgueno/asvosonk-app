package org.asvosonk.aid.domain.repository;

import org.asvosonk.aid.domain.model.AidContribution;
import org.asvosonk.aid.domain.valueobject.AidContributionStatus;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

public interface AidContributionRepository {

    Optional<AidContribution> findById(Long id);

    List<AidContribution> findByAidId(Long aidId);

    List<AidContribution> findByMemberId(Long memberId);

    Optional<AidContribution> findByAidIdAndMemberId(Long aidId, Long memberId);

    List<AidContribution> findByAidIdAndStatus(Long aidId, AidContributionStatus status);

    /** Parts encore dues d'un membre, toutes aides confondues. */
    List<AidContribution> findOwedByMemberId(Long memberId);

    /** Total recouvré pendant une séance (entrées du jour, remis au trésorier). */
    BigDecimal totalPaidBySessionId(Long sessionId);

    AidContribution save(AidContribution contribution);
}
