package org.asvosonk.session.application.usecase;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.asvosonk.common.domain.exception.BusinessRuleException;
import jakarta.persistence.EntityManager;
import org.asvosonk.cashbox.domain.valueobject.MovementDirection;
import org.asvosonk.security.domain.model.AppUser;
import org.asvosonk.session.domain.valueobject.SessionStep;
import org.asvosonk.session.infrastructure.persistence.entity.MeetingSessionEntity;
import org.asvosonk.session.infrastructure.persistence.entity.SessionReportEntity;
import org.asvosonk.session.infrastructure.persistence.repository.SessionReportRepository;
import org.asvosonk.session.application.service.SessionService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Fige le rapport de séance à la dernière étape du déroulé.
 *
 * <p>Les chiffres de la présence et de la grande tontine ont déjà été calculés à
 * la clôture de leurs étapes respectives. Ce cas d'usage recalcule les flux de
 * caisse — des entrées ou des sorties ont pu être saisies après ces clôtures — et
 * horodate le rapport.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GenerateSessionReportUseCase {

    private final SessionService          sessionService;
    private final SessionReportRepository sessionReportRepository;
    private final EntityManager           entityManager;
    private final org.asvosonk.member.infrastructure.persistence.repository.MembershipFeePaymentRepository
        membershipFeePaymentRepository;

    @Transactional
    public SessionReportEntity execute(Long sessionId, AppUser user) {
        MeetingSessionEntity session = sessionService.findById(sessionId);

        if (session.isStepExactly(SessionStep.REPORT_GENERATED)) {
            throw new BusinessRuleException("Le rapport de cette séance a déjà été généré.");
        }

        SessionReportEntity report = sessionReportRepository.findBySessionId(sessionId)
            .orElseGet(() -> {
                SessionReportEntity r = new SessionReportEntity();
                r.setSessionId(sessionId);
                return r;
            });

        // Entrées et sorties de caisse rattachées à la séance : le total remis au
        // trésorier est leur solde. Les tontines et le retour dans les fonds de
        // roulement ne passent par aucune caisse et n'y figurent donc pas.
        List<Object[]> rows = entityManager.createQuery("""
                SELECT m.direction, SUM(m.amount)
                  FROM CashboxMovementEntity m
                 WHERE m.session.id = :sessionId
                 GROUP BY m.direction
                """, Object[].class)
            .setParameter("sessionId", sessionId)
            .getResultList();

        BigDecimal totalIn = BigDecimal.ZERO;
        BigDecimal totalOut = BigDecimal.ZERO;
        for (Object[] row : rows) {
            if (row[0] == MovementDirection.in) {
                totalIn = totalIn.add((BigDecimal) row[1]);
            } else {
                totalOut = totalOut.add((BigDecimal) row[1]);
            }
        }

        // Les frais d'adhésion encaissés en séance vont droit au trésorier, sans
        // passer par une caisse : ils comptent dans les entrées du jour.
        BigDecimal fees = membershipFeePaymentRepository.totalBySessionId(sessionId);
        if (fees == null) {
            fees = BigDecimal.ZERO;
        }
        totalIn = totalIn.add(fees);
        report.setMembershipFeesCollected(fees);

        // Si les sorties dépassent les entrées, rien n'est remis au trésorier :
        // l'écart a été prélevé sur le solde déjà en caisse.
        BigDecimal balance = totalIn.subtract(totalOut);
        report.setTotalOutflow(totalOut);
        report.setTotalToTreasurer(balance.max(BigDecimal.ZERO));
        report.setTotalFromCashboxes(balance.negate().max(BigDecimal.ZERO));
        report.setGeneratedAt(LocalDateTime.now());

        SessionReportEntity saved = sessionReportRepository.save(report);
        log.info("Rapport de la séance {} — entrées={}, sorties={}, remis au trésorier={}, prélevé en caisse={}",
            sessionId, totalIn, totalOut, saved.getTotalToTreasurer(), saved.getTotalFromCashboxes());
        return saved;
    }
}
