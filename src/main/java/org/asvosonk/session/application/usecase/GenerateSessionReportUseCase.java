package org.asvosonk.session.application.usecase;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.asvosonk.common.domain.exception.BusinessRuleException;
import jakarta.persistence.EntityManager;
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

/**
 * Aggregates all session financial data and persists the final session report.
 * Called when transitioning to REPORT_GENERATED step.
 * The presence data is already saved by closePresence();
 * this use case updates the report with tontine, banque projet,
 * and banque annuelle data, then generates the synthesis.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GenerateSessionReportUseCase {

    private final SessionService          sessionService;
    private final SessionReportRepository sessionReportRepository;
    private final EntityManager           entityManager;

    @Transactional
    public SessionReportEntity execute(Long sessionId, AppUser user) {
        MeetingSessionEntity session = sessionService.findById(sessionId);

        if (session.isStepExactly(SessionStep.REPORT_GENERATED)) {
            throw new BusinessRuleException("Le rapport de cette séance a déjà été généré.");
        }

        // Load existing report (presence data already persisted by closePresence)
        SessionReportEntity report = sessionReportRepository.findBySessionId(sessionId)
            .orElseGet(() -> {
                SessionReportEntity r = new SessionReportEntity();
                r.setSessionId(sessionId);
                return r;
            });

        // ── Update tontine data ─────────────────────────────
        var tontineContribs = entityManager.createQuery(
                "SELECT c.amount, c.status FROM TontineContributionEntity c WHERE c.sessionId = :sessionId",
                Object[].class)
            .setParameter("sessionId", sessionId)
            .getResultList();

        BigDecimal tontineGross = BigDecimal.ZERO;
        for (Object[] row : tontineContribs) {
            String status = row[1].toString();
            if ("paid".equals(status)) {
                tontineGross = tontineGross.add((BigDecimal) row[0]);
            }
        }
        report.setTontineGrossCollected(tontineGross);
        report.setTontineNetPaid(tontineGross);

        // ── Update banque projet data ────────────────────────
        // (placeholder)
        // report.setBanqueProjetCollected(...);

        // ── Update banque annuelle data ──────────────────────
        // (placeholder)
        // report.setBanqueAnnuelleSavings(...);
        // report.setBanqueAnnuelleRepayments(...);

        // ── Calculate synthesis ──────────────────────────────
        BigDecimal presenceNet = report.getPresenceNetTontine() != null
            ? report.getPresenceNetTontine() : BigDecimal.ZERO;
        BigDecimal presenceDev = report.getPresenceDevelopmentTotal() != null
            ? report.getPresenceDevelopmentTotal() : BigDecimal.ZERO;

        report.setTotalToTreasurer(presenceNet.add(presenceDev).add(tontineGross));
        report.setGeneratedAt(LocalDateTime.now());

        SessionReportEntity saved = sessionReportRepository.save(report);

        log.info("Session report generated for session {} — totalToTreasurer={}",
            sessionId, saved.getTotalToTreasurer());

        return saved;
    }
}
