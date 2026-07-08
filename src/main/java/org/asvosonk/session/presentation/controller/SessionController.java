package org.asvosonk.session.presentation.controller;

import jakarta.persistence.EntityManager;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.asvosonk.common.domain.exception.BusinessRuleException;
import org.asvosonk.member.application.usecase.SearchMemberUseCase;
import org.asvosonk.member.domain.model.Member;
import org.asvosonk.presence.application.usecase.GetCurrentPresenceBeneficiaryUseCase;
import org.asvosonk.presence.application.usecase.GetPresenceTourSummaryUseCase;
import org.asvosonk.security.application.service.UserDetailsImpl;
import org.asvosonk.session.application.service.SessionService;
import org.asvosonk.session.application.service.SessionStepService;
import org.asvosonk.session.domain.valueobject.SessionStep;
import org.asvosonk.session.infrastructure.persistence.entity.MeetingSessionEntity;
import org.asvosonk.cashbox.infrastructure.persistence.entity.CashboxEntity;
import org.asvosonk.session.infrastructure.persistence.entity.SessionReportEntity;
import org.asvosonk.tontine.application.usecase.GetTourSummaryUseCase;
import org.asvosonk.tontine.application.usecase.RecordTontineContributionUseCase;
import org.asvosonk.tontine.application.usecase.MarkBenefitedUseCase;
import org.asvosonk.tontine.domain.model.TontineContribution;
import org.asvosonk.tontine.domain.model.TontineParticipant;
import org.asvosonk.tontine.domain.model.TontineTour;
import org.asvosonk.session.infrastructure.persistence.repository.SessionReportRepository;
import org.asvosonk.session.presentation.request.AttendanceEntryForm;
import org.asvosonk.session.presentation.request.SessionForm;
import org.asvosonk.session.presentation.response.SessionCloseResult;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.math.BigDecimal;

@Controller
@RequestMapping("/sessions")
@RequiredArgsConstructor
public class SessionController {

    private final SessionService                   sessionService;
    private final SessionStepService               sessionStepService;
    private final SearchMemberUseCase              searchMemberUseCase;
    private final SessionReportRepository          sessionReportRepository;
    private final EntityManager                    entityManager;    private final GetCurrentPresenceBeneficiaryUseCase    getCurrentPresenceBeneficiaryUseCase;
    private final GetPresenceTourSummaryUseCase    getPresenceTourSummaryUseCase;
    private final GetTourSummaryUseCase            getTourSummaryUseCase;
    private final RecordTontineContributionUseCase recordTontineContributionUseCase;
    private final MarkBenefitedUseCase             markBenefitedUseCase;

    // ── List ─────────────────────────────────────────────────

    @GetMapping
    @PreAuthorize("hasAuthority('SESSION_VIEW')")
    public String list(Model model) {
        var sessions = sessionService.findAll();

        // Compute step index for each session for progress display
        java.util.Map<Long, Integer> stepIndexMap = new java.util.HashMap<>();
        for (var s : sessions) {
            stepIndexMap.put(s.getId(), computeDisplayStepIndex(s.getCurrentStepEnum()));
        }

        model.addAttribute("sessions", sessions);
        model.addAttribute("displaySteps", SessionStep.DisplayStep.values());
        model.addAttribute("stepIndexMap", stepIndexMap);
        model.addAttribute("totalSteps", SessionStep.DisplayStep.values().length);
        model.addAttribute("pageTitle", "Séances");
        return "sessions/list";
    }

