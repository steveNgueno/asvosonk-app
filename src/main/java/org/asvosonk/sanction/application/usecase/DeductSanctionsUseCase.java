package org.asvosonk.sanction.application.usecase;

import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.asvosonk.cashbox.application.service.CashboxService;
import org.asvosonk.cashbox.domain.valueobject.CashboxType;
import org.asvosonk.cashbox.domain.valueobject.MovementOrigin;
import org.asvosonk.member.infrastructure.persistence.entity.MemberEntity;
import org.asvosonk.sanction.domain.valueobject.SanctionStatus;
import org.asvosonk.sanction.infrastructure.persistence.entity.SanctionEntity;
import org.asvosonk.security.domain.model.AppUser;
import org.asvosonk.session.infrastructure.persistence.entity.MeetingSessionEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Retenues sur une tontine remise à un membre.
 *
 * <p>Quand un membre perçoit une tontine (présence ou grande tontine), ses
 * sanctions impayées sont retenues sur le montant qui lui revient, de la plus
 * ancienne à la plus récente. L'imputation est <strong>partielle</strong> : si la
 * tontine ne suffit pas à solder une sanction, on retient ce qui est disponible
 * et la sanction reste due pour le solde.</p>
 *
 * <p>L'argent retenu entre réellement en caisse Sanction : la retenue et
 * l'encaissement sont donc indissociables et se font dans la même transaction que
 * la clôture de l'étape.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DeductSanctionsUseCase {

    private final EntityManager  entityManager;
    private final CashboxService cashboxService;

    /**
     * Retient les sanctions impayées du membre sur le montant disponible.
     *
     * @param memberId  bénéficiaire de la tontine
     * @param available montant de la tontine sur lequel les retenues sont possibles
     * @param session   séance en cours (traçabilité du mouvement de caisse)
     * @param label     libellé du mouvement de caisse ("Retenue sur tontine de présence"…)
     * @param user      utilisateur connecté
     * @return total réellement retenu (≤ available)
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public BigDecimal deduct(Long memberId,
                             BigDecimal available,
                             MeetingSessionEntity session,
                             String label,
                             AppUser user) {
        if (memberId == null || available == null || available.signum() <= 0) {
            return BigDecimal.ZERO;
        }

        List<SanctionEntity> unpaid = entityManager.createQuery("""
                SELECT s FROM SanctionEntity s
                 WHERE s.member.id = :memberId
                   AND s.status = :unpaid
                 ORDER BY s.sanctionDate ASC, s.id ASC
                """, SanctionEntity.class)
            .setParameter("memberId", memberId)
            .setParameter("unpaid", SanctionStatus.unpaid)
            .getResultList();

        BigDecimal remainingTontine = available;
        BigDecimal deducted = BigDecimal.ZERO;

        for (SanctionEntity sanction : unpaid) {
            if (remainingTontine.signum() <= 0) {
                break;
            }
            BigDecimal taken = remainingTontine.min(sanction.remaining());
            if (taken.signum() <= 0) {
                continue;
            }
            sanction.collect(taken, LocalDate.now());
            entityManager.merge(sanction);
            remainingTontine = remainingTontine.subtract(taken);
            deducted = deducted.add(taken);
        }

        if (deducted.signum() > 0) {
            cashboxService.credit(CashboxType.sanction, deducted, label,
                MovementOrigin.sanction, session,
                entityManager.getReference(MemberEntity.class, memberId), null, user);
            log.info("Retenue de {} FCFA sur la tontine du membre {} ({})",
                deducted, memberId, label);
        }
        return deducted;
    }
}
