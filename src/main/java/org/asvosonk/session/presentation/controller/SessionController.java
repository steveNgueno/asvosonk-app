package org.asvosonk.session.presentation.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.asvosonk.member.application.usecase.SearchMemberUseCase;
import org.asvosonk.security.application.service.UserDetailsImpl;
import org.asvosonk.session.presentation.request.AttendanceEntryForm;
import org.asvosonk.session.presentation.response.SessionCloseResult;
import org.asvosonk.session.presentation.request.SessionForm;
import org.asvosonk.session.infrastructure.persistence.entity.MeetingSessionEntity;
import org.asvosonk.session.application.service.SessionService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/sessions")
@RequiredArgsConstructor
public class SessionController {

    private final SessionService     sessionService;
    private final SearchMemberUseCase searchMemberUseCase;

    // ── List ─────────────────────────────────────────────────

    @GetMapping
    @PreAuthorize("hasAuthority('SESSION_VIEW')")
    public String list(Model model) {
        model.addAttribute("sessions", sessionService.findAll());
        model.addAttribute("pageTitle", "Séances");
        return "sessions/list";
    }

    // ── Detail ───────────────────────────────────────────────

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('SESSION_VIEW')")
    public String detail(@PathVariable Long id, Model model) {
        MeetingSessionEntity session = sessionService.findById(id);
        model.addAttribute("session", session);
        model.addAttribute("attendances", sessionService.findAttendances(id));
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
            return "redirect:/sessions/" + session.getId() + "/attendance";
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
        if (session.isClosed()) return "redirect:/sessions/" + id;

        model.addAttribute("session",     session);
        model.addAttribute("attendances", sessionService.findAttendances(id));
        model.addAttribute("members",     searchMemberUseCase.findAllActive());
        model.addAttribute("entryForm",   new AttendanceEntryForm());
        model.addAttribute("pageTitle",   "Saisie — " + session.getSessionDate());
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

    // ── Set beneficiary ──────────────────────────────────────

    @PostMapping("/{id}/beneficiary")
    @PreAuthorize("hasAuthority('ATTENDANCE_RECORD')")
    public String setBeneficiary(@PathVariable Long id,
                                 @RequestParam Long memberId,
                                 RedirectAttributes ra) {
        sessionService.setBeneficiary(id, memberId);
        ra.addFlashAttribute("successMessage", "Bénéficiaire du jour enregistré.");
        return "redirect:/sessions/" + id + "/attendance";
    }

    // ── Close session ────────────────────────────────────────

    @PostMapping("/{id}/close")
    @PreAuthorize("hasAuthority('SESSION_CLOSE')")
    public String closeSession(@PathVariable Long id,
                               @AuthenticationPrincipal UserDetailsImpl principal,
                               RedirectAttributes ra) {
        try {
            SessionCloseResult closeResult =
                sessionService.closeSession(id, principal.getAppUser());
            // Store result in flash for the report page
            ra.addFlashAttribute("closeResult", closeResult);
            ra.addFlashAttribute("successMessage", "Séance clôturée avec succès.");
            return "redirect:/sessions/" + id + "/report";
        } catch (IllegalStateException e) {
            ra.addFlashAttribute("errorMessage", e.getMessage());
            return "redirect:/sessions/" + id + "/attendance";
        }
    }

    // ── Session report ───────────────────────────────────────

    @GetMapping("/{id}/report")
    @PreAuthorize("hasAuthority('SESSION_VIEW')")
    public String report(@PathVariable Long id, Model model) {
        MeetingSessionEntity session = sessionService.findById(id);
        model.addAttribute("session",     session);
        model.addAttribute("attendances", sessionService.findAttendances(id));
        model.addAttribute("pageTitle",   "Rapport — " + session.getSessionDate());
        // closeResult is injected from flash attributes if coming right after close
        return "sessions/report";
    }
}