    // ── Detail (shows current step content) ──────────────────

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('SESSION_VIEW')")
    public String detail(@PathVariable Long id, Model model) {
        MeetingSessionEntity session = sessionService.findById(id);
        model.addAttribute("meetingSession", session);
        model.addAttribute("attendances",    sessionService.findAttendances(id));
        model.addAttribute("currentStep",    session.getCurrentStepEnum());
        model.addAttribute("displaySteps",   SessionStep.DisplayStep.values());
        model.addAttribute("currentDisplayStepIdx", computeDisplayStepIndex(session.getCurrentStepEnum()));
        model.addAttribute("isReportGenerated", session.isStepAtLeast(SessionStep.REPORT_GENERATED));

        // Presence beneficiary info from tour
        var openTour = getPresenceTourSummaryUseCase.findCurrentOpenTour();
        if (openTour != null) {
            model.addAttribute("presenceOpenTour", openTour);
            var nextBeneficiary = getCurrentPresenceBeneficiaryUseCase.execute();
            if (nextBeneficiary != null) {
                model.addAttribute("nextPresenceBeneficiary",
                    searchMemberUseCase.findById(nextBeneficiary.getMemberId()));
            }
        }

        // Session report if generated
        if (session.isStepAtLeast(SessionStep.PRESENCE_CLOSED)) {
            sessionReportRepository.findBySessionId(id).ifPresent(report -> {
                SessionCloseResult closeResult = buildResultFromReport(report, session);
                model.addAttribute("closeResult", closeResult);
                model.addAttribute("sessionReport", report);
            });
        }

        // ── Tontine data for TONTINE_OPEN step ─────────────
        if (session.isStepAtLeast(SessionStep.TONTINE_OPEN) && !session.isStepAtLeast(SessionStep.TONTINE_CLOSED)) {
            TontineTour tontineOpenTour = getTourSummaryUseCase.findCurrentOpenTour();
            if (tontineOpenTour != null) {
                model.addAttribute("tontineTour", tontineOpenTour);
                model.addAttribute("tontineParticipants",
                    getTourSummaryUseCase.findParticipantsByTourId(tontineOpenTour.getId()));

                java.util.List<TontineParticipant> notBenefited =
                    getTourSummaryUseCase.findNotBenefitedYet(tontineOpenTour.getId());
                if (!notBenefited.isEmpty()) {
                    model.addAttribute("tontineNextBeneficiary", notBenefited.get(0));
                }

                // Build member name map
                java.util.Map<Long, String> memberNames = new java.util.HashMap<>();
                for (var p : getTourSummaryUseCase.findParticipantsByTourId(tontineOpenTour.getId())) {
                    memberNames.putIfAbsent(p.getMemberId(),
                        searchMemberUseCase.findById(p.getMemberId()).getFullName());
                }
                model.addAttribute("tontineMemberNames", memberNames);

                // Existing contributions for this session
                var existingContribs = getTourSummaryUseCase.findContributionsByTourId(tontineOpenTour.getId())
                    .stream()
                    .filter(c -> c.getSessionId().equals(id))
                    .toList();
                model.addAttribute("tontineSessionContribs", existingContribs);
            }
        }

        // Tontine summary after CLOSED
        if (session.isStepAtLeast(SessionStep.TONTINE_CLOSED)) {
            TontineTour tontineOpenTour = getTourSummaryUseCase.findCurrentOpenTour();
            if (tontineOpenTour != null) {
                var tourContribs = getTourSummaryUseCase.findContributionsByTourId(tontineOpenTour.getId())
                    .stream()
                    .filter(c -> c.getSessionId().equals(id))
                    .toList();
                if (!tourContribs.isEmpty()) {
                    java.math.BigDecimal tontineGross = tourContribs.stream()
                        .map(TontineContribution::getAmount)
                        .reduce(java.math.BigDecimal.ZERO, java.math.BigDecimal::add);
                    model.addAttribute("tontineSessionTotal", tontineGross);
                    model.addAttribute("tontineSessionContribs", tourContribs);

                    java.util.Map<Long, String> memberNames = new java.util.HashMap<>();
                    for (var c : tourContribs) {
                        memberNames.putIfAbsent(c.getContributorId(),
                            searchMemberUseCase.findById(c.getContributorId()).getFullName());
                        memberNames.putIfAbsent(c.getBeneficiaryId(),
                            searchMemberUseCase.findById(c.getBeneficiaryId()).getFullName());
                    }
                    model.addAttribute("tontineMemberNames", memberNames);
                }
            }
        }

        model.addAttribute("pageTitle", "Séance du " + session.getSessionDate());
        return "sessions/detail";
    }

