package org.asvosonk.sanction.application.usecase;

import lombok.RequiredArgsConstructor;
import org.asvosonk.sanction.domain.valueobject.SanctionStatus;
import org.asvosonk.sanction.infrastructure.persistence.entity.SanctionEntity;
import jakarta.persistence.EntityManager;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

/**
 * Use case: mark a sanction as paid.
 */
@Service
@RequiredArgsConstructor
public class PaySanctionUseCase {

    private final EntityManager entityManager;

    @Transactional
    public SanctionEntity execute(Long sanctionId) {
        SanctionEntity sanction = entityManager.find(SanctionEntity.class, sanctionId);
        if (sanction == null) {
            throw new IllegalArgumentException("Sanction introuvable : " + sanctionId);
        }
        if (sanction.getStatus() != SanctionStatus.unpaid) {
            String msg = sanction.getStatus() == SanctionStatus.paid
                ? "Cette sanction a déjà été payée"
                : "Impossible de payer une sanction annulée";
            throw new IllegalStateException(msg);
        }
        sanction.setStatus(SanctionStatus.paid);
        sanction.setPaymentDate(LocalDate.now());
        entityManager.merge(sanction);
        return sanction;
    }
}
