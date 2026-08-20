package org.asvosonk.presence.presentation.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.asvosonk.member.application.usecase.SearchMemberUseCase;
import org.asvosonk.presence.application.usecase.ClosePresenceTourUseCase;
import org.asvosonk.presence.application.usecase.CreatePresenceTourUseCase;
import org.asvosonk.presence.application.usecase.GetPresenceTourSummaryUseCase;
import org.asvosonk.presence.domain.model.PresenceTour;
import org.asvosonk.presence.domain.model.PresenceTourParticipant;
import org.asvosonk.presence.presentation.request.CreatePresenceTourForm;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.List;
import java.util.Map;

@Controller
@RequestMapping("/presence-tours")
@RequiredArgsConstructor
public class PresenceTourController {

    private final GetPresenceTourSummaryUseCase getPresenceTourSummaryUseCase;
    private final CreatePresenceTourUseCase     createPresenceTourUseCase;
    private final ClosePresenceTourUseCase      closePresenceTourUseCase;
    private final SearchMemberUseCase           searchMemberUseCase;

    // ── List tours ───────────────────────────────────────────

    @GetMapping
    @PreAuthorize("hasAuthority('SESSION_VIEW')")
    public String list(Model model) {
        List<PresenceTour> tours = getPresenceTourSummaryUseCase.findAllTours();
        PresenceTour currentOpen = getPresenceTourSummaryUseCase.findCurrentOpenTour();

        if (currentOpen != null) {
            List<PresenceTourParticipant> participants = getPresenceTourSummaryUseCase
                .findParticipantsByTourId(currentOpen.getId());

            model.addAttribute("openTour", currentOpen);
            model.addAttribute("openParticipants", participants);
            model.addAttribute("openBenefitedCount",
                getPresenceTourSummaryUseCase.countBenefited(currentOpen.getId()));
            model.addAttribute("memberNames", resolveNames(participants));
        }

        model.addAttribute("tours", tours);
        model.addAttribute("activeMemberCount", searchMemberUseCase.findAllActive().size());
        model.addAttribute("pageTitle", "Tours de présence");
        return "presence-tours/list";
    }

    // ── Create tour ──────────────────────────────────────────

    @GetMapping("/new")
    @PreAuthorize("hasAuthority('SESSION_CREATE')")
    public String newTourForm(Model model) {
        model.addAttribute("form", new CreatePresenceTourForm());
        model.addAttribute("members", searchMemberUseCase.findAllActive());
        model.addAttribute("pageTitle", "Nouveau tour de présence");
        return "presence-tours/form";
    }

    @PostMapping("/new")
    @PreAuthorize("hasAuthority('SESSION_CREATE')")
    public String createTour(@Valid @ModelAttribute("form") CreatePresenceTourForm form,
                             BindingResult result,
                             Model model,
                             RedirectAttributes ra) {
        if (result.hasErrors()) {
            model.addAttribute("members", searchMemberUseCase.findAllActive());
            model.addAttribute("pageTitle", "Nouveau tour de présence");
            return "presence-tours/form";
        }

        try {
            PresenceTour tour = createPresenceTourUseCase.execute(form.getStartDate());
            ra.addFlashAttribute("successMessage",
                "Tour de présence ouvert avec tous les membres actifs.");
            return "redirect:/presence-tours/" + tour.getId();
        } catch (RuntimeException e) {
            ra.addFlashAttribute("errorMessage", e.getMessage());
            return "redirect:/presence-tours/new";
        }
    }

    // ── Tour detail ──────────────────────────────────────────

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('SESSION_VIEW')")
    public String detail(@PathVariable Long id, Model model) {
        PresenceTour tour = getPresenceTourSummaryUseCase.findTourById(id);
        List<PresenceTourParticipant> participants = getPresenceTourSummaryUseCase
            .findParticipantsByTourId(id);

        model.addAttribute("tour", tour);
        model.addAttribute("participants", participants);
        model.addAttribute("allBenefited", getPresenceTourSummaryUseCase.allParticipantsBenefited(id));
        model.addAttribute("benefitedCount", getPresenceTourSummaryUseCase.countBenefited(id));
        model.addAttribute("eligible", getPresenceTourSummaryUseCase.findEligibleBeneficiaries(id));
        model.addAttribute("memberNames", resolveNames(participants));
        model.addAttribute("pageTitle", "Tour de présence #" + tour.getId());
        return "presence-tours/detail";
    }

    // ── Close tour ───────────────────────────────────────────

    @PostMapping("/{id}/close")
    @PreAuthorize("hasAuthority('SESSION_CLOSE')")
    public String closeTour(@PathVariable Long id, RedirectAttributes ra) {
        try {
            closePresenceTourUseCase.execute(id);
            ra.addFlashAttribute("successMessage", "Tour de présence clôturé.");
        } catch (RuntimeException e) {
            ra.addFlashAttribute("errorMessage", e.getMessage());
        }
        return "redirect:/presence-tours/" + id;
    }

    // ── Helpers ──────────────────────────────────────────────

    private Map<Long, String> resolveNames(List<PresenceTourParticipant> participants) {
        return searchMemberUseCase.findNamesByIds(
            participants.stream().map(PresenceTourParticipant::getMemberId).distinct().toList());
    }
}
