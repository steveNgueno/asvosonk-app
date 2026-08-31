package org.asvosonk.aid.application.usecase;

import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.asvosonk.aid.domain.valueobject.AidContributionStatus;
import org.asvosonk.aid.domain.valueobject.AidPaymentMode;
import org.asvosonk.aid.domain.valueobject.AidStatus;
import org.asvosonk.aid.infrastructure.persistence.entity.AidContributionEntity;
import org.asvosonk.aid.infrastructure.persistence.entity.AidEntity;
import org.asvosonk.security.domain.model.AppUser;
import org.asvosonk.session.infrastructure.persistence.entity.MeetingSessionEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Retenues d'aides sur une tontine remise à un membre.
 *
 * <p>Quand un membre perçoit une tontine (présence ou grande tontine), les
 * parts d'aides qu'il doit recouvrir sont coupées obligatoirement sur le
 * montant qui lui revient — comme pour les sanctions — de l'aide la plus
 * ancienne à la plus récente. L'imputation est <strong>partielle</strong> : si
 * la tontine ne suffit pas à solder la part, on retient ce qui est disponible
 * et la part reste due pour le solde.</p>
 *
 * <p>Les recouvrements par retenue sont des entrées du jour : chaque part
 * touchée est rattachée à la séance en cours et remise au trésorier, sans
 * transiter par une caisse.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DeductAidsUseCase {

    private final EntityManager entityManager;

    /**
     * Retient les parts d'aides dues du membre sur le montant disponible.
     *
     * @param memberId  bénéficiaire de la tontine
     * @param available montant de la tontine sur lequel les retenues sont possibles
     * @param session   séance en cours (rattachement de l'entrée du jour)
     * @param mode      retained_presence ou retained_tontine selon l'étape
     * @param label     libellé pour la traçabilité
     * @param user      utilisateur connecté
     * @return total réellement retenu (≤ available)
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public BigDecimal deduct(Long memberId,
                             BigDecimal available,
                             MeetingSessionEntity session,
                             AidPaymentMode mode,
                             String label,
                             AppUser user) {
        if (memberId == null || available == null || available.signum() <= 0) {
            return BigDecimal.ZERO;
        }

        List<AidContributionEntity> owed = entityManager.createQuery("""
                SELECT c FROM AidContributionEntity c
                 WHERE c.member.id = :memberId
                   AND c.status = :owed
                 ORDER BY c.aid.aidDate ASC, c.id ASC
                """, AidContributionEntity.class)
            .setParameter("memberId", memberId)
            .setParameter("owed", AidContributionStatus.owed)
            .getResultList();

        // Les aides déjà soldées (tous les membres ont recouvert) ne sont plus
        // d'actualité : elles sont exclues ci-dessus puisque leurs parts sont payées.
        BigDecimal remainingTontine = available;
        BigDecimal deducted = BigDecimal.ZERO;

        for (AidContributionEntity contribution : owed) {
            if (remainingTontine.signum() <= 0) {
                break;
            }
            BigDecimal taken = contribution.collect(remainingTontine, mode, LocalDate.now());
            if (taken.signum() <= 0) {
                continue;
            }
            contribution.setSession(session);
            entityManager.merge(contribution);
            remainingTontine = remainingTontine.subtract(taken);
            deducted = deducted.add(taken);

            completeAidIfFullyRecovered(contribution.getAid().getId());
        }

        if (deducted.signum() > 0) {
            log.info("Retenue d'aides de {} FCFA sur la tontine du membre {} ({})",
                deducted, memberId, label);
        }
        return deducted;
    }

    /** Une aide n'est plus d'actualité lorsque tous les membres ont recouvert. */
    private void completeAidIfFullyRecovered(Long aidId) {
        Long open = entityManager.createQuery("""
                SELECT COUNT(c) FROM AidContributionEntity c
                 WHERE c.aid.id = :aidId AND c.status = :owed
                """, Long.class)
            .setParameter("aidId", aidId)
            .setParameter("owed", AidContributionStatus.owed)
            .getSingleResult();
        if (open == 0) {
            // On passe par une entité gérée plutôt qu'un UPDATE en masse :
            // le contexte de persistance reste cohérent avec la base.
            AidEntity aid = entityManager.find(AidEntity.class, aidId);
            if (aid != null && aid.getStatus() == AidStatus.in_progress) {
                aid.setStatus(AidStatus.completed);
                log.info("Aide #{} entièrement recouverte : elle n'est plus d'actualité.", aidId);
            }
        }
    }
}
