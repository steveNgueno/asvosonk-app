package org.asvosonk.sanction.application.usecase;

import lombok.RequiredArgsConstructor;
import org.asvosonk.cashbox.application.service.CashboxService;
import org.asvosonk.cashbox.domain.valueobject.CashboxType;
import org.asvosonk.cashbox.domain.valueobject.MovementOrigin;
import org.asvosonk.common.domain.exception.BusinessRuleException;
import org.asvosonk.sanction.domain.model.Sanction;
import org.asvosonk.sanction.domain.repository.SanctionRepository;
import org.asvosonk.sanction.domain.valueobject.SanctionStatus;
import org.asvosonk.security.domain.model.AppUser;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

/**
 * Use case: mark a sanction as paid AND credit the sanction cashbox, atomically.
 *
 * <p>F-05: the status change and the cashbox credit must live in the SAME
 * transaction. Previously the controller committed the status change first,
 * then credited the cashbox in a separate transaction — if the credit failed,
 * the sanction stayed "paid" with no money recorded. Folding the credit here
 * (CashboxService.record propagates MANDATORY, so it joins this transaction)
 * makes both succeed or both roll back together.
 */
@Service
@RequiredArgsConstructor
public class PaySanctionUseCase {

    private final SanctionRepository sanctionRepository;
    private final CashboxService     cashboxService;

    @Transactional
    public Sanction execute(Long sanctionId, AppUser paidBy) {
        Sanction sanction = sanctionRepository.findById(sanctionId)
            .orElseThrow(() -> new BusinessRuleException("Sanction introuvable : " + sanctionId));
        if (sanction.getStatus() != SanctionStatus.unpaid) {
            String msg = sanction.getStatus() == SanctionStatus.paid
                ? "Cette sanction a déjà été payée"
                : "Impossible de payer une sanction annulée";
            throw new BusinessRuleException(msg);
        }

        // Mark as paid.
        Sanction updated = new Sanction(
            sanction.getId(), sanction.getMemberId(),
            sanction.getSanctionDate(), sanction.getAmount(),
            sanction.getReason(), sanction.getOrigin(),
            sanction.getReferenceId(), SanctionStatus.paid,
            LocalDate.now(), sanction.getCreatedAt(),
            java.time.LocalDateTime.now()
        );
        Sanction saved = sanctionRepository.save(updated);

        // Same transaction: cash actually enters the sanction cashbox (F-05).
        cashboxService.credit(CashboxType.sanction, saved.getAmount(),
            "Paiement cash sanction #" + saved.getId() + " — " + saved.getReason(),
            MovementOrigin.sanction, null, null, saved.getId(), paidBy);

        return saved;
    }
}
