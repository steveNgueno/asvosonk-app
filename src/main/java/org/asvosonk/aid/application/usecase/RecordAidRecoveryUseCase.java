package org.asvosonk.aid.application.usecase;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.asvosonk.aid.domain.model.AidContribution;
import org.asvosonk.aid.domain.model.Aid;
import org.asvosonk.aid.domain.repository.AidContributionRepository;
import org.asvosonk.aid.domain.repository.AidRepository;
import org.asvosonk.aid.domain.valueobject.AidPaymentMode;
import org.asvosonk.common.domain.exception.BusinessRuleException;
import org.asvosonk.security.domain.model.AppUser;
import org.asvosonk.session.application.usecase.RequireOpenSessionUseCase;
import org.asvosonk.session.infrastructure.persistence.entity.MeetingSessionEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Recouvrement direct : un membre verse lui-même sa part d'aide, en séance.
 *
 * <p>Le recouvrement constitue une entrée du jour — il est rattaché à la
 * séance en cours et remis directement au trésorier, sans transiter par une
 * caisse. Quand toutes les parts sont recouvrées, l'aide n'est plus
 * d'actualité.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RecordAidRecoveryUseCase {

    private final AidContributionRepository aidContributionRepository;
    private final AidRepository             aidRepository;
    private final RequireOpenSessionUseCase requireOpenSession;

    /**
     * @param contributionId la part à recouvrir
     * @param amount         montant versé (le solde si null) ; un versement
     *                       partiel est accepté, la part reste due pour le reste
     */
    @Transactional
    public AidContribution execute(Long contributionId, BigDecimal amount, AppUser paidBy) {
        MeetingSessionEntity session = requireOpenSession.require("le recouvrement d'une aide");

        AidContribution contribution = aidContributionRepository.findById(contributionId)
            .orElseThrow(() -> new BusinessRuleException("Part d'aide introuvable : " + contributionId));
        if (!contribution.isOwed()) {
            throw new BusinessRuleException("Cette part a déjà été entièrement recouverte.");
        }

        final Long aidId = contribution.getAidId();
        Aid aid = aidRepository.findById(aidId)
            .orElseThrow(() -> new BusinessRuleException("Aide introuvable : " + aidId));
        if (!aid.isCurrent()){
            throw new BusinessRuleException("Cette aide n'est plus d'actualité.");
        }

        BigDecimal due = contribution.getRemaining();
        if (due.signum() <= 0) {
            throw new BusinessRuleException("Cette part n'a plus rien à recouvrir.");
        }
        BigDecimal paid = amount != null ? amount : due;
        if (paid.signum() <= 0) {
            throw new BusinessRuleException("Le montant versé doit être strictement positif.");
        }
        if (paid.compareTo(due) > 0) {
            java.text.NumberFormat fmt = java.text.NumberFormat.getIntegerInstance();
            throw new BusinessRuleException(
                "Le montant versé (" + fmt.format(paid) + " FCFA) dépasse le reste à recouvrir "
              + "(" + fmt.format(due) + " FCFA) sur cette part.");
        }

        contribution.collect(paid, AidPaymentMode.direct, LocalDate.now());
        // sessionId portée par le domaine pour rattacher l'encaissement à la séance.
        contribution = reopenWithSession(contribution, session.getId());
        AidContribution saved = aidContributionRepository.save(contribution);

        completeAidIfFullyRecovered(aid);

        log.info("Recouvrement direct de {} FCFA sur l'aide #{} (part du membre {})"
                + " en séance {}.",
            paid, aid.getId(), contribution.getMemberId(), session.getSessionDate());
        return saved;
    }

    /** Rattache l'encaissement à la séance du jour (entrée du jour). */
    private AidContribution reopenWithSession(AidContribution contribution, Long sessionId) {
        return new AidContribution(contribution.getId(), contribution.getAidId(),
            contribution.getMemberId(), contribution.getAmountDue(),
            contribution.getAmountPaid(), contribution.getStatus(),
            contribution.getPaymentMode(), contribution.getPaymentDate(),
            sessionId, contribution.getCreatedAt(), contribution.getUpdatedAt());
    }

    /**
     * Une aide n'est plus d'actualité lorsque tous les membres ont recouvert :
     * dès que plus aucune part n'est due, elle passe au statut completed.
     */
    private void completeAidIfFullyRecovered(Aid aid) {
        boolean allRecovered = aidContributionRepository
            .findByAidIdAndStatus(aid.getId(),
                org.asvosonk.aid.domain.valueobject.AidContributionStatus.owed)
            .isEmpty();
        if (allRecovered && aid.isCurrent()) {
            aid.complete();
            aidRepository.save(aid);
            log.info("Aide #{} entièrement recouverte : elle n'est plus d'actualité.", aid.getId());
        }
    }
}