    /**
     * Maps a SessionStep to its DisplayStep index (0-5) for the stepper.
     */
    private int computeDisplayStepIndex(SessionStep step) {
        return switch (step) {
            case CREATED               -> 0; // Agenda
            case PRESENCE_OPEN,
                 PRESENCE_CLOSED       -> 1; // Présence
            case TONTINE_OPEN,
                 TONTINE_CLOSED        -> 2; // Grande Tontine
            case BANQUE_PROJET_OPEN,
                 BANQUE_PROJET_CLOSED  -> 3; // Banque Projet
            case BANQUE_ANNUELLE_OPEN,
                 BANQUE_ANNUELLE_CLOSED -> 4; // Banque Annuelle
            case REPORT_GENERATED      -> 5; // Rapport
        };
    }

    // ── Create ───────────────────────────────────────────────

    @GetMapping("/new")
    @PreAuthorize("hasAuthority('SESSION_CREATE')")
    public String newForm(Model model) {
        model.addAttribute("sessionForm", new SessionForm());
        model.addAttribute("pageTitle", "Nouvelle séance");
        return "sessions/form";
    }

    @PostMapping("/new")
    @PreAuthorize("hasAuthority('SESSION_CREATE')")
    public String create(@Valid @ModelAttribute SessionForm sessionForm,
                         BindingResult result,
                         @AuthenticationPrincipal UserDetailsImpl principal,
                         Model model,
                         RedirectAttributes ra) {
        if (result.hasErrors()) {
            model.addAttribute("pageTitle", "Nouvelle séance");
            return "sessions/form";
        }
        try {
            MeetingSessionEntity session = sessionService.create(sessionForm, principal.getAppUser());
            ra.addFlashAttribute("successMessage",
                "Séance du " + session.getSessionDate() + " créée avec succès.");
            return "redirect:/sessions/" + session.getId();
        } catch (IllegalArgumentException e) {
            model.addAttribute("errorMessage", e.getMessage());
            return "sessions/form";
        }
    }

    // ── Attendance form ──────────────────────────────────────

    @GetMapping("/{id}/attendance")
    @PreAuthorize("hasAuthority('ATTENDANCE_RECORD')")
    public String attendanceForm(@PathVariable Long id, Model model) {
        MeetingSessionEntity session = sessionService.findById(id);
        if (!session.isStepExactly(SessionStep.PRESENCE_OPEN)) {
            return "redirect:/sessions/" + id;
        }

        model.addAttribute("meetingSession", session);
        model.addAttribute("attendances",    sessionService.findAttendances(id));
        model.addAttribute("members",        searchMemberUseCase.findAllActive());
        model.addAttribute("entryForm",      new AttendanceEntryForm());

        // Current beneficiary info
        if (session.getPresenceBeneficiary() != null) {
            model.addAttribute("beneficiaryMember",
                searchMemberUseCase.findById(session.getPresenceBeneficiary().getId()));
        }
        var openTour = getPresenceTourSummaryUseCase.findCurrentOpenTour();
        if (openTour != null) {
            model.addAttribute("presenceOpenTour", openTour);
        }

        // ── Revolving fund data per member ──────────────────
        java.util.List<Object[]> fundData = entityManager.createQuery(
                "SELECT f.member.id, f.balance, " +
                "  (SELECT COUNT(m) FROM RevolvingFundMovementEntity m " +
                "   WHERE m.fund.member.id = f.member.id " +
                "   AND m.movementType = 'advance' AND m.recovered = false) " +
                "FROM RevolvingFundEntity f", Object[].class)
            .getResultList();

        java.util.Map<Long, java.math.BigDecimal> fundBalances = new java.util.HashMap<>();
        java.util.Map<Long, Integer> pendingDebts = new java.util.HashMap<>();
        for (Object[] row : fundData) {
            Long memberId = (Long) row[0];
            fundBalances.put(memberId, (java.math.BigDecimal) row[1]);
            pendingDebts.put(memberId, ((Number) row[2]).intValue());
        }
        model.addAttribute("fundBalances", fundBalances);
        model.addAttribute("pendingDebts", pendingDebts);

        model.addAttribute("pageTitle", "Saisie — " + session.getSessionDate());
        return "sessions/attendance";
    }

    @PostMapping("/{id}/attendance")
    @PreAuthorize("hasAuthority('ATTENDANCE_RECORD')")
    public String saveAttendanceEntry(@PathVariable Long id,
                                      @Valid @ModelAttribute AttendanceEntryForm entryForm,
                                      BindingResult result,
                                      RedirectAttributes ra) {
        if (result.hasErrors()) {
            ra.addFlashAttribute("errorMessage", "Données invalides.");
            return "redirect:/sessions/" + id + "/attendance";
        }
        sessionService.saveAttendanceEntry(id, entryForm);
        return "redirect:/sessions/" + id + "/attendance";
    }

