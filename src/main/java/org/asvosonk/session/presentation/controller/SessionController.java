package org.asvosonk.session.presentation.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.asvosonk.bank.application.usecase.CreateLoanUseCase;
import org.asvosonk.bank.application.usecase.GetMemberBankSummaryUseCase;
import org.asvosonk.bank.application.usecase.RecordLoanRepaymentUseCase;
import org.asvosonk.bank.application.usecase.RecordSavingUseCase;
import org.asvosonk.bank.presentation.request.LoanForm;
import org.asvosonk.bank.presentation.request.RepaymentForm;
import org.asvosonk.bank.presentation.request.SavingForm;
import org.asvosonk.bank.domain.repository.LoanRepository;
import org.asvosonk.bank.domain.repository.LoanRepaymentRepository;
import org.asvosonk.bank.domain.repository.SavingRepository;
import org.asvosonk.bank.domain.model.Loan;
import org.asvosonk.cashbox.application.usecase.DepositMoneyUseCase;
import org.asvosonk.cashbox.application.usecase.GenerateBalanceUseCase;
import org.asvosonk.cashbox.application.usecase.WithdrawMoneyUseCase;
import org.asvosonk.cashbox.domain.model.CashboxMovement;
import org.asvosonk.cashbox.domain.repository.CashboxMovementRepository;
import org.asvosonk.cashbox.domain.valueobject.CashboxType;
import org.asvosonk.cashbox.domain.valueobject.MovementDirection;
import org.asvosonk.cashbox.domain.valueobject.MovementOrigin;
import org.asvosonk.common.domain.exception.BusinessRuleException;
import org.asvosonk.common.domain.exception.ResourceNotFoundException;
import org.asvosonk.member.application.usecase.SearchMemberUseCase;
import org.asvosonk.member.domain.model.Member;
import org.asvosonk.member.infrastructure.persistence.repository.MembershipFeePaymentRepository;
import org.asvosonk.presence.application.usecase.GetPresenceTourSummaryUseCase;
import org.asvosonk.presence.domain.model.PresenceTour;
import org.asvosonk.presence.domain.model.PresenceTourParticipant;
import org.asvosonk.security.application.service.UserDetailsImpl;
import org.asvosonk.session.application.service.SessionService;
import org.asvosonk.session.application.service.SessionStepService;
import org.asvosonk.session.application.usecase.ComputePresenceFeeUseCase;
import org.asvosonk.session.application.usecase.GetRevolvingFundStatusUseCase;
import org.asvosonk.session.domain.valueobject.SessionStep;
import org.asvosonk.session.infrastructure.persistence.entity.MeetingSessionEntity;
import org.asvosonk.session.infrastructure.persistence.entity.SessionReportEntity;
import org.asvosonk.tontine.application.usecase.GetTourSummaryUseCase;
import org.asvosonk.tontine.application.usecase.RecordTontineContributionUseCase;
import org.asvosonk.tontine.domain.model.TontineContribution;
import org.asvosonk.tontine.domain.model.TontineParticipant;
import org.asvosonk.tontine.domain.model.TontineTour;
import org.asvosonk.session.infrastructure.persistence.repository.SessionReportRepository;
import org.asvosonk.session.presentation.request.AttendanceEntryForm;
import org.asvosonk.session.presentation.request.AttendanceSheetForm;
import org.asvosonk.session.presentation.request.SessionForm;
import org.asvosonk.session.presentation.request.SessionMovementForm;
import org.asvosonk.session.presentation.request.TontineSheetForm;
import org.asvosonk.session.presentation.response.SessionCloseResult;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Controller
@RequestMapping("/sessions")
@RequiredArgsConstructor
public class SessionController {

    private final SessionService                   sessionService;
    private final SessionStepService               sessionStepService;
    private final SearchMemberUseCase              searchMemberUseCase;
    private final SessionReportRepository          sessionReportRepository;
    private final GetRevolvingFundStatusUseCase    getRevolvingFundStatusUseCase;
    private final ComputePresenceFeeUseCase        computePresenceFeeUseCase;
    private final GenerateBalanceUseCase           generateBalanceUseCase;
    private final CashboxMovementRepository        cashboxMovementRepository;
    private final DepositMoneyUseCase              depositMoneyUseCase;
    private final WithdrawMoneyUseCase             withdrawMoneyUseCase;
    private final GetPresenceTourSummaryUseCase    getPresenceTourSummaryUseCase;
    private final GetTourSummaryUseCase            getTourSummaryUseCase;
    private final RecordTontineContributionUseCase recordTontineContributionUseCase;
    private final MembershipFeePaymentRepository   membershipFeePaymentRepository;
    private final RecordSavingUseCase              recordSavingUseCase;
    private final CreateLoanUseCase                createLoanUseCase;
    private final RecordLoanRepaymentUseCase       recordLoanRepaymentUseCase;
    private final GetMemberBankSummaryUseCase      getMemberBankSummaryUseCase;
    private final LoanRepository                   loanRepository;
    private final LoanRepaymentRepository          loanRepaymentRepository;
    private final SavingRepository                 savingRepository;

