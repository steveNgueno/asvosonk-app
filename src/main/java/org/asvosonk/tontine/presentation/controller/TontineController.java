package org.asvosonk.tontine.presentation.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.asvosonk.member.application.usecase.SearchMemberUseCase;
import org.asvosonk.tontine.application.usecase.CloseTourUseCase;
import org.asvosonk.tontine.application.usecase.CreateTourUseCase;
import org.asvosonk.tontine.application.usecase.GetTourSummaryUseCase;
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
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Controller
@RequestMapping("/tontine")
@RequiredArgsConstructor
public class TontineController {

    private final GetTourSummaryUseCase               getTourSummaryUseCase;
    private final CreateTourUseCase                   createTourUseCase;
    private final RecordTontineContributionUseCase     recordContributionUseCase;
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

            model.addAttribute("openTour", currentOpen);
            model.addAttribute("openParticipants", participants);
            model.addAttribute("openTotalCollected", totalCollected);
            model.addAttribute("openBenefitedCount", participants.stream()
                .filter(TontineParticipant::isHasBenefited).count());
            model.addAttribute("memberNames", namesOfParticipants(participants));
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
        } catch (RuntimeException e) {
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
        List<TontineContribution> contributions = getTourSummaryUseCase.findContributionsByTourId(id);
        List<TontineDebt> owedDebts = getTourSummaryUseCase.findOwedDebtsByTourId(id);
        boolean allBenefited = getTourSummaryUseCase.allParticipantsBenefited(id);

        // One batched lookup covering participants, debtors, creditors,
        // contributors and beneficiaries (some may no longer be participants).
        List<Long> ids = new ArrayList<>(namesToResolve(participants));
        owedDebts.forEach(d -> { ids.add(d.getDebtorId()); ids.add(d.getCreditorId()); });
        contributions.forEach(c -> { ids.add(c.getContributorId()); ids.add(c.getBeneficiaryId()); });

        model.addAttribute("tour", tour);
        model.addAttribute("participants", participants);
        model.addAttribute("contributions", contributions);
        model.addAttribute("owedDebts", owedDebts);
        model.addAttribute("allBenefited", allBenefited);
        model.addAttribute("totalCollected", getTourSummaryUseCase.calculateTotalCollected(id));
        model.addAttribute("memberNames", searchMemberUseCase.findNamesByIds(ids));
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

        // Load the current open session so the contribution form can link to it
        MeetingSessionEntity openSession = meetingSessionRepository
            .findByStatusOrderBySessionDateDesc(SessionStatus.open)
            .stream()
            .findFirst()
            .orElse(null);

        // Pre-fill the form: current session, next beneficiary in draw order and
        // the imposed contribution amount. These are only saisie defaults — the
        // use case re-validates everything server-side.
        ContributionForm form = new ContributionForm();
        if (openSession != null) {
            form.setSessionId(openSession.getId());
        }
        getTourSummaryUseCase.findNotBenefitedYet(id).stream().findFirst()
            .ifPresent(next -> form.setBeneficiaryId(next.getMemberId()));
        form.setAmount(new BigDecimal("5000"));

        model.addAttribute("openSession", openSession);
        model.addAttribute("form", form);
        addContributionFormModel(id, participants, model);
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
            addContributionFormModel(id, getTourSummaryUseCase.findParticipantsByTourId(id), model);
            return "tontine/contribution-form";
        }

        try {
            Long actualSessionId = form.getSessionId();
            if (actualSessionId == null) {
                throw new IllegalArgumentException(
                    "La séance de référence est obligatoire pour enregistrer une cotisation.");
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
        } catch (RuntimeException e) {
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
        } catch (RuntimeException e) {
            ra.addFlashAttribute("errorMessage", e.getMessage());
        }
        return "redirect:/tontine/" + id;
    }

    // ── Helpers ──────────────────────────────────────────────

    private void addContributionFormModel(Long tourId,
                                          List<TontineParticipant> participants,
                                          Model model) {
        TontineTour tour = getTourSummaryUseCase.findTourById(tourId);
        model.addAttribute("tour", tour);
        model.addAttribute("participants", participants);
        model.addAttribute("notBenefitedYet", getTourSummaryUseCase.findNotBenefitedYet(tourId));
        model.addAttribute("memberNames", namesOfParticipants(participants));
        model.addAttribute("pageTitle", "Cotisation — Tour #" + tourId);
    }

    private Map<Long, String> namesOfParticipants(List<TontineParticipant> participants) {
        return searchMemberUseCase.findNamesByIds(namesToResolve(participants));
    }

    private List<Long> namesToResolve(List<TontineParticipant> participants) {
        return participants.stream().map(TontineParticipant::getMemberId).distinct().toList();
    }
}
