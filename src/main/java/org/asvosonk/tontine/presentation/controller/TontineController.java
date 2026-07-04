package org.asvosonk.tontine.presentation.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.asvosonk.member.application.usecase.SearchMemberUseCase;
import org.asvosonk.tontine.application.usecase.CloseTourUseCase;
import org.asvosonk.tontine.application.usecase.CreateTourUseCase;
import org.asvosonk.tontine.application.usecase.GetTourDebtsUseCase;
import org.asvosonk.tontine.application.usecase.MarkBenefitedUseCase;
import org.asvosonk.tontine.application.usecase.RecordTontineContributionUseCase;
import org.asvosonk.tontine.domain.model.TontineContribution;
import org.asvosonk.tontine.domain.model.TontineDebt;
import org.asvosonk.tontine.domain.model.TontineParticipant;
import org.asvosonk.tontine.domain.model.TontineTour;
import org.asvosonk.tontine.domain.repository.TontineContributionRepository;
import org.asvosonk.tontine.domain.repository.TontineParticipantRepository;
import org.asvosonk.tontine.domain.repository.TontineTourRepository;
import org.asvosonk.tontine.domain.valueobject.DebtStatus;
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
import java.util.stream.Collectors;

@Controller
@RequestMapping("/tontine")
@RequiredArgsConstructor
public class TontineController {

    private final TontineTourRepository           tourRepository;
    private final TontineParticipantRepository    participantRepository;
    private final TontineContributionRepository   contributionRepository;
    private final CreateTourUseCase               createTourUseCase;
    private final RecordTontineContributionUseCase recordContributionUseCase;
    private final MarkBenefitedUseCase            markBenefitedUseCase;
    private final CloseTourUseCase                closeTourUseCase;
    private final GetTourDebtsUseCase             getTourDebtsUseCase;
    private final SearchMemberUseCase             searchMemberUseCase;

    // ── List tours ───────────────────────────────────────────

    @GetMapping
    @PreAuthorize("hasAuthority('TONTINE_VIEW')")
    public String list(Model model) {
        List<TontineTour> tours = tourRepository.findAllByOrderByStartDateDesc();
        TontineTour currentOpen = tourRepository.findCurrentOpenTour().orElse(null);

        if (currentOpen != null) {
            List<TontineParticipant> participants = participantRepository
                .findByTourIdOrderByDrawOrder(currentOpen.getId());
            List<TontineContribution> contributions = contributionRepository
                .findByTourIdOrderByCreatedAtDesc(currentOpen.getId());
            BigDecimal totalCollected = contributions.stream()
                .filter(TontineContribution::isPaid)
                .map(TontineContribution::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
            model.addAttribute("openTour", currentOpen);
            model.addAttribute("openParticipants", participants);
            model.addAttribute("openTotalCollected", totalCollected);
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
        TontineTour tour = tourRepository.findById(id)
            .orElseThrow(() -> new IllegalArgumentException("Tour introuvable : " + id));
        List<TontineParticipant> participants = participantRepository
            .findByTourIdOrderByDrawOrder(id);
        List<TontineContribution> contributions = contributionRepository
            .findByTourIdOrderByCreatedAtDesc(id);
        List<TontineDebt> debts = getTourDebtsUseCase.execute(id);
        List<TontineDebt> owedDebts = debts.stream()
            .filter(d -> d.getStatus() == DebtStatus.owed)
            .collect(Collectors.toList());

        boolean allBenefited = participants.stream()
            .allMatch(TontineParticipant::isHasBenefited);

        model.addAttribute("tour", tour);
        model.addAttribute("participants", participants);
        model.addAttribute("contributions", contributions);
        model.addAttribute("owedDebts", owedDebts);
        model.addAttribute("allBenefited", allBenefited);
        model.addAttribute("pageTitle", "Tour #" + tour.getId() + " — Grande Tontine");
        return "tontine/tour-detail";
    }

    // ── Contribution form ────────────────────────────────────

    @GetMapping("/{id}/contribute")
    @PreAuthorize("hasAuthority('TONTINE_CONTRIBUTION_RECORD')")
    public String contributionForm(@PathVariable Long id, Model model) {
        TontineTour tour = tourRepository.findById(id)
            .orElseThrow(() -> new IllegalArgumentException("Tour introuvable : " + id));

        if (!tour.isOpen()) {
            return "redirect:/tontine/" + id;
        }

        List<TontineParticipant> participants = participantRepository
            .findByTourIdOrderByDrawOrder(id);
        List<TontineParticipant> notBenefitedYet = participants.stream()
            .filter(p -> !p.isHasBenefited())
            .collect(Collectors.toList());

        model.addAttribute("tour", tour);
        model.addAttribute("participants", participants);
        model.addAttribute("notBenefitedYet", notBenefitedYet);
        model.addAttribute("form", new ContributionForm());
        model.addAttribute("members", searchMemberUseCase.findAllActive());
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
            TontineTour tour = tourRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Tour introuvable : " + id));
            List<TontineParticipant> participants = participantRepository
                .findByTourIdOrderByDrawOrder(id);
            model.addAttribute("tour", tour);
            model.addAttribute("participants", participants);
            model.addAttribute("notBenefitedYet", participants.stream()
                .filter(p -> !p.isHasBenefited()).collect(Collectors.toList()));
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