    // ── List ─────────────────────────────────────────────────

    @GetMapping
    @PreAuthorize("hasAuthority('SESSION_VIEW')")
    public String list(Model model) {
        var sessions = sessionService.findAll();

        Map<Long, Integer> stepIndexMap = new HashMap<>();
        for (var s : sessions) {
            stepIndexMap.put(s.getId(), s.getCurrentStepEnum().displayIndex());
        }

        model.addAttribute("sessions", sessions);
        model.addAttribute("displaySteps", SessionStep.DisplayStep.values());
        model.addAttribute("stepIndexMap", stepIndexMap);
        model.addAttribute("totalSteps", SessionStep.DisplayStep.values().length);
        model.addAttribute("pageTitle", "Séances");
        return "sessions/list";
    }

    // ── Detail (contenu de l'étape courante) ─────────────────

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('SESSION_VIEW')")
    public String detail(@PathVariable Long id, Model model) {
        MeetingSessionEntity session = sessionService.findById(id);
        SessionStep step = session.getCurrentStepEnum();

        model.addAttribute("meetingSession", session);
        model.addAttribute("attendances",    sessionService.findAttendancesSynchronized(id));
        model.addAttribute("currentStep",    step);
        model.addAttribute("displaySteps",   SessionStep.DisplayStep.values());
        model.addAttribute("currentDisplayStepIdx", step.displayIndex());
        model.addAttribute("isReportGenerated", session.isStepAtLeast(SessionStep.REPORT_GENERATED));

        // Détail chiffré de la séance, rubrique par rubrique : chaque étape
        // affiche les mêmes lignes que le rapport, dès qu'elles sont connues.
        var stepRecoveries = sessionService.findRecoveriesDuring(id);
        model.addAttribute("sessionRecoveries", stepRecoveries);
        model.addAttribute("recoveryNames", searchMemberUseCase.findNamesByIds(
            stepRecoveries.stream()
                .flatMap(r -> java.util.stream.Stream.of(r.memberId(), r.beneficiaryId()))
                .filter(java.util.Objects::nonNull)
                .distinct()
                .toList()));
        addSessionMovements(id, model);

        // ── Tour de présence : bénéficiaires éligibles au tirage ──
        PresenceTour openTour = getPresenceTourSummaryUseCase.findCurrentOpenTour();
        if (openTour != null) {
            model.addAttribute("presenceOpenTour", openTour);
            List<PresenceTourParticipant> eligible =
                getPresenceTourSummaryUseCase.findEligibleBeneficiaries(openTour.getId());
            model.addAttribute("eligibleBeneficiaries", eligible);
            model.addAttribute("eligibleNames", searchMemberUseCase.findNamesByIds(
                eligible.stream().map(PresenceTourParticipant::getMemberId).toList()));
        }

        // ── Rapport (dès la clôture de la présence) ──────────
        if (session.isStepAtLeast(SessionStep.PRESENCE_CLOSED)) {
            sessionReportRepository.findBySessionId(id).ifPresent(report -> {
                model.addAttribute("closeResult", buildResultFromReport(report, session));
                model.addAttribute("sessionReport", report);
            });
        }

        // ── Grande tontine : saisie en cours ─────────────────
        if (step == SessionStep.TONTINE_OPEN) {
            TontineTour tontineTour = getTourSummaryUseCase.findCurrentOpenTour();
            if (tontineTour != null) {
                List<TontineParticipant> participants =
                    getTourSummaryUseCase.findParticipantsByTourId(tontineTour.getId());
                List<TontineParticipant> notBenefited =
                    getTourSummaryUseCase.findNotBenefitedYet(tontineTour.getId());

                model.addAttribute("tontineTour", tontineTour);
                model.addAttribute("tontineParticipants", participants);
                if (!notBenefited.isEmpty()) {
                    model.addAttribute("tontineNextBeneficiary", notBenefited.get(0));
                    model.addAttribute("tontineDebts", getTourSummaryUseCase
                        .debtsOwedTo(tontineTour.getId(), notBenefited.get(0).getMemberId()));
                }
                model.addAttribute("tontineMemberNames", searchMemberUseCase.findNamesByIds(
                    participants.stream().map(TontineParticipant::getMemberId).distinct().toList()));

                List<TontineContribution> recorded = getTourSummaryUseCase.findContributionsBySessionId(id);
                model.addAttribute("tontineSessionContribs", recorded);
                // Lignes déjà saisies : affichées en lecture seule sur la feuille,
                // pour qu'un second envoi ne les rejoue pas.
                Map<Long, BigDecimal> recordedBy = new HashMap<>();
                recorded.forEach(c -> recordedBy.put(c.getContributorId(), c.getAmount()));
                model.addAttribute("tontineRecordedBy", recordedBy);
            }
        }

        // ── Grande tontine : récapitulatif après clôture ─────
        if (session.isStepAtLeast(SessionStep.TONTINE_CLOSED)) {
            List<TontineContribution> contributions =
                getTourSummaryUseCase.findContributionsBySessionId(id);
            if (!contributions.isEmpty()) {
                model.addAttribute("tontineSessionContribs", contributions);
                model.addAttribute("tontineSessionTotal", contributions.stream()
                    .filter(TontineContribution::isPaid)
                    .map(TontineContribution::getAmount)
                    .reduce(BigDecimal.ZERO, BigDecimal::add));
                model.addAttribute("tontineSessionDefaults", contributions.stream()
                    .filter(c -> !c.isPaid()).count());
                model.addAttribute("tontineMemberNames", namesOfContributions(contributions));
            }
        }

        // ── Banque Annuelle : saisie en cours ────────────────
        if (step == SessionStep.BANQUE_ANNUELLE_OPEN) {
            // Seuls les membres actifs : épargner et emprunter leur sont réservés
            // (RecordSavingUseCase / CreateLoanUseCase rejettent les autres), inutile
            // de leur proposer un bouton qui n'aboutira jamais.
            List<Member> bankMembers = searchMemberUseCase.findAllActive();
            Map<Long, GetMemberBankSummaryUseCase.MemberBankSummary> bankSummaries = new HashMap<>();
            for (Member m : bankMembers) {
                bankSummaries.put(m.getId(), getMemberBankSummaryUseCase.execute(m.getId()));
            }
            model.addAttribute("bankMembers", bankMembers);
            model.addAttribute("bankSummaries", bankSummaries);
            model.addAttribute("savingForm", new SavingForm());
            model.addAttribute("loanForm", new LoanForm());
            model.addAttribute("repaymentForm", new RepaymentForm());

            // Emprunts remboursables : tout emprunt encore dû, en retard compris.
            // Filtrer sur le seul statut « actif » rendait un emprunt en retard
            // irremboursable en séance, alors que la page Banque Annuelle et
            // RecordLoanRepaymentUseCase l'acceptent tous deux.
            List<Loan> outstandingLoans = loanRepository.findOutstanding();
            model.addAttribute("activeBankLoans", outstandingLoans);
            Map<Long, BigDecimal> remainingByLoan = new HashMap<>();
            for (Loan loan : outstandingLoans) {
                BigDecimal repaid = loanRepaymentRepository.getTotalRepaidByLoanId(loan.getId());
                remainingByLoan.put(loan.getId(), loan.getRemainingBalance(repaid));
            }
            model.addAttribute("remainingByLoan", remainingByLoan);

            // Les emprunts peuvent appartenir à un membre devenu inactif : leurs
            // noms sont résolus à part, sinon la ligne s'afficherait sans membre.
            List<Long> nameIds = new ArrayList<>(bankMembers.stream().map(Member::getId).toList());
            outstandingLoans.forEach(l -> nameIds.add(l.getMemberId()));
            model.addAttribute("bankMemberNames", searchMemberUseCase.findNamesByIds(nameIds));

            // Chiffres courants de la rubrique : ce sont eux que la clôture figera.
            model.addAttribute("bankSessionSavings", savingRepository.getTotalSavingsBySessionId(id));
            model.addAttribute("bankSessionRepayments", loanRepaymentRepository.getTotalRepaidBySessionId(id));
            model.addAttribute("bankSessionLoans", loanRepository.getTotalLoanedBySessionId(id));
        }

        // ── Entrées et sorties diverses de la séance ─────────
        addSessionMovements(id, model);
        model.addAttribute("movementForm", new SessionMovementForm());
        model.addAttribute("cashboxTypes", CashboxType.values());

        model.addAttribute("pageTitle", "Séance du " + session.getSessionDate());
        return "sessions/detail";
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
            model.addAttribute("pageTitle", "Nouvelle séance");
            return "sessions/form";
        }
    }

    // ── Bénéficiaire de la tontine de présence ───────────────

    /**
     * Enregistre le résultat du tirage au sort effectué en séance. Le bénéficiaire
     * doit faire partie des membres éligibles du tour en cours (pas encore servis,
     * et arrivants en cours de tour seulement une fois les autres servis).
     */
    @PostMapping("/{id}/presence/beneficiary")
    @PreAuthorize("hasAuthority('ATTENDANCE_RECORD')")
    public String designateBeneficiary(@PathVariable Long id,
                                       @RequestParam Long memberId,
                                       RedirectAttributes ra) {
        PresenceTour openTour = getPresenceTourSummaryUseCase.findCurrentOpenTour();
        if (openTour == null) {
            ra.addFlashAttribute("errorMessage",
                "Aucun tour de présence ouvert : impossible de désigner un bénéficiaire.");
            return "redirect:/sessions/" + id;
        }

        boolean eligible = getPresenceTourSummaryUseCase.findEligibleBeneficiaries(openTour.getId())
            .stream().anyMatch(p -> p.getMemberId().equals(memberId));
        if (!eligible) {
            ra.addFlashAttribute("errorMessage",
                "Ce membre ne peut pas bénéficier : il a déjà été servi dans ce tour, "
              + "ou des membres présents au démarrage du tour attendent encore leur tour.");
            return "redirect:/sessions/" + id;
        }

        sessionService.setBeneficiary(id, memberId);
        ra.addFlashAttribute("successMessage",
            "Bénéficiaire de la tontine de présence : "
                + searchMemberUseCase.findById(memberId).getFullName() + ".");
        return "redirect:/sessions/" + id;
    }

    // ── Feuille de présence ──────────────────────────────────

    @GetMapping("/{id}/attendance")
    @PreAuthorize("hasAuthority('ATTENDANCE_RECORD')")
    public String attendanceForm(@PathVariable Long id, Model model) {
        MeetingSessionEntity session = sessionService.findById(id);
        if (!session.isStepExactly(SessionStep.PRESENCE_OPEN)) {
            return "redirect:/sessions/" + id;
        }

        // Un membre inscrit pendant la réunion doit apparaître sur la feuille :
        // elle se réaligne sur les membres actifs à chaque affichage.
        var attendances = sessionService.findAttendancesSynchronized(id);
        model.addAttribute("meetingSession", session);
        model.addAttribute("attendances", attendances);

        // Le formulaire reprend les lignes affichées, dans le même ordre :
        // les champs indexés (entries[i].*) se lient ligne à ligne.
        AttendanceSheetForm sheetForm = new AttendanceSheetForm();
        attendances.forEach(att -> {
            AttendanceEntryForm entry = new AttendanceEntryForm();
            entry.setMemberId(att.getMember().getId());
            entry.setPresent(att.isPresent());
            entry.setAmountPaid(att.getAmountPaid());
            sheetForm.getEntries().add(entry);
        });
        model.addAttribute("sheetForm", sheetForm);

        // Bénéficiaire du jour et montants dus (1 000 FCFA pour les membres qui
        // avaient déjà bénéficié avant l'arrivée d'un bénéficiaire récent).
        PresenceTour openTour = getPresenceTourSummaryUseCase.findCurrentOpenTour();
        Map<Long, BigDecimal> fees = Map.of();
        if (session.getPresenceBeneficiary() != null) {
            model.addAttribute("beneficiaryMember",
                searchMemberUseCase.findById(session.getPresenceBeneficiary().getId()));
            if (openTour != null) {
                fees = computePresenceFeeUseCase.feesByMember(
                    openTour.getId(), session.getPresenceBeneficiary().getId());
            }
        }
        Map<Long, BigDecimal> dueByMember = new HashMap<>();
        for (var att : attendances) {
            dueByMember.put(att.getMember().getId(),
                computePresenceFeeUseCase.feeFor(fees, att.getMember().getId()));
        }
        model.addAttribute("dueByMember", dueByMember);
        model.addAttribute("presenceOpenTour", openTour);

        // Plafond de saisie : cotisation du jour + dettes. On ne donne jamais
        // plus que son dû ; la borne est posée sur le champ et revérifiée
        // à l'enregistrement.
        Map<Long, BigDecimal> ceilingByMember = new HashMap<>();
        for (var att : attendances) {
            ceilingByMember.put(att.getMember().getId(),
                sessionService.presenceCeiling(id, att.getMember().getId()));
        }
        model.addAttribute("ceilingByMember", ceilingByMember);

        model.addAttribute("fundStatuses", getRevolvingFundStatusUseCase.findAllByMemberId());
        model.addAttribute("openFailures", getRevolvingFundStatusUseCase.openFailuresByMember());

        // Détail chiffré des dettes, dans l'ordre où la clôture les imputera :
        // il permet à la feuille d'annoncer l'effet de chaque montant saisi.
        var debtsByMember = getRevolvingFundStatusUseCase.outstandingDebtsByMember();
        Map<Long, String> debtTokens = new HashMap<>();
        for (var att : attendances) {
            debtTokens.put(att.getMember().getId(), GetRevolvingFundStatusUseCase.debtTokens(
                debtsByMember.get(att.getMember().getId())));
        }
        model.addAttribute("debtTokens", debtTokens);

        model.addAttribute("displaySteps", SessionStep.DisplayStep.values());
        model.addAttribute("currentDisplayStepIdx", session.getCurrentStepEnum().displayIndex());
        model.addAttribute("currentStep", session.getCurrentStepEnum());
        model.addAttribute("pageTitle", "Saisie — " + session.getSessionDate());
        return "sessions/attendance";
    }

    @PostMapping("/{id}/attendance")
    @PreAuthorize("hasAuthority('ATTENDANCE_RECORD')")
    public String saveAttendanceSheet(@PathVariable Long id,
                                      @Valid @ModelAttribute("sheetForm") AttendanceSheetForm sheetForm,
                                      BindingResult result,
                                      RedirectAttributes ra) {
        if (result.hasErrors()) {
            ra.addFlashAttribute("errorMessage",
                "Feuille non enregistrée : un montant saisi est invalide (il doit être positif ou nul).");
            return "redirect:/sessions/" + id + "/attendance";
        }

        int saved;
        try {
            saved = sessionService.saveAttendanceSheet(id, sheetForm.getEntries());
        } catch (BusinessRuleException e) {
            // Un montant hors limite annule tout l'enregistrement : la feuille
            // reste telle qu'elle était, sans lignes à moitié saisies.
            ra.addFlashAttribute("errorMessage", e.getMessage());
            return "redirect:/sessions/" + id + "/attendance";
        }
        ra.addFlashAttribute("successMessage",
            "Feuille de présence enregistrée (" + saved + " membre(s)).");
        return "redirect:/sessions/" + id + "/attendance";
    }

    // ── Transitions d'étape ──────────────────────────────────

    private String transitionToNext(Long id, UserDetailsImpl user,
                                    RedirectAttributes ra, String successMsg,
                                    String errorRedirect, SessionStep expectedCurrent) {
        try {
            sessionStepService.transitionToNext(id, user.getAppUser(), expectedCurrent);
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
        return transitionToNext(id, user, ra, "Saisie de présence ouverte.",
            "/sessions/" + id, SessionStep.CREATED);
    }

    @PostMapping("/{id}/presence/close")
    @PreAuthorize("hasAuthority('SESSION_CLOSE')")
    public String closePresence(@PathVariable Long id,
                                @AuthenticationPrincipal UserDetailsImpl user,
                                RedirectAttributes ra) {
        return transitionToNext(id, user, ra, "Présence clôturée : les calculs ont été effectués.",
            "/sessions/" + id + "/attendance", SessionStep.PRESENCE_OPEN);
    }

    @PostMapping("/{id}/tontine/open")
    @PreAuthorize("hasAuthority('TONTINE_VIEW')")
    public String openTontine(@PathVariable Long id,
                              @AuthenticationPrincipal UserDetailsImpl user,
                              RedirectAttributes ra) {
        return transitionToNext(id, user, ra, "Grande tontine ouverte.",
            "/sessions/" + id, SessionStep.PRESENCE_CLOSED);
    }

    @PostMapping("/{id}/tontine/close")
    @PreAuthorize("hasAuthority('TONTINE_TOUR_CREATE')")
    public String closeTontine(@PathVariable Long id,
                               @AuthenticationPrincipal UserDetailsImpl user,
                               RedirectAttributes ra) {
        return transitionToNext(id, user, ra, "Grande tontine clôturée.",
            "/sessions/" + id, SessionStep.TONTINE_OPEN);
    }

    @PostMapping("/{id}/banque-projet/open")
    @PreAuthorize("hasAuthority('SESSION_VIEW')")
    public String openBanqueProjet(@PathVariable Long id,
                                   @AuthenticationPrincipal UserDetailsImpl user,
                                   RedirectAttributes ra) {
        return transitionToNext(id, user, ra, "Banque Projet ouverte.",
            "/sessions/" + id, SessionStep.TONTINE_CLOSED);
    }

    @PostMapping("/{id}/banque-projet/close")
    @PreAuthorize("hasAuthority('SESSION_CLOSE')")
    public String closeBanqueProjet(@PathVariable Long id,
                                    @AuthenticationPrincipal UserDetailsImpl user,
                                    RedirectAttributes ra) {
        return transitionToNext(id, user, ra, "Banque Projet clôturée.",
            "/sessions/" + id, SessionStep.BANQUE_PROJET_OPEN);
    }

    @PostMapping("/{id}/banque-annuelle/open")
    @PreAuthorize("hasAuthority('SESSION_VIEW')")
    public String openBanqueAnnuelle(@PathVariable Long id,
                                     @AuthenticationPrincipal UserDetailsImpl user,
                                     RedirectAttributes ra) {
        return transitionToNext(id, user, ra, "Banque Annuelle ouverte.",
            "/sessions/" + id, SessionStep.BANQUE_PROJET_CLOSED);
    }

    @PostMapping("/{id}/banque-annuelle/close")
    @PreAuthorize("hasAuthority('SESSION_CLOSE')")
    public String closeBanqueAnnuelle(@PathVariable Long id,
                                      @AuthenticationPrincipal UserDetailsImpl user,
                                      RedirectAttributes ra) {
        return transitionToNext(id, user, ra, "Banque Annuelle clôturée.",
            "/sessions/" + id, SessionStep.BANQUE_ANNUELLE_OPEN);
    }

    @PostMapping("/{id}/report/generate")
    @PreAuthorize("hasAuthority('SESSION_CLOSE')")
    public String generateReport(@PathVariable Long id,
                                 @AuthenticationPrincipal UserDetailsImpl user,
                                 RedirectAttributes ra) {
        return transitionToNext(id, user, ra, "Rapport de séance généré.",
            "/sessions/" + id, SessionStep.BANQUE_ANNUELLE_CLOSED);
    }

    // ── Cotisation de grande tontine ─────────────────────────

    @PostMapping("/{id}/tontine/contribute")
    @PreAuthorize("hasAuthority('TONTINE_CONTRIBUTION_RECORD')")
    public String saveTontineContribution(@PathVariable Long id,
                                          @RequestParam Long tourId,
                                          @RequestParam Long beneficiaryId,
                                          @RequestParam Long contributorId,
                                          @RequestParam BigDecimal amount,
                                          RedirectAttributes ra) {
        try {
            TontineContribution contrib = recordTontineContributionUseCase.execute(
                tourId, id, contributorId, beneficiaryId, amount);
            ra.addFlashAttribute("successMessage", contrib.isPaid()
                ? "Cotisation de " + amount + " FCFA enregistrée."
                : "Échec de cotisation enregistré.");
        } catch (BusinessRuleException | ResourceNotFoundException | IllegalArgumentException e) {
            ra.addFlashAttribute("errorMessage", e.getMessage());
        }
        return "redirect:/sessions/" + id;
    }

    /**
     * Enregistre la feuille de cotisation de la grande tontine en une fois.
     *
     * <p>Les lignes laissées vides sont ignorées : la feuille peut se remplir en
     * plusieurs passages. Une ligne déjà enregistrée est remplacée : l'ancienne
     * cotisation et ses effets (dettes, sanctions) sont annulés avant
     * ré-enregistrement. Chaque ligne refusée est signalée nommément.</p>
     */
    @PostMapping("/{id}/tontine/sheet")
    @PreAuthorize("hasAuthority('TONTINE_CONTRIBUTION_RECORD')")
    public String saveTontineSheet(@PathVariable Long id,
                                   @ModelAttribute("tontineSheetForm") TontineSheetForm sheetForm,
                                   RedirectAttributes ra) {
        if (sheetForm.getTourId() == null || sheetForm.getBeneficiaryId() == null) {
            ra.addFlashAttribute("errorMessage",
                "Feuille non enregistrée : tour ou bénéficiaire manquant.");
            return "redirect:/sessions/" + id;
        }

        int saved = 0;
        List<String> refused = new ArrayList<>();
        for (TontineSheetForm.Entry entry : sheetForm.getEntries()) {
            if (entry == null || entry.getContributorId() == null || entry.getAmount() == null) {
                continue;
            }
            try {
                recordTontineContributionUseCase.replaceContribution(sheetForm.getTourId(), id,
                    entry.getContributorId(), sheetForm.getBeneficiaryId(), entry.getAmount());
                saved++;
            } catch (BusinessRuleException | ResourceNotFoundException | IllegalArgumentException e) {
                refused.add(searchMemberUseCase.findById(entry.getContributorId()).getFullName()
                    + " : " + e.getMessage());
            }
        }

        if (!refused.isEmpty()) {
            ra.addFlashAttribute("errorMessage",
                (saved > 0 ? saved + " cotisation(s) enregistrée(s). " : "")
                    + "Ligne(s) refusée(s) — " + String.join(" · ", refused));
        } else {
            ra.addFlashAttribute("successMessage",
                saved > 0 ? saved + " cotisation(s) enregistrée(s)."
                          : "Aucune ligne à enregistrer : tous les montants étaient vides.");
        }
        return "redirect:/sessions/" + id;
    }

    // ── Banque Annuelle : opérations de séance ──────────────

    /**
     * Séance ouverte à la saisie Banque Annuelle, ou null si la rubrique n'est
     * pas (ou plus) en cours.
     *
     * <p>La clôture de l'étape fige les épargnes, remboursements et emprunts de
     * la séance dans le rapport. Accepter une opération après coup rattacherait
     * de l'argent à une rubrique déjà arrêtée : les totaux figés ne
     * correspondraient plus aux lignes réellement enregistrées.</p>
     */
    private MeetingSessionEntity requireBanqueAnnuelleOpen(Long id, RedirectAttributes ra) {
        MeetingSessionEntity session = sessionService.findById(id);
        if (!session.isStepExactly(SessionStep.BANQUE_ANNUELLE_OPEN)) {
            ra.addFlashAttribute("errorMessage",
                "La Banque Annuelle de cette séance n'est pas ouverte à la saisie (étape courante : "
                    + session.getCurrentStepEnum().label() + ").");
            return null;
        }
        return session;
    }

    /** Premier message de validation du formulaire, pour ne pas le perdre au redirect. */
    private String firstError(BindingResult result, String fallback) {
        return result.getFieldError() != null && result.getFieldError().getDefaultMessage() != null
            ? result.getFieldError().getDefaultMessage()
            : fallback;
    }

    @PostMapping("/{id}/banque-annuelle/save")
    @PreAuthorize("hasAuthority('BANK_SAVING_RECORD')")
    public String recordSessionSaving(@PathVariable Long id,
                                      @RequestParam Long memberId,
                                      @Valid @ModelAttribute("savingForm") SavingForm form,
                                      BindingResult result,
                                      @AuthenticationPrincipal UserDetailsImpl principal,
                                      RedirectAttributes ra) {
        if (result.hasErrors()) {
            ra.addFlashAttribute("errorMessage",
                "Épargne non enregistrée : " + firstError(result, "montant invalide."));
            return "redirect:/sessions/" + id;
        }
        MeetingSessionEntity session = requireBanqueAnnuelleOpen(id, ra);
        if (session == null) {
            return "redirect:/sessions/" + id;
        }
        try {
            LocalDate date = form.getOperationDate() != null ? form.getOperationDate() : LocalDate.now();
            recordSavingUseCase.execute(memberId, form.getAmount(), date,
                session, principal.getAppUser());
            ra.addFlashAttribute("successMessage",
                "Épargne de " + form.getAmount() + " FCFA enregistrée.");
        } catch (BusinessRuleException e) {
            ra.addFlashAttribute("errorMessage", e.getMessage());
        }
        return "redirect:/sessions/" + id;
    }

    @PostMapping("/{id}/banque-annuelle/loan")
    @PreAuthorize("hasAuthority('BANK_LOAN_CREATE')")
    public String recordSessionLoan(@PathVariable Long id,
                                    @RequestParam Long memberId,
                                    @Valid @ModelAttribute("loanForm") LoanForm form,
                                    BindingResult result,
                                    @AuthenticationPrincipal UserDetailsImpl principal,
                                    RedirectAttributes ra) {
        if (result.hasErrors()) {
            ra.addFlashAttribute("errorMessage",
                "Emprunt non enregistré : " + firstError(result, "montant invalide."));
            return "redirect:/sessions/" + id;
        }
        MeetingSessionEntity session = requireBanqueAnnuelleOpen(id, ra);
        if (session == null) {
            return "redirect:/sessions/" + id;
        }
        try {
            Loan loan = createLoanUseCase.execute(memberId, form.getAmount(),
                session, principal.getAppUser());
            ra.addFlashAttribute("successMessage",
                "Emprunt de " + form.getAmount() + " FCFA créé (total dû : " + loan.getTotalDue() + " FCFA).");
        } catch (BusinessRuleException e) {
            ra.addFlashAttribute("errorMessage", e.getMessage());
        }
        return "redirect:/sessions/" + id;
    }

    @PostMapping("/{id}/banque-annuelle/repay")
    @PreAuthorize("hasAuthority('BANK_LOAN_REPAYMENT')")
    public String recordSessionRepayment(@PathVariable Long id,
                                         @RequestParam Long loanId,
                                         @Valid @ModelAttribute("repaymentForm") RepaymentForm form,
                                         BindingResult result,
                                         @AuthenticationPrincipal UserDetailsImpl principal,
                                         RedirectAttributes ra) {
        if (result.hasErrors()) {
            ra.addFlashAttribute("errorMessage",
                "Remboursement non enregistré : " + firstError(result, "montant invalide."));
            return "redirect:/sessions/" + id;
        }
        MeetingSessionEntity session = requireBanqueAnnuelleOpen(id, ra);
        if (session == null) {
            return "redirect:/sessions/" + id;
        }
        try {
            recordLoanRepaymentUseCase.execute(loanId, form.getAmount(),
                session, principal.getAppUser());
            ra.addFlashAttribute("successMessage",
                "Remboursement de " + form.getAmount() + " FCFA enregistré.");
        } catch (BusinessRuleException | ResourceNotFoundException e) {
            ra.addFlashAttribute("errorMessage", e.getMessage());
        }
        return "redirect:/sessions/" + id;
    }

    // ── Entrées / sorties de caisse de la séance ─────────────

    /**
     * Enregistre un mouvement de caisse rattaché à la séance : un don reçu, une
     * dépense décidée en séance… Ces mouvements alimentent les entrées et sorties
     * du jour dans le rapport, et donc le total remis au trésorier.
     */
    @PostMapping("/{id}/movements")
    @PreAuthorize("hasAuthority('CASHBOX_MANUAL_MOVEMENT')")
    public String recordMovement(@PathVariable Long id,
                                 @Valid @ModelAttribute("movementForm") SessionMovementForm form,
                                 BindingResult result,
                                 @AuthenticationPrincipal UserDetailsImpl principal,
                                 RedirectAttributes ra) {
        if (result.hasErrors()) {
            ra.addFlashAttribute("errorMessage",
                "Mouvement non enregistré : " + (result.getFieldError() != null
                    ? result.getFieldError().getDefaultMessage()
                    : "données invalides."));
            return "redirect:/sessions/" + id;
        }

        MeetingSessionEntity session = sessionService.findById(id);
        if (session.isStepAtLeast(SessionStep.REPORT_GENERATED)) {
            ra.addFlashAttribute("errorMessage",
                "Le rapport de cette séance est déjà généré : plus aucun mouvement ne peut y être rattaché.");
            return "redirect:/sessions/" + id;
        }

        try {
            if (form.getDirection() == MovementDirection.in) {
                depositMoneyUseCase.execute(form.getCashbox(), form.getAmount(), form.getReason(),
                    MovementOrigin.manual, session, null, null, principal.getAppUser());
            } else {
                withdrawMoneyUseCase.execute(form.getCashbox(), form.getAmount(), form.getReason(),
                    MovementOrigin.manual, session, null, null, principal.getAppUser());
            }
            ra.addFlashAttribute("successMessage", "Mouvement enregistré et rattaché à la séance.");
        } catch (BusinessRuleException e) {
            ra.addFlashAttribute("errorMessage", e.getMessage());
        }
        return "redirect:/sessions/" + id;
    }

    // ── Rapport ──────────────────────────────────────────────

    @GetMapping("/{id}/report")
    @PreAuthorize("hasAuthority('SESSION_VIEW')")
    public String report(@PathVariable Long id, Model model) {
        MeetingSessionEntity session = sessionService.findById(id);
        model.addAttribute("meetingSession", session);
        model.addAttribute("attendances", sessionService.findAttendances(id));
        model.addAttribute("displaySteps", SessionStep.DisplayStep.values());
        model.addAttribute("currentDisplayStepIdx", session.getCurrentStepEnum().displayIndex());
        model.addAttribute("currentStep", session.getCurrentStepEnum());
        model.addAttribute("pageTitle", "Rapport — " + session.getSessionDate());

        if (session.isStepAtLeast(SessionStep.PRESENCE_CLOSED)) {
            sessionReportRepository.findBySessionId(id).ifPresent(report -> {
                model.addAttribute("closeResult", buildResultFromReport(report, session));
                model.addAttribute("sessionReport", report);
                if (report.getTontineBeneficiaryId() != null) {
                    model.addAttribute("tontineBeneficiaryName", searchMemberUseCase
                        .findNamesByIds(List.of(report.getTontineBeneficiaryId()))
                        .get(report.getTontineBeneficiaryId()));
                }
            });
        }

        List<TontineContribution> contributions = getTourSummaryUseCase.findContributionsBySessionId(id);
        if (!contributions.isEmpty()) {
            model.addAttribute("tontineContribs", contributions);
            model.addAttribute("tontineSessionTotal", contributions.stream()
                .filter(TontineContribution::isPaid)
                .map(TontineContribution::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add));
            model.addAttribute("tontineMemberNames", namesOfContributions(contributions));
        }

        // Rattrapages d'échecs recouverts pendant cette séance
        var recoveries = sessionService.findRecoveriesDuring(id);
        model.addAttribute("recoveries", recoveries);
        model.addAttribute("recoveryNames", searchMemberUseCase.findNamesByIds(
            recoveries.stream()
                .flatMap(r -> java.util.stream.Stream.of(r.memberId(), r.beneficiaryId()))
                .filter(java.util.Objects::nonNull)
                .distinct()
                .toList()));

        addSessionMovements(id, model);
        model.addAttribute("cashboxTypes", CashboxType.values());
        model.addAttribute("balances", generateBalanceUseCase.execute());

        return "sessions/report";
    }

    // ── Helpers ──────────────────────────────────────────────

    /** Mouvements de caisse rattachés à la séance, séparés en entrées et sorties. */
    private void addSessionMovements(Long sessionId, Model model) {
        model.addAttribute("feePayments", membershipFeePaymentRepository.findBySessionId(sessionId));
        List<CashboxMovement> movements =
            cashboxMovementRepository.findBySessionIdOrderByMovementDateAsc(sessionId);
        List<CashboxMovement> inflows = movements.stream()
            .filter(m -> m.getDirection() == MovementDirection.in).toList();
        List<CashboxMovement> outflows = movements.stream()
            .filter(m -> m.getDirection() == MovementDirection.out).toList();

        model.addAttribute("sessionInflows", inflows);
        model.addAttribute("sessionOutflows", outflows);
        model.addAttribute("sessionInflowTotal", inflows.stream()
            .map(CashboxMovement::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add));
        model.addAttribute("sessionOutflowTotal", outflows.stream()
            .map(CashboxMovement::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add));
        model.addAttribute("manualInflows", inflows.stream()
            .filter(m -> m.getOrigin() == MovementOrigin.manual).toList());
    }

    private Map<Long, String> namesOfContributions(List<TontineContribution> contributions) {
        List<Long> ids = new ArrayList<>();
        contributions.forEach(c -> {
            ids.add(c.getContributorId());
            ids.add(c.getBeneficiaryId());
        });
        return searchMemberUseCase.findNamesByIds(ids);
    }

    private SessionCloseResult buildResultFromReport(SessionReportEntity report,
                                                     MeetingSessionEntity session) {
        Member beneficiary = null;
        if (session.getPresenceBeneficiary() != null) {
            try {
                beneficiary = searchMemberUseCase.findById(session.getPresenceBeneficiary().getId());
            } catch (IllegalArgumentException notFound) {
                beneficiary = null;
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
            .returnToFund(report.getPresenceReturnToFund())
            .recoveryTotal(report.getPresenceRecoveryTotal())
            .fundCatchUp(report.getPresenceFundCatchUp())
            .attendanceResults(null)
            .build();
    }
}
