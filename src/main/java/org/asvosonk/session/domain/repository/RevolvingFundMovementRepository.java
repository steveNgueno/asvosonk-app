package org.asvosonk.session.domain.repository;

import org.asvosonk.session.domain.model.RevolvingFundMovement;

import java.util.List;

public interface RevolvingFundMovementRepository {

    List<RevolvingFundMovement> findPendingAdvances(Long memberId);

    List<RevolvingFundMovement> findByFundMemberIdOrderByCreatedAtDesc(Long memberId);

    RevolvingFundMovement save(RevolvingFundMovement movement);
}
