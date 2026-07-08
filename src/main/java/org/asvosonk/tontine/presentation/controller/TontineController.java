package org.asvosonk.tontine.presentation.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.asvosonk.member.application.usecase.SearchMemberUseCase;
import org.asvosonk.tontine.application.usecase.CloseTourUseCase;
import org.asvosonk.tontine.application.usecase.CreateTourUseCase;
import org.asvosonk.tontine.application.usecase.GetTourSummaryUseCase;
import org.asvosonk.tontine.application.usecase.MarkBenefitedUseCase;
import org.asvosonk.tontine.application.usecase.RecordTontineContributionUseCase;
import org.asvosonk.tontine.domain.model.TontineContribution;
import org.asvosonk.tontine.domain.model.TontineDebt;
import org.asvosonk.tontine.domain.model.TontineParticipant;
import org.asvosonk.tontine.domain.model.TontineTour;
import org.asvosonk.session.domain.valueobject.SessionStatus;
import org.asvosonk.session.infrastructure.persistence.entity.MeetingSessionEntity;
import org.asvosonk.session.infrastructure.persistence.repository.SpringDataMeetingSessionRepository;
import org.asvosonk.tontine.presentation.request.ContributionForm;
import org.asvosonk.tontine.presentation.request.CreateTourForm;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Controller
@RequestMapping("/tontine")
@RequiredArgsConstructor
public class TontineController {

    private final GetTourSummaryUseCase               getTourSummaryUseCase;
    private final CreateTourUseCase                   createTourUseCase;
    private final RecordTontineContributionUseCase     recordContributionUseCase;
    private final MarkBenefitedUseCase                markBenefitedUseCase;
    private final CloseTourUseCase                    closeTourUseCase;
    private final SearchMemberUseCase                 searchMemberUseCase;
    private final SpringDataMeetingSessionRepository  meetingSessionRepository;

    // ── List tours ───────────────────────────────────────────

    @GetMapping
    @PreAuthorize("hasAuthority('TONTINE_VIEW')")
    public String list(Model model) {
        List<TontineTour> tours = getTourSummaryUseCase.findAllTours();
        TontineTour currentOpen = getTourSummaryUseCase.findCurrentOpenTour();

        if (currentOpen != null) {
            List<TontineParticipant> participants = getTourSummaryUseCase
                .findParticipantsByTourId(currentOpen.getId());
            BigDecimal totalCollected = getTourSummaryUseCase
                .calculateTotalCollected(currentOpen.getId());

            Map<Long, String> memberNames = participants.stream()
                .map(TontineParticipant::getMemberId)
                .distinct()
                .collect(Collectors.toMap(
                    Function.identity(),
                    mid -> searchMemberUseCase.findById(mid).getFullName()
                ));

            model.addAttribute("openTour", currentOpen);
            model.addAttribute("openParticipants", participants);
            model.addAttribute("openTotalCollected", totalCollected);
            model.addAttribute("memberNames", memberNames);
        }

        model.addAttribute("tours", tours);
        model.addAttribute("pageTitle", "Grande Tontine");
        return "tontine/list";
    }

    // ── Create tour form ─────────────────────────────────────

    @GetMapping("/new")
    @PreAuthorize("hasAuthority('TONTINE_TOUR_CREATE')")
    public String newTourForm(Model model) {
        model.addAttribute("form", new CreateTourForm());
        model.addAttribute("members", searchMemberUseCase.findAllActive());
        model.addAttribute("pageTitle", "Nouveau tour de grande tontine");
        return "tontine/form";
    }

    @PostMapping("/new")
    @PreAuthorize("hasAuthority('TONTINE_TOUR_CREATE')")
    public String createTour(@Valid @ModelAttribute("form") CreateTourForm form,
                             BindingResult result,
                             Model model,
                             RedirectAttributes ra) {
        if (result.hasErrors()) {
            model.addAttribute("members", searchMemberUseCase.findAllActive());
            model.addAttribute("pageTitle", "Nouveau tour de grande tontine");
            return "tontine/form";
        }

        try {
            TontineTour tour = createTourUseCase.execute(
                form.getStartDate(), form.getParticipantIds(), form.getDrawOrders());
            ra.addFlashAttribute("successMessage",
                "Tour de grande tontine créé avec " + form.getParticipantIds().size() + " participants.");
            return "redirect:/tontine/" + tour.getId();
        } catch (Exception e) {
            ra.addFlashAttribute("errorMessage", e.getMessage());
            return "redirect:/tontine/new";
        }
    }

