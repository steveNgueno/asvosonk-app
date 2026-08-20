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
import org.asvosonk.session.application.usecase.RequireOpenSessionUseCase;
import org.asvosonk.session.infrastructure.persistence.entity.MeetingSessionEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
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

    private final SanctionRepository        sanctionRepository;
    private final CashboxService            cashboxService;
    private final RequireOpenSessionUseCase requireOpenSession;

    @Transactional
    public Sanction execute(Long sanctionId, AppUser paidBy) {
        // Tout encaissement appartient à une séance : celle du jour où l'argent
        // a été remis. C'est ce rattachement qui le fait figurer dans les
        // entrées du jour et dans le total remis au trésorier.
        MeetingSessionEntity session = requireOpenSession.require("le paiement d'une sanction");

        Sanction sanction = sanctionRepository.findById(sanctionId)
            .orElseThrow(() -> new BusinessRuleException("Sanction introuvable : " + sanctionId));
        if (sanction.getStatus() != SanctionStatus.unpaid) {
            String msg = sanction.getStatus() == SanctionStatus.paid
                ? "Cette sanction a déjà été payée"
                : "Impossible de payer une sanction annulée";
            throw new BusinessRuleException(msg);
        }

        // Seul le reste à payer est encaissé : une partie a pu être retenue sur
        // une tontine antérieure (imputation partielle).
        BigDecimal due = sanction.getRemaining();
        if (due.signum() <= 0) {
            throw new BusinessRuleException("Cette sanction est déjà entièrement réglée.");
        }

        Sanction updated = new Sanction(
            sanction.getId(), sanction.getMemberId(),
            sanction.getSanctionDate(), sanction.getAmount(),
            sanction.getAmount(),               // soldée : montant encaissé = montant dû
            sanction.getReason(), sanction.getOrigin(),
            sanction.getReferenceId(), SanctionStatus.paid,
            LocalDate.now(), sanction.getCancelReason(), sanction.getCreatedAt(),
            java.time.LocalDateTime.now()
        );
        Sanction saved = sanctionRepository.save(updated);

        // Same transaction: cash actually enters the sanction cashbox (F-05).
        cashboxService.credit(CashboxType.sanction, due,
            "Paiement cash sanction #" + saved.getId() + " — " + saved.getReason(),
            MovementOrigin.sanction, session, null, saved.getId(), paidBy);

        return saved;
    }
}