    // ── Step transition endpoints ────────────────────────────

    private String transitionToNext(Long id, UserDetailsImpl user,
                                     RedirectAttributes ra, String successMsg,
                                     String errorRedirect) {
        try {
            sessionStepService.transitionToNext(id, user.getAppUser());
            ra.addFlashAttribute("successMessage", successMsg);
        } catch (BusinessRuleException | IllegalStateException e) {
            ra.addFlashAttribute("errorMessage", e.getMessage());
            return "redirect:" + errorRedirect;
        }
        return "redirect:/sessions/" + id;
    }

    @PostMapping("/{id}/presence/open")
    @PreAuthorize("hasAuthority('ATTENDANCE_RECORD')")
    public String openPresence(@PathVariable Long id,
                                @AuthenticationPrincipal UserDetailsImpl user,
                                RedirectAttributes ra) {
        return transitionToNext(id, user, ra,
            "Saisie de présence ouverte.",
            "/sessions/" + id);
    }

    @PostMapping("/{id}/presence/close")
    @PreAuthorize("hasAuthority('SESSION_CLOSE')")
    public String closePresence(@PathVariable Long id,
                                 @AuthenticationPrincipal UserDetailsImpl user,
                                 RedirectAttributes ra) {
        try {
            sessionStepService.transitionToNext(id, user.getAppUser());
            ra.addFlashAttribute("successMessage", "Présence clôturée avec succès.");
            return "redirect:/sessions/" + id;
        } catch (BusinessRuleException | IllegalStateException e) {
            ra.addFlashAttribute("errorMessage", e.getMessage());
            return "redirect:/sessions/" + id + "/attendance";
        }
    }

    @PostMapping("/{id}/tontine/open")
    @PreAuthorize("hasAuthority('TONTINE_VIEW')")
    public String openTontine(@PathVariable Long id,
                               @AuthenticationPrincipal UserDetailsImpl user,
                               RedirectAttributes ra) {
        return transitionToNext(id, user, ra,
            "Grande tontine ouverte.",
            "/sessions/" + id);
    }

    @PostMapping("/{id}/tontine/close")
    @PreAuthorize("hasAuthority('TONTINE_TOUR_CREATE')")
    public String closeTontine(@PathVariable Long id,
                                @AuthenticationPrincipal UserDetailsImpl user,
                                RedirectAttributes ra) {
        return transitionToNext(id, user, ra,
            "Grande tontine clôturée.",
            "/sessions/" + id);
    }

    @PostMapping("/{id}/banque-projet/open")
    @PreAuthorize("hasAuthority('BANK_VIEW')")
    public String openBanqueProjet(@PathVariable Long id,
                                    @AuthenticationPrincipal UserDetailsImpl user,
                                    RedirectAttributes ra) {
        return transitionToNext(id, user, ra,
            "Banque projet ouverte.",
            "/sessions/" + id);
    }

    @PostMapping("/{id}/banque-projet/close")
    @PreAuthorize("hasAuthority('BANK_VIEW')")
    public String closeBanqueProjet(@PathVariable Long id,
                                     @AuthenticationPrincipal UserDetailsImpl user,
                                     RedirectAttributes ra) {
        return transitionToNext(id, user, ra,
            "Banque projet clôturée.",
            "/sessions/" + id);
    }

    @PostMapping("/{id}/banque-annuelle/open")
    @PreAuthorize("hasAuthority('BANK_VIEW')")
    public String openBanqueAnnuelle(@PathVariable Long id,
                                      @AuthenticationPrincipal UserDetailsImpl user,
                                      RedirectAttributes ra) {
        return transitionToNext(id, user, ra,
            "Banque annuelle ouverte.",
            "/sessions/" + id);
    }

    @PostMapping("/{id}/banque-annuelle/close")
    @PreAuthorize("hasAuthority('BANK_VIEW')")
    public String closeBanqueAnnuelle(@PathVariable Long id,
                                       @AuthenticationPrincipal UserDetailsImpl user,
                                       RedirectAttributes ra) {
        return transitionToNext(id, user, ra,
            "Banque annuelle clôturée.",
            "/sessions/" + id);
    }

