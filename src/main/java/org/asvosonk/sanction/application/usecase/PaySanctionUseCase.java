package org.asvosonk.sanction.application.usecase;

import lombok.RequiredArgsConstructor;
import org.asvosonk.sanction.domain.model.Sanction;
import org.asvosonk.sanction.domain.repository.SanctionRepository;
import org.asvosonk.sanction.domain.valueobject.SanctionStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

/**
 * Use case: mark a sanction as paid.
 */
@Service
@RequiredArgsConstructor
public class PaySanctionUseCase {

    private final SanctionRepository sanctionRepository;

    @Transactional
    public Sanction execute(Long sanctionId) {
        Sanction sanction = sanctionRepository.findById(sanctionId)
            .orElseThrow(() -> new IllegalArgumentException("Sanction introuvable : " + sanctionId));
        if (sanction.getStatus() != SanctionStatus.unpaid) {
            String msg = sanction.getStatus() == SanctionStatus.paid
                ? "Cette sanction a déjà été payée"
                : "Impossible de payer une sanction annulée";
            throw new IllegalStateException(msg);
        }
        // Create updated sanction with paid status
        Sanction updated = new Sanction(
            sanction.getId(), sanction.getMemberId(),
            sanction.getSanctionDate(), sanction.getAmount(),
            sanction.getReason(), sanction.getOrigin(),
            sanction.getReferenceId(), SanctionStatus.paid,
            LocalDate.now(), sanction.getCreatedAt(),
            java.time.LocalDateTime.now()
        );
        return sanctionRepository.save(updated);
    }
}
