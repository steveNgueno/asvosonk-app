package org.asvosonk.sanction.application.usecase;

import lombok.RequiredArgsConstructor;
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
    public Sanction execute(Long sanctionId) {
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
            sanction.getPaymentDate(), sanction.getCreatedAt(),
            LocalDateTime.now()
        );
        return sanctionRepository.save(updated);
    }
}