    // ── Tontine contribution within session ────────────────

    @PostMapping("/{id}/tontine/contribute")
    @PreAuthorize("hasAuthority('TONTINE_CONTRIBUTION_RECORD')")
    public String saveTontineContribution(@PathVariable Long id,
                                           @RequestParam Long tourId,
                                           @RequestParam Long beneficiaryId,
                                           @RequestParam Long contributorId,
                                           @RequestParam java.math.BigDecimal amount,
                                           RedirectAttributes ra) {
        try {
            TontineContribution contrib = recordTontineContributionUseCase.execute(
                tourId, id, contributorId, beneficiaryId, amount);
            if (contrib.isPaid()) {
                ra.addFlashAttribute("successMessage",
                    "Cotisation de " + amount + " FCFA enregistrée.");
            } else {
                ra.addFlashAttribute("successMessage",
                    "Échec de cotisation enregistré.");
            }
            markBenefitedUseCase.execute(tourId, beneficiaryId);
        } catch (Exception e) {
            ra.addFlashAttribute("errorMessage", e.getMessage());
        }
        return "redirect:/sessions/" + id;
    }

    // ── Generate Report ─────────────────────────────────────

    @PostMapping("/{id}/report/generate")
    @PreAuthorize("hasAuthority('SESSION_CLOSE')")
    public String generateReport(@PathVariable Long id,
                                  @AuthenticationPrincipal UserDetailsImpl user,
                                  RedirectAttributes ra) {
        return transitionToNext(id, user, ra,
            "Rapport de séance généré.",
            "/sessions/" + id + "/report");
    }

    // ── View Report ─────────────────────────────────────────

    @GetMapping("/{id}/report")
    @PreAuthorize("hasAuthority('SESSION_VIEW')")
    public String report(@PathVariable Long id, Model model) {
        MeetingSessionEntity session = sessionService.findById(id);
        model.addAttribute("meetingSession", session);
        model.addAttribute("attendances",     sessionService.findAttendances(id));
        model.addAttribute("pageTitle",      "Rapport — " + session.getSessionDate());

        if (session.isStepAtLeast(SessionStep.PRESENCE_CLOSED)) {
            sessionReportRepository.findBySessionId(id).ifPresent(report -> {
                SessionCloseResult closeResult = buildResultFromReport(report, session);
                model.addAttribute("closeResult", closeResult);
                model.addAttribute("sessionReport", report);
            });
        }

        // ── Cashbox balances ────────────────────────────────
        var cashboxBalances = new java.util.LinkedHashMap<String, java.math.BigDecimal>();
        for (var cb : entityManager.createQuery(
                "SELECT c FROM CashboxEntity c",
                CashboxEntity.class)
                .getResultList()) {
            cashboxBalances.put(cb.getType().label(), cb.getBalance());
        }
        model.addAttribute("cashboxBalances", cashboxBalances);

        return "sessions/report";
    }

    // ── Helpers ──────────────────────────────────────────────

    private SessionCloseResult buildResultFromReport(SessionReportEntity report,
                                                      MeetingSessionEntity session) {
        Member beneficiary = null;
        if (session.getPresenceBeneficiary() != null) {
            try {
                beneficiary = searchMemberUseCase.findById(
                    session.getPresenceBeneficiary().getId());
            } catch (Exception e) {
                // fallback: null
            }
        }

        return SessionCloseResult.builder()
            .session(session)
            .beneficiary(beneficiary)
            .totalCotisants(report.getPresenceTotalCotisants())
            .presentCount(report.getPresencePresentCount())
            .fundCoveredCount(report.getPresenceFundCoveredCount())
            .defaultCount(report.getPresenceDefaultCount())
            .grossTontine(report.getPresenceGrossTontine())
            .sanctionDeductions(report.getPresenceSanctionDeductions())
            .netTontine(report.getPresenceNetTontine())
            .totalDevelopment(report.getPresenceDevelopmentTotal())
            .beverageReliquat(report.getPresenceBeverageReliquat())
            .attendanceResults(null)
            .build();
    }
}
