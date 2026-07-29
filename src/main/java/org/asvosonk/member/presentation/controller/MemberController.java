package org.asvosonk.member.presentation.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.asvosonk.member.application.usecase.*;
import org.asvosonk.member.domain.model.Member;
import org.asvosonk.member.domain.valueobject.FeeType;
import org.asvosonk.member.domain.valueobject.MemberStatus;
import org.asvosonk.member.infrastructure.persistence.entity.MembershipFee;
import org.asvosonk.member.infrastructure.persistence.repository.MembershipFeeRepository;
import org.asvosonk.member.presentation.request.FeePaymentForm;
import org.asvosonk.member.presentation.request.MemberRequest;
import org.asvosonk.member.presentation.response.MemberResponse;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.Set;
import java.util.stream.Collectors;

@Controller
@RequestMapping("/members")
@RequiredArgsConstructor
public class MemberController {

    private final SearchMemberUseCase      searchMemberUseCase;
    private final CreateMemberUseCase      createMemberUseCase;
    private final UpdateMemberUseCase      updateMemberUseCase;
    private final SuspendMemberUseCase     suspendMemberUseCase;
    private final RecordFeePaymentUseCase  recordFeePaymentUseCase;
    private final MembershipFeeRepository  feeRepository;

    // ── List ─────────────────────────────────────────────────

    @GetMapping
    @PreAuthorize("hasAuthority('MEMBER_VIEW')")
    public String list(Model model) {
        model.addAttribute("members", searchMemberUseCase.findAll());
        // Membres ayant des frais d'adhésion non soldés (UI-004) — 1 seule requête.
        Set<Long> membersWithPendingFees = feeRepository.findAll().stream()
                .filter(fee -> !fee.isFullyPaid())
                .map(fee -> fee.getMember().getId())
                .collect(Collectors.toSet());
        model.addAttribute("membersWithPendingFees", membersWithPendingFees);
        model.addAttribute("pageTitle", "Membres");
        return "members/list";
    }

    // ── Detail ───────────────────────────────────────────────

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('MEMBER_VIEW')")
    public String detail(@PathVariable Long id, Model model) {
        Member member = searchMemberUseCase.findById(id);
        model.addAttribute("member", member);
        model.addAttribute("fees", feeRepository.findByMemberIdOrderByFeeType(id));
        model.addAttribute("feePaymentForm", new FeePaymentForm());
        model.addAttribute("feeTypes", FeeType.values());
        model.addAttribute("memberStatuses", MemberStatus.values());
        model.addAttribute("pageTitle", member.getFullName());
        return "members/detail";
    }

    // ── Create ───────────────────────────────────────────────

    @GetMapping("/new")
    @PreAuthorize("hasAuthority('MEMBER_CREATE')")
    public String newForm(Model model) {
        model.addAttribute("memberRequest", new MemberRequest());
        model.addAttribute("statuses", MemberStatus.values());
        model.addAttribute("pageTitle", "Nouveau membre");
        model.addAttribute("editMode", false);
        return "members/form";
    }

    @PostMapping("/new")
    @PreAuthorize("hasAuthority('MEMBER_CREATE')")
    public String create(@Valid @ModelAttribute("memberRequest") MemberRequest memberRequest,
                         BindingResult result,
                         Model model,
                         RedirectAttributes ra) {
        if (result.hasErrors()) {
            model.addAttribute("statuses", MemberStatus.values());
            model.addAttribute("editMode", false);
            return "members/form";
        }
        Member created = createMemberUseCase.execute(memberRequest);
        ra.addFlashAttribute("successMessage",
            "Membre « " + created.getFullName() + " » enregistré avec succès.");
        return "redirect:/members/" + created.getId();
    }

    // ── Edit ─────────────────────────────────────────────────

    @GetMapping("/{id}/edit")
    @PreAuthorize("hasAuthority('MEMBER_EDIT')")
    public String editForm(@PathVariable Long id, Model model) {
        Member member = searchMemberUseCase.findById(id);
        MemberRequest request = new MemberRequest();
        request.setFullName(member.getFullName());
        request.setPhone(member.getPhone());
        request.setJoinDate(member.getJoinDate());
        request.setResident(member.isResident());
        request.setStatus(member.getStatus());
        model.addAttribute("memberRequest", request);
        model.addAttribute("memberId", id);
        model.addAttribute("statuses", MemberStatus.values());
        model.addAttribute("pageTitle", "Modifier — " + member.getFullName());
        model.addAttribute("editMode", true);
        return "members/form";
    }

    @PostMapping("/{id}/edit")
    @PreAuthorize("hasAuthority('MEMBER_EDIT')")
    public String update(@PathVariable Long id,
                         @Valid @ModelAttribute("memberRequest") MemberRequest memberRequest,
                         BindingResult result,
                         Model model,
                         RedirectAttributes ra) {
        if (result.hasErrors()) {
            model.addAttribute("statuses", MemberStatus.values());
            model.addAttribute("editMode", true);
            return "members/form";
        }
        updateMemberUseCase.execute(id, memberRequest);
        ra.addFlashAttribute("successMessage", "Informations mises à jour.");
        return "redirect:/members/" + id;
    }

    // ── Status change ────────────────────────────────────────

    @PostMapping("/{id}/status")
    @PreAuthorize("hasAuthority('MEMBER_CHANGE_STATUS')")
    public String changeStatus(@PathVariable Long id,
                               @RequestParam MemberStatus newStatus,
                               RedirectAttributes ra) {
        suspendMemberUseCase.changeStatus(id, newStatus);
        ra.addFlashAttribute("successMessage", "Statut mis à jour.");
        return "redirect:/members/" + id;
    }

    // ── Fee payment ──────────────────────────────────────────

    @PostMapping("/{id}/fees")
    @PreAuthorize("hasAuthority('MEMBERSHIP_FEE_RECORD')")
    public String recordFee(@PathVariable Long id,
                            @Valid @ModelAttribute FeePaymentForm feePaymentForm,
                            BindingResult result,
                            RedirectAttributes ra) {
        if (result.hasErrors()) {
            ra.addFlashAttribute("errorMessage", "Données invalides pour le paiement.");
            return "redirect:/members/" + id;
        }
        feePaymentForm.setMemberId(id);
        try {
            recordFeePaymentUseCase.execute(id, feePaymentForm.getFeeType(), feePaymentForm.getAmount());
            ra.addFlashAttribute("successMessage",
                "Paiement " + feePaymentForm.getFeeType().label() + " enregistré.");
        } catch (Exception e) {
            ra.addFlashAttribute("errorMessage", e.getMessage());
        }
        return "redirect:/members/" + id;
    }
}
