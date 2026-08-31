package org.asvosonk.tontine.application.usecase;

import lombok.RequiredArgsConstructor;
import org.asvosonk.common.domain.exception.BusinessRuleException;
import org.asvosonk.common.domain.exception.ResourceNotFoundException;
import org.asvosonk.sanction.application.usecase.CreateSanctionUseCase;
import org.asvosonk.sanction.domain.repository.SanctionRepository;
import org.asvosonk.sanction.domain.valueobject.SanctionOrigin;
import org.asvosonk.tontine.domain.model.TontineContribution;
import org.asvosonk.tontine.domain.model.TontineDebt;
import org.asvosonk.tontine.domain.model.TontineParticipant;
import org.asvosonk.tontine.domain.model.TontineTour;
import org.asvosonk.tontine.domain.repository.TontineContributionRepository;
import org.asvosonk.tontine.domain.repository.TontineDebtRepository;
import org.asvosonk.tontine.domain.repository.TontineParticipantRepository;
import org.asvosonk.tontine.domain.repository.TontineTourRepository;
import org.asvosonk.tontine.domain.valueobject.DebtStatus;
import org.asvosonk.tontine.domain.valueobject.PaymentStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class RecordTontineContributionUseCase {

    private static final BigDecimal MIN_CONTRIBUTION = new BigDecimal("5000");
    private static final BigDecimal DEFAULT_FINE_NOT_BENEFITED = new BigDecimal("2000");
    private static final BigDecimal DEFAULT_FINE_BENEFITED = new BigDecimal("5000");

    private final TontineTourRepository tourRepository;
    private final TontineParticipantRepository participantRepository;
    private final TontineContributionRepository contributionRepository;
    private final TontineDebtRepository debtRepository;
    private final SanctionRepository sanctionRepository;
    private final CreateSanctionUseCase createSanctionUseCase;

    /**
     * Record a grand tontine contribution.
     *
     * @param tourId        the active tour ID
     * @param sessionId     the session ID (meeting session)
     * @param contributorId the member contributing
     * @param beneficiaryId the member benefiting this session
     * @param amount        contribution amount (0 = default, otherwise multiple of 5000)
     * @return the recorded contribution
     */
    @Transactional
    public TontineContribution execute(Long tourId, Long sessionId, Long contributorId,
                                       Long beneficiaryId, BigDecimal amount) {
        // ── Preconditions ────────────────────────────────────
        // F-34 — locked so two concurrent submissions for this tour can't both
        // read the same "next" draw order in handleDefault's reassignment.
        TontineTour tour = tourRepository.findByIdForUpdate(tourId)
            .orElseThrow(() -> new ResourceNotFoundException("Tour de grande tontine", tourId));

        if (!tour.isOpen()) {
            throw new BusinessRuleException("Le tour de grande tontine est clôturé.");
        }

        // Le bénéficiaire du jour cotise lui aussi à sa propre tontine : son
        // versement grossit la somme qu'il perçoit, mais ne crée évidemment
        // aucune dette (personne ne la lui doit en retour) — voir plus bas.

        // Both must be participants
        TontineParticipant contributor = participantRepository
            .findByTourIdAndMemberId(tourId, contributorId)
            .orElseThrow(() -> new BusinessRuleException(
                "Le cotisant n'est pas participant de ce tour."));

        TontineParticipant beneficiary = participantRepository
            .findByTourIdAndMemberId(tourId, beneficiaryId)
            .orElseThrow(() -> new BusinessRuleException(
                "Le bénéficiaire n'est pas participant de ce tour."));

        // Amount validation
        if (amount.compareTo(BigDecimal.ZERO) < 0) {
            throw new BusinessRuleException("Le montant ne peut pas être négatif.");
        }

        // F-49 : idempotence anti double-clic. La table impose UNIQUE(tour, session,
        // contributor, beneficiary) ; sans détection préalable, un second envoi lèverait
        // une violation d'unicité SQL exposée brute à l'utilisateur. On la traduit en
        // message métier clair et on refuse le doublon.
        boolean alreadyRecorded = contributionRepository
            .findByTourIdAndSessionId(tourId, sessionId).stream()
            .anyMatch(c -> contributorId.equals(c.getContributorId())
                        && beneficiaryId.equals(c.getBeneficiaryId()));
        if (alreadyRecorded) {
            throw new BusinessRuleException(
                "Cette cotisation a déjà été enregistrée pour cette séance.");
        }

        // F-07 — Debt orientation invariant:
        //   A debt is stored as debtor = the member who RECEIVED (beneficiary),
        //   creditor = the member who GAVE (contributor).  See Case 3 below,
        //   which creates `new TontineDebt(..., beneficiaryId, contributorId, ...)`.
        //
        // To SETTLE a debt, the current contributor must be its DEBTOR: in a
        // later session they pay back the member who previously funded them.
        //   e.g. S1 benef=A, B pays  → debt (debtor=A, creditor=B)
        //        S2 benef=B, A pays  → settles that debt: debtor=A, creditor=B
        //
        // The lookup therefore keys on (debtor = current contributor,
        // creditor = current beneficiary). The previous code searched the SAME
        // orientation as creation (debtor=beneficiary, creditor=contributor),
        // never matched, and piled up a mirror debt that was never repaid.
        Optional<TontineDebt> existingDebt = debtRepository
            .findByTourIdAndDebtorIdAndCreditorIdAndStatus(
                tourId, contributorId, beneficiaryId, DebtStatus.owed);

        // ── Case 1: Default (amount = 0) ─────────────────────
        if (amount.compareTo(BigDecimal.ZERO) == 0) {
            return handleDefault(tourId, sessionId, contributor, beneficiary);
        }

        // ── Case 2: Repayment (debt exists) ──────────────────
        if (existingDebt.isPresent()) {
            TontineDebt debt = existingDebt.get();
            if (amount.compareTo(debt.getAmount()) != 0) {
                throw new BusinessRuleException(
                    "Vous devez rembourser exactement " + debt.getAmount()
                        + " FCFA (montant de la dette envers " + beneficiaryId + ").");
            }

            // Record the contribution
            TontineContribution contribution = new TontineContribution(
                null, tourId, sessionId, contributorId, beneficiaryId,
                amount, PaymentStatus.paid, LocalDateTime.now());
            TontineContribution saved = contributionRepository.save(contribution);

            // Mark debt as repaid
            debt.markAsRepaid(sessionId);
            debtRepository.save(debt);

            return saved;
        }

        // ── Case 3: Normal contribution (no existing debt) ───
        if (amount.compareTo(MIN_CONTRIBUTION) < 0) {
            throw new BusinessRuleException(
                "La cotisation minimale est de " + MIN_CONTRIBUTION + " FCFA.");
        }
        if (amount.remainder(MIN_CONTRIBUTION).compareTo(BigDecimal.ZERO) != 0) {
            throw new BusinessRuleException(
                "Le montant doit être un multiple de " + MIN_CONTRIBUTION + " FCFA.");
        }

        // Record the contribution
        TontineContribution contribution = new TontineContribution(
            null, tourId, sessionId, contributorId, beneficiaryId,
            amount, PaymentStatus.paid, LocalDateTime.now());
        TontineContribution saved = contributionRepository.save(contribution);

        // Le bénéficiaire cotise pour lui-même : la somme entre dans sa tontine,
        // mais aucune dette n'est créée — il ne se doit rien à lui-même.
        if (!contributorId.equals(beneficiaryId)) {
            TontineDebt debt = new TontineDebt(
                null, tourId, beneficiaryId, contributorId,
                amount, sessionId, DebtStatus.owed, null,
                LocalDateTime.now(), LocalDateTime.now());
            debtRepository.save(debt);
        }

        return saved;
    }

    /**
     * Replace an existing contribution for the same (tour, session, contributor).
     * Deletes the old contribution and reverses its side effects before recording
     * the new one. This allows the sheet to be edited multiple times before closing.
     */
    @Transactional
    public TontineContribution replaceContribution(Long tourId, Long sessionId, Long contributorId,
                                                    Long beneficiaryId, BigDecimal amount) {
        // Find the existing contribution for this (tour, session, contributor)
        Optional<TontineContribution> existing = contributionRepository
            .findByTourIdAndSessionId(tourId, sessionId).stream()
            .filter(c -> contributorId.equals(c.getContributorId()))
            .findFirst();

        if (existing.isPresent()) {
            TontineContribution old = existing.get();

            // Reverse side effects based on the old contribution type
            reverseContributionSideEffects(old, tourId, sessionId);

            // Delete the old contribution
            contributionRepository.deleteById(old.getId());
        }

        // Record the new contribution
        return execute(tourId, sessionId, contributorId, beneficiaryId, amount);
    }

    /**
     * Reverse the side effects of an existing contribution so it can be replaced.
     */
    private void reverseContributionSideEffects(TontineContribution old, Long tourId, Long sessionId) {
        if (old.isDefault()) {
            // Case 1: Default → delete the associated sanction
            sanctionRepository.findByOriginAndReferenceId(SanctionOrigin.tontine_default, old.getId())
                .ifPresent(sanction -> sanctionRepository.deleteById(sanction.getId()));
        }

        if (old.isPaid()) {
            if (!old.getContributorId().equals(old.getBeneficiaryId())) {
                // Case 2 or 3: A debt exists — either was repaid (Case 2) or was created (Case 3).
                // We need to find the specific debt linked to this session and reverse it.
                // Look for debts where this contributor is the debtor (Case 2 — repayment)
                // or the creditor (Case 3 — normal contribution).

                // Try Case 2 first: debt was repaid by this contributor (debtor=contributor)
                Optional<TontineDebt> repaidDebt = debtRepository
                    .findByTourIdAndDebtorIdAndCreditorIdAndStatus(
                        tourId, old.getContributorId(), old.getBeneficiaryId(), DebtStatus.repaid);
                if (repaidDebt.isPresent()) {
                    TontineDebt debt = repaidDebt.get();
                    // Only reverse if it was repaid in this session
                    if (sessionId.equals(debt.getRepaymentSessionId())) {
                        debt.markAsUnrepaid();
                        debtRepository.save(debt);
                    }
                }

                // Case 3: debt was created by this contribution (debtor=beneficiary)
                // Find the debt created for this session
                List<TontineDebt> debtorDebts = debtRepository
                    .findByTourIdAndDebtorId(tourId, old.getBeneficiaryId());
                debtorDebts.stream()
                    .filter(d -> old.getContributorId().equals(d.getCreditorId())
                              && sessionId.equals(d.getOriginSessionId())
                              && d.isOwed())
                    .findFirst()
                    .ifPresent(d -> debtRepository.deleteById(d.getId()));
            }
        }
    }

    /**
     * Handle contribution default — create sanction and possibly reorder.
     */
    private TontineContribution handleDefault(Long tourId, Long sessionId,
                                              TontineParticipant contributor,
                                              TontineParticipant beneficiary) {
        // Record default contribution
        TontineContribution contribution = new TontineContribution(
            null, tourId, sessionId, contributor.getMemberId(), beneficiary.getMemberId(),
            BigDecimal.ZERO, PaymentStatus.default_status, LocalDateTime.now());
        TontineContribution saved = contributionRepository.save(contribution);

        // Determine sanction
        BigDecimal sanctionAmount;
        boolean needsReassign = false;

        if (!contributor.isHasBenefited()) {
            // Has not yet benefited this tour → 2000 FCFA + last place
            sanctionAmount = DEFAULT_FINE_NOT_BENEFITED;
            needsReassign = true;
        } else {
            // Has already benefited → 5000 FCFA, no reclassification
            sanctionAmount = DEFAULT_FINE_BENEFITED;
        }

        // Create sanction
        createSanctionUseCase.execute(
            contributor.getMemberId(),
            LocalDate.now(),
            sanctionAmount,
            "Défaut de cotisation Grande Tontine — Tour #" + tourId + " — " + (needsReassign ? "Non bénéficié" : "Bénéficié"),
            SanctionOrigin.tontine_default,
            saved.getId()
        );

        // Un échec de cotisation n'engendre pas de nouvelle dette : le règlement
        // ne prévoit que la sanction (2 000 ou 5 000 FCFA) et, pour un membre pas
        // encore bénéficiaire, le renvoi en fin de classement. Si le cotisant
        // avait déjà bénéficié, la dette née de son propre passage reste due
        // telle quelle — elle n'est ni soldée ni augmentée.

        // Reassign draw order to last place if needed
        if (needsReassign) {
            List<TontineParticipant> allParticipants = participantRepository
                .findByTourIdOrderByDrawOrder(tourId);
            int maxOrder = allParticipants.stream()
                .mapToInt(TontineParticipant::getDrawOrder)
                .max()
                .orElse(0);
            contributor.reassignDrawOrder(maxOrder + 1);
            participantRepository.save(contributor);
        }

        return saved;
    }
}
