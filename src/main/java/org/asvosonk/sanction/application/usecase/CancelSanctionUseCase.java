package org.asvosonk.sanction.application.usecase;

import lombok.RequiredArgsConstructor;
import org.asvosonk.common.domain.exception.BusinessRuleException;
import org.asvosonk.sanction.domain.model.Sanction;
import org.asvosonk.sanction.domain.repository.SanctionRepository;
import org.asvosonk.sanction.domain.valueobject.SanctionStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * Use case: cancel a sanction without cashbox movement.
 * Only the PRESIDENT can cancel a sanction.
 */
@Service
@RequiredArgsConstructor
public class CancelSanctionUseCase {

    private final SanctionRepository sanctionRepository;

    @Transactional
    public Sanction execute(Long sanctionId, String cancelReason) {
        // F-39 — a cancellation reason is mandatory and kept on the record for
        // audit purposes (a sanction disappearing from the "unpaid" list with no
        // trace of why was the original complaint).
        if (cancelReason == null || cancelReason.isBlank()) {
            throw new BusinessRuleException("Le motif d'annulation est obligatoire.");
        }
        Sanction sanction = sanctionRepository.findById(sanctionId)
            .orElseThrow(() -> new IllegalArgumentException("Sanction introuvable : " + sanctionId));
        if (sanction.getStatus() == SanctionStatus.paid) {
            throw new IllegalStateException("Impossible d'annuler une sanction déjà payée");
        }
        Sanction updated = new Sanction(
            sanction.getId(), sanction.getMemberId(),
            sanction.getSanctionDate(), sanction.getAmount(),
            sanction.getReason(), sanction.getOrigin(),
            sanction.getReferenceId(), SanctionStatus.cancelled,
            sanction.getPaymentDate(), cancelReason.trim(), sanction.getCreatedAt(),
            LocalDateTime.now()
        );
        return sanctionRepository.save(updated);
    }
}
