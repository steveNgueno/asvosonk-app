package org.asvosonk.tontine.application.usecase;

import lombok.RequiredArgsConstructor;
import org.asvosonk.common.domain.exception.BusinessRuleException;
import org.asvosonk.common.domain.exception.ResourceNotFoundException;
import org.asvosonk.sanction.application.usecase.CreateSanctionUseCase;
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
        TontineTour tour = tourRepository.findById(tourId)
            .orElseThrow(() -> new ResourceNotFoundException("Tour de grande tontine", tourId));

        if (!tour.isOpen()) {
            throw new BusinessRuleException("Le tour de grande tontine est clôturé.");
        }

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

        // Check if there's an existing debt from beneficiary to contributor
        Optional<TontineDebt> existingDebt = debtRepository
            .findByTourIdAndDebtorIdAndCreditorIdAndStatus(
                tourId, beneficiaryId, contributorId, DebtStatus.owed);

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

        // Create debt: beneficiary owes the contributor
        TontineDebt debt = new TontineDebt(
            null, tourId, beneficiaryId, contributorId,
            amount, sessionId, DebtStatus.owed, null,
            LocalDateTime.now(), LocalDateTime.now());
        debtRepository.save(debt);

        return saved;
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
