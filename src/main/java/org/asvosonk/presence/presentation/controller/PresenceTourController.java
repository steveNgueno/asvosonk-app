package org.asvosonk.presence.presentation.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.asvosonk.member.application.usecase.SearchMemberUseCase;
import org.asvosonk.presence.application.usecase.ClosePresenceTourUseCase;
import org.asvosonk.presence.application.usecase.CreatePresenceTourUseCase;
import org.asvosonk.presence.application.usecase.GetPresenceTourSummaryUseCase;
import org.asvosonk.presence.application.usecase.MarkPresenceBenefitedUseCase;
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
import java.util.function.Function;
import java.util.stream.Collectors;

@Controller
@RequestMapping("/presence-tours")
@RequiredArgsConstructor
public class PresenceTourController {

    private final GetPresenceTourSummaryUseCase     getPresenceTourSummaryUseCase;
    private final CreatePresenceTourUseCase         createPresenceTourUseCase;
    private final MarkPresenceBenefitedUseCase      markPresenceBenefitedUseCase;
    private final ClosePresenceTourUseCase          closePresenceTourUseCase;
    private final SearchMemberUseCase               searchMemberUseCase;

    // ── List tours ───────────────────────────────────────────

    @GetMapping
    @PreAuthorize("hasAuthority('SESSION_VIEW')")
    public String list(Model model) {
        List<PresenceTour> tours = getPresenceTourSummaryUseCase.findAllTours();
        PresenceTour currentOpen = getPresenceTourSummaryUseCase.findCurrentOpenTour();

        if (currentOpen != null) {
            List<PresenceTourParticipant> participants = getPresenceTourSummaryUseCase
                .findParticipantsByTourId(currentOpen.getId());
            long benefitedCount = getPresenceTourSummaryUseCase.countBenefited(currentOpen.getId());

            Map<Long, String> memberNames = participants.stream()
                .map(PresenceTourParticipant::getMemberId)
                .distinct()
                .collect(Collectors.toMap(
                    Function.identity(),
                    mid -> searchMemberUseCase.findById(mid).getFullName()
                ));

            model.addAttribute("openTour", currentOpen);
            model.addAttribute("openParticipants", participants);
            model.addAttribute("openBenefitedCount", benefitedCount);
            model.addAttribute("memberNames", memberNames);
        }

        model.addAttribute("tours", tours);
        model.addAttribute("pageTitle", "Tours de présence");
        return "presence-tours/list";
    }

    // ── Create tour form ─────────────────────────────────────

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
            PresenceTour tour = createPresenceTourUseCase.execute(
                form.getStartDate(), form.getParticipantIds(), form.getDrawOrders());
            ra.addFlashAttribute("successMessage",
                "Tour de présence créé avec " + form.getParticipantIds().size() + " participants.");
            return "redirect:/presence-tours/" + tour.getId();
        } catch (Exception e) {
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
        boolean allBenefited = getPresenceTourSummaryUseCase.allParticipantsBenefited(id);

        Map<Long, String> memberNames = participants.stream()
            .map(PresenceTourParticipant::getMemberId)
            .distinct()
            .collect(Collectors.toMap(
                Function.identity(),
                mid -> searchMemberUseCase.findById(mid).getFullName()
            ));

        model.addAttribute("tour", tour);
        model.addAttribute("participants", participants);
        model.addAttribute("allBenefited", allBenefited);
        model.addAttribute("memberNames", memberNames);
        model.addAttribute("pageTitle", "Tour #" + tour.getId() + " — Présence");
        return "presence-tours/detail";
    }

    // ── Close tour ───────────────────────────────────────────

    @PostMapping("/{id}/close")
    @PreAuthorize("hasAuthority('SESSION_CLOSE')")
    public String closeTour(@PathVariable Long id, RedirectAttributes ra) {
        try {
            closePresenceTourUseCase.execute(id);
            ra.addFlashAttribute("successMessage", "Tour de présence clôturé.");
        } catch (Exception e) {
            ra.addFlashAttribute("errorMessage", e.getMessage());
        }
        return "redirect:/presence-tours/" + id;
    }

    // ── Mark benefited (via session) ─────────────────────────

    @PostMapping("/{id}/benefited")
    @PreAuthorize("hasAuthority('ATTENDANCE_RECORD')")
    public String markBenefited(@PathVariable Long id,
                                @RequestParam Long memberId,
                                @RequestParam(required = false) Long sessionId,
                                RedirectAttributes ra) {
        try {
            markPresenceBenefitedUseCase.execute(id, memberId, sessionId);
            ra.addFlashAttribute("successMessage", "Bénéficiaire enregistré.");
        } catch (Exception e) {
            ra.addFlashAttribute("errorMessage", e.getMessage());
        }
        return "redirect:/presence-tours/" + id;
    }
}
