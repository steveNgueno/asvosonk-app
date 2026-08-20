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
import org.asvosonk.sanction.application.usecase.ListSanctionsUseCase;
import org.asvosonk.security.application.service.UserDetailsImpl;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.math.BigDecimal;
import java.util.EnumMap;
import java.util.Map;
import java.util.Set;

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
    private final ListSanctionsUseCase     listSanctionsUseCase;

    // ── List ─────────────────────────────────────────────────

    @GetMapping
    @PreAuthorize("hasAuthority('MEMBER_VIEW')")
    public String list(Model model) {
        var members = searchMemberUseCase.findAll();
        // Membres ayant des frais d'adhésion non soldés (UI-004) — 1 seule requête,
        // agrégée en SQL (l'ancienne version chargeait toutes les lignes de frais).
        Set<Long> membersWithPendingFees = feeRepository.findMemberIdsWithOutstandingFees();

        model.addAttribute("members", members);
        model.addAttribute("membersWithPendingFees", membersWithPendingFees);
        model.addAttribute("activeCount", members.stream().filter(Member::isActive).count());
        model.addAttribute("pageTitle", "Membres");
        return "members/list";
    }

    // ── Detail ───────────────────────────────────────────────

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('MEMBER_VIEW')")
    public String detail(@PathVariable Long id, Model model) {
        Member member = searchMemberUseCase.findById(id);
        var fees = feeRepository.findByMemberIdOrderByFeeType(id);

        model.addAttribute("member", member);
        model.addAttribute("fees", fees);
        model.addAttribute("feesTotalDue", fees.stream()
            .map(MembershipFee::getAmountDue).reduce(BigDecimal.ZERO, BigDecimal::add));
        model.addAttribute("feesTotalPaid", fees.stream()
            .map(MembershipFee::getAmountPaid).reduce(BigDecimal.ZERO, BigDecimal::add));
        // Sanctions of this member — the "Sanctions" tab used to be an empty
        // placeholder pointing at another screen; the data already exists here.
        model.addAttribute("sanctions", listSanctionsUseCase.findByMember(id));
        model.addAttribute("feePaymentForm", new FeePaymentForm());
        model.addAttribute("feeTypes", FeeType.values());

        // Reste à payer par type de frais : le formulaire s'en sert pour
        // pré-remplir le montant. Un frais jamais entamé reste dû en entier.
        Map<FeeType, BigDecimal> remaining = new EnumMap<>(FeeType.class);
        for (FeeType type : FeeType.values()) {
            remaining.put(type, type.defaultAmount());
        }
        for (MembershipFee fee : fees) {
            remaining.put(fee.getFeeType(),
                fee.getAmountDue().subtract(fee.getAmountPaid()).max(BigDecimal.ZERO));
        }
        model.addAttribute("feeRemaining", remaining);
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
            model.addAttribute("pageTitle", "Nouveau membre");
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
            model.addAttribute("memberId", id);
            model.addAttribute("pageTitle", "Modifier le membre");
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
                            @AuthenticationPrincipal UserDetailsImpl principal,
                            RedirectAttributes ra) {
        if (result.hasErrors()) {
            ra.addFlashAttribute("errorMessage", "Données invalides pour le paiement.");
            return "redirect:/members/" + id;
        }
        feePaymentForm.setMemberId(id);
        try {
            recordFeePaymentUseCase.execute(id, feePaymentForm.getFeeType(),
                feePaymentForm.getAmount(), principal != null ? principal.getAppUser() : null);
            ra.addFlashAttribute("successMessage",
                "Paiement " + feePaymentForm.getFeeType().label()
                    + " enregistré et rattaché à la séance du jour.");
        } catch (Exception e) {
            ra.addFlashAttribute("errorMessage", e.getMessage());
        }
        return "redirect:/members/" + id;
    }
}
