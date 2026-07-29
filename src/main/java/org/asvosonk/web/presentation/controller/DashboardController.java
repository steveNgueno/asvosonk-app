package org.asvosonk.web.presentation.controller;

import lombok.RequiredArgsConstructor;
import org.asvosonk.cashbox.application.usecase.GenerateBalanceUseCase;
import org.asvosonk.cashbox.domain.valueobject.CashboxType;
import org.asvosonk.member.application.usecase.SearchMemberUseCase;
import org.asvosonk.member.domain.model.Member;
import org.asvosonk.presence.application.usecase.GetPresenceTourSummaryUseCase;
import org.asvosonk.presence.domain.model.PresenceTour;
import org.asvosonk.presence.domain.model.PresenceTourParticipant;
import org.asvosonk.sanction.domain.model.Sanction;
import org.asvosonk.sanction.domain.repository.SanctionRepository;
import org.asvosonk.sanction.domain.valueobject.SanctionStatus;
import org.asvosonk.security.application.service.UserDetailsImpl;
import org.asvosonk.session.domain.model.MeetingSession;
import org.asvosonk.session.domain.repository.MeetingSessionRepository;
import org.asvosonk.session.domain.valueobject.SessionStep;
import org.asvosonk.session.infrastructure.persistence.entity.SessionReportEntity;
import org.asvosonk.session.infrastructure.persistence.repository.SessionReportRepository;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import java.math.BigDecimal;
import java.util.List;

@Controller
@RequiredArgsConstructor
@PreAuthorize("isAuthenticated()")
public class DashboardController {

    private final GenerateBalanceUseCase         generateBalanceUseCase;
    private final GetPresenceTourSummaryUseCase  presenceSummary;
    private final MeetingSessionRepository        meetingSessionRepository;
    private final SessionReportRepository         sessionReportRepository;
    private final SanctionRepository              sanctionRepository;
    private final SearchMemberUseCase             searchMemberUseCase;

    @GetMapping({"/", "/dashboard"})
    public String dashboard(@AuthenticationPrincipal UserDetailsImpl principal,
                            Model model) {
        if (principal == null || principal.getAppUser() == null) {
            return "redirect:/login";
        }
        model.addAttribute("userName",  principal.getAppUser().getLogin());
        model.addAttribute("roleName",  principal.getAppUser().getRole().getName());
        model.addAttribute("lastLogin", principal.getAppUser().getLastLogin());

        // ── Soldes des 4 caisses (lecture seule) ──────────────
        model.addAttribute("cashboxTypes", CashboxType.values());
        model.addAttribute("balances",     generateBalanceUseCase.execute());

        // ── Séances : la source de vérité est currentStep (F-01) ──
        // SessionStatus reste toujours "open" (machine à états morte) : filtrer dessus
        // ferait planter findByStatus(open) dès la 2e séance (résultat non unique).
        // On lit donc l'état réel via currentStep, sur la liste déjà triée par date desc.
        List<MeetingSession> allSessions = meetingSessionRepository.findAllByOrderBySessionDateDesc();
        MeetingSession openSession = allSessions.stream()
                .filter(s -> s.getCurrentStep() != SessionStep.REPORT_GENERATED)
                .findFirst().orElse(null);
        model.addAttribute("openSession", openSession);

        // ── Prochain bénéficiaire de présence (lecture seule) ──
        // On évite GetCurrentPresenceBeneficiaryUseCase qui clôture le tour
        // comme effet de bord ; GetPresenceTourSummaryUseCase est en lecture seule.
        String nextPresenceBeneficiary = null;
        PresenceTour openTour = presenceSummary.findCurrentOpenTour();
        Integer presenceTourNumber = openTour != null ? openTour.getId().intValue() : null;
        if (openTour != null) {
            List<PresenceTourParticipant> pending =
                    presenceSummary.findNotBenefitedYet(openTour.getId());
            if (!pending.isEmpty()) {
                nextPresenceBeneficiary = resolveName(pending.get(0).getMemberId());
            }
        }
        model.addAttribute("nextPresenceBeneficiary", nextPresenceBeneficiary);
        model.addAttribute("presenceTourNumber", presenceTourNumber);

        // ── Dernière séance clôturée + son rapport ────────────
        // "Clôturée" = workflow arrivé à REPORT_GENERATED (et non SessionStatus.closed,
        // jamais positionné). allSessions est déjà trié par date desc.
        MeetingSession lastSession = allSessions.stream()
                .filter(s -> s.getCurrentStep() == SessionStep.REPORT_GENERATED)
                .findFirst().orElse(null);
        model.addAttribute("lastSession", lastSession);
        if (lastSession != null) {
            SessionReportEntity report = sessionReportRepository
                    .findBySessionId(lastSession.getId()).orElse(null);
            model.addAttribute("lastReport", report);
            if (report != null && report.getPresenceBeneficiaryId() != null) {
                model.addAttribute("lastBeneficiaryName",
                        resolveName(report.getPresenceBeneficiaryId()));
            }
        }

        // ── Sanctions impayées (agrégat en Java) ──────────────
        List<Sanction> unpaid = sanctionRepository
                .findByStatusOrderBySanctionDateDesc(SanctionStatus.unpaid);
        BigDecimal unpaidTotal = unpaid.stream()
                .map(Sanction::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        model.addAttribute("unpaidSanctionsCount", unpaid.size());
        model.addAttribute("unpaidSanctionsTotal", unpaidTotal);

        return "dashboard";   // → templates/dashboard.html
    }

    /** Résout le nom complet d'un membre, en tolérant un id introuvable. */
    private String resolveName(Long memberId) {
        try {
            Member m = searchMemberUseCase.findById(memberId);
            return m != null ? m.getFullName() : null;
        } catch (RuntimeException ex) {
            return null;
        }
    }
}
