package org.asvosonk.sanction.application.usecase;

import lombok.RequiredArgsConstructor;
import org.asvosonk.sanction.domain.valueobject.SanctionStatus;
import org.asvosonk.sanction.infrastructure.persistence.entity.SanctionEntity;
import jakarta.persistence.EntityManager;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Use case: cancel a sanction without cashbox movement.
 * Only the PRESIDENT can cancel a sanction.
 */
@Service
@RequiredArgsConstructor
public class CancelSanctionUseCase {

    private final EntityManager entityManager;

    @Transactional
    public SanctionEntity execute(Long sanctionId) {
        SanctionEntity sanction = entityManager.find(SanctionEntity.class, sanctionId);
        if (sanction == null) {
            throw new IllegalArgumentException("Sanction introuvable : " + sanctionId);
        }
        if (sanction.getStatus() == SanctionStatus.paid) {
            throw new IllegalStateException("Impossible d'annuler une sanction déjà payée");
        }
        sanction.setStatus(SanctionStatus.cancelled);
        entityManager.merge(sanction);
        return sanction;
    }
}