    // ── Tour detail ──────────────────────────────────────────

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('TONTINE_VIEW')")
    public String detail(@PathVariable Long id, Model model) {
        TontineTour tour = getTourSummaryUseCase.findTourById(id);
        List<TontineParticipant> participants = getTourSummaryUseCase
            .findParticipantsByTourId(id);
        var contributions = getTourSummaryUseCase.findContributionsByTourId(id);
        var owedDebts = getTourSummaryUseCase.findOwedDebtsByTourId(id);
        boolean allBenefited = getTourSummaryUseCase.allParticipantsBenefited(id);

        // Build member name lookup for participants, debtors, creditors, contributors
        Map<Long, String> memberNames = participants.stream()
            .map(TontineParticipant::getMemberId)
            .distinct()
            .collect(Collectors.toMap(
                Function.identity(),
                mid -> searchMemberUseCase.findById(mid).getFullName()
            ));
        // Add names for debtors/creditors that might not be participants
        for (var d : owedDebts) {
            memberNames.putIfAbsent(d.getDebtorId(),
                searchMemberUseCase.findById(d.getDebtorId()).getFullName());
            memberNames.putIfAbsent(d.getCreditorId(),
                searchMemberUseCase.findById(d.getCreditorId()).getFullName());
        }
        // Add names for contributors/beneficiaries that might not be current participants
        for (var c : contributions) {
            memberNames.putIfAbsent(c.getContributorId(),
                searchMemberUseCase.findById(c.getContributorId()).getFullName());
            memberNames.putIfAbsent(c.getBeneficiaryId(),
                searchMemberUseCase.findById(c.getBeneficiaryId()).getFullName());
        }

        model.addAttribute("tour", tour);
        model.addAttribute("participants", participants);
        model.addAttribute("contributions", contributions);
        model.addAttribute("owedDebts", owedDebts);
        model.addAttribute("allBenefited", allBenefited);
        model.addAttribute("memberNames", memberNames);
        model.addAttribute("pageTitle", "Tour #" + tour.getId() + " — Grande Tontine");
        return "tontine/tour-detail";
    }

    // ── Contribution form ────────────────────────────────────

    @GetMapping("/{id}/contribute")
    @PreAuthorize("hasAuthority('TONTINE_CONTRIBUTION_RECORD')")
    public String contributionForm(@PathVariable Long id, Model model) {
        TontineTour tour = getTourSummaryUseCase.findTourById(id);

        if (!tour.isOpen()) {
            return "redirect:/tontine/" + id;
        }

        List<TontineParticipant> participants = getTourSummaryUseCase
            .findParticipantsByTourId(id);
        List<TontineParticipant> notBenefitedYet = getTourSummaryUseCase
            .findNotBenefitedYet(id);

        // Load the current open session so the contribution form can link to it
        MeetingSessionEntity openSession = meetingSessionRepository
            .findByStatusOrderBySessionDateDesc(SessionStatus.open)
            .stream()
            .findFirst()
            .orElse(null);
        model.addAttribute("openSession", openSession);

        // Pre-fill the form with the open session ID
        ContributionForm form = new ContributionForm();
        if (openSession != null) {
            form.setSessionId(openSession.getId());
        }

        model.addAttribute("tour", tour);
        model.addAttribute("participants", participants);
        model.addAttribute("notBenefitedYet", notBenefitedYet);
        model.addAttribute("form", form);
        model.addAttribute("members", searchMemberUseCase.findAllActive());

        // Build member name lookup for participants
        Map<Long, String> memberNames = participants.stream()
            .map(TontineParticipant::getMemberId)
            .distinct()
            .collect(Collectors.toMap(
                Function.identity(),
                mid -> searchMemberUseCase.findById(mid).getFullName()
            ));
        model.addAttribute("memberNames", memberNames);

        model.addAttribute("pageTitle", "Cotisation — Tour #" + tour.getId());
        return "tontine/contribution-form";
    }

    @PostMapping("/{id}/contribute")
    @PreAuthorize("hasAuthority('TONTINE_CONTRIBUTION_RECORD')")
    public String recordContribution(@PathVariable Long id,
                                     @Valid @ModelAttribute("form") ContributionForm form,
                                     BindingResult result,
                                     Model model,
                                     RedirectAttributes ra) {
        if (result.hasErrors()) {
            TontineTour tour = getTourSummaryUseCase.findTourById(id);
            List<TontineParticipant> participants = getTourSummaryUseCase
                .findParticipantsByTourId(id);
            Map<Long, String> memberNames = participants.stream()
                .map(TontineParticipant::getMemberId)
                .distinct()
                .collect(Collectors.toMap(
                    Function.identity(),
                    mid -> searchMemberUseCase.findById(mid).getFullName()
                ));
            model.addAttribute("tour", tour);
            model.addAttribute("participants", participants);
            model.addAttribute("memberNames", memberNames);
            model.addAttribute("notBenefitedYet", getTourSummaryUseCase
                .findNotBenefitedYet(id));
            model.addAttribute("members", searchMemberUseCase.findAllActive());
            model.addAttribute("pageTitle", "Cotisation — Tour #" + tour.getId());
            return "tontine/contribution-form";
        }

        try {
            Long actualSessionId = form.getSessionId();
            if (actualSessionId == null) {
                throw new IllegalArgumentException("La séance de référence est obligatoire pour enregistrer une cotisation.");
            }
            TontineContribution contribution = recordContributionUseCase.execute(
                id, actualSessionId, form.getContributorId(),
                form.getBeneficiaryId(), form.getAmount());
            if (contribution.isPaid()) {
                ra.addFlashAttribute("successMessage",
                    "Cotisation de " + form.getAmount() + " FCFA enregistrée.");
            } else {
                ra.addFlashAttribute("successMessage",
                    "Échec de cotisation enregistré.");
            }
        } catch (Exception e) {
            ra.addFlashAttribute("errorMessage", e.getMessage());
        }
        return "redirect:/tontine/" + id;
    }

    // ── Close tour ───────────────────────────────────────────

    @PostMapping("/{id}/close")
    @PreAuthorize("hasAuthority('TONTINE_TOUR_CREATE')")
    public String closeTour(@PathVariable Long id, RedirectAttributes ra) {
        try {
            closeTourUseCase.execute(id);
            ra.addFlashAttribute("successMessage", "Tour de grande tontine clôturé.");
        } catch (Exception e) {
            ra.addFlashAttribute("errorMessage", e.getMessage());
        }
        return "redirect:/tontine/" + id;
    }
}
