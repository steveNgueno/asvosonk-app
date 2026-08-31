package org.asvosonk.aid.presentation.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.asvosonk.aid.application.usecase.CreateAidUseCase;
import org.asvosonk.aid.application.usecase.GetAidDetailsUseCase;
import org.asvosonk.aid.application.usecase.ListAidsUseCase;
import org.asvosonk.aid.application.usecase.RecordAidRecoveryUseCase;
import org.asvosonk.aid.domain.model.Aid;
import org.asvosonk.aid.domain.model.AidContribution;
import org.asvosonk.aid.domain.repository.AidContributionRepository;
import org.asvosonk.aid.domain.valueobject.AidContributionStatus;
import org.asvosonk.aid.domain.valueobject.AidStatus;
import org.asvosonk.aid.domain.valueobject.AidType;
import org.asvosonk.aid.presentation.request.AidForm;
import org.asvosonk.common.domain.exception.BusinessRuleException;
import org.asvosonk.member.application.usecase.SearchMemberUseCase;
import org.asvosonk.security.application.service.UserDetailsImpl;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * Partie dédiée aux aides : enregistrement d'une aide, consultation avec
 * recherche et filtres, suivi des recouvrements, recouvrement direct.
 */
@Controller
@RequestMapping("/aids")
@RequiredArgsConstructor
public class AidController {

    private final ListAidsUseCase          listAidsUseCase;
    private final GetAidDetailsUseCase     getAidDetailsUseCase;
    private final CreateAidUseCase         createAidUseCase;
    private final RecordAidRecoveryUseCase recordAidRecoveryUseCase;
    private final AidContributionRepository aidContributionRepository;
    private final SearchMemberUseCase      searchMemberUseCase;

    // ── Liste + recherche/filtres ────────────────────────────

    @GetMapping
    @PreAuthorize("hasAuthority('AID_VIEW')")
    public String list(@RequestParam(required = false) Long memberId,
                       @RequestParam(required = false) AidStatus status,
                       Model model) {
        List<Aid> aids = listAidsUseCase.search(memberId, status);

        // Reste à recouvrir, aide par aide, calculé côté serveur.
        Map<Long, BigDecimal> remainingByAid = new java.util.HashMap<>();
        BigDecimal totalRemaining = BigDecimal.ZERO;
        for (Aid aid : aids) {
            BigDecimal remaining = getAidDetailsUseCase.remainingTotal(aid.getId());
            remainingByAid.put(aid.getId(), remaining);
            totalRemaining = totalRemaining.add(remaining);
        }
        long currentCount = aids.stream().filter(Aid::isCurrent).count();

        model.addAttribute("aids", aids);
        model.addAttribute("remainingByAid", remainingByAid);
        model.addAttribute("memberNames", searchMemberUseCase.findNamesByIds(
            aids.stream().map(Aid::getBeneficiaryId).toList()));
        model.addAttribute("totalRemaining", totalRemaining);
        model.addAttribute("currentCount", currentCount);
        model.addAttribute("members", searchMemberUseCase.findAllActive());
        model.addAttribute("statuses", AidStatus.values());
        model.addAttribute("types", AidType.values());
        model.addAttribute("filterMemberId", memberId);
        model.addAttribute("filterStatus", status);
        model.addAttribute("pageTitle", "Aides");
        return "aids/list";
    }

    // ── Détail d'une aide : qui a recouvert, qui reste dû ────

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('AID_VIEW')")
    public String detail(@PathVariable Long id,
                         @RequestParam(required = false) AidContributionStatus contributionStatus,
                         Model model) {
        Aid aid = getAidDetailsUseCase.getAid(id);
        List<AidContribution> contributions = getAidDetailsUseCase.getContributions(id);

        if (contributionStatus != null) {
            contributions = contributions.stream()
                .filter(c -> c.getStatus() == contributionStatus)
                .toList();
        }

        BigDecimal recoveredTotal = contributions.stream()
            .map(AidContribution::getAmountPaid)
            .reduce(BigDecimal.ZERO, BigDecimal::add);

        model.addAttribute("aid", aid);
        model.addAttribute("contributions", contributions);
        model.addAttribute("memberNames", searchMemberUseCase.findNamesByIds(
            contributions.stream().map(AidContribution::getMemberId).toList()));
        model.addAttribute("beneficiaryName",
            searchMemberUseCase.findById(aid.getBeneficiaryId()).getFullName());
        model.addAttribute("remainingTotal", getAidDetailsUseCase.remainingTotal(id));
        model.addAttribute("recoveredTotal", recoveredTotal);
        model.addAttribute("contributionStatuses", AidContributionStatus.values());
        model.addAttribute("paymentModes", org.asvosonk.aid.domain.valueobject.AidPaymentMode.values());
        model.addAttribute("filterContributionStatus", contributionStatus);
        model.addAttribute("pageTitle", "Aide — " + aid.getType().label());
        return "aids/detail";
    }

    // ── Enregistrement d'une aide ────────────────────────────

    @GetMapping("/new")
    @PreAuthorize("hasAuthority('AID_CREATE')")
    public String newForm(Model model) {
        model.addAttribute("form", new AidForm());
        model.addAttribute("members", searchMemberUseCase.findAllActive());
        model.addAttribute("types", AidType.values());
        model.addAttribute("defaultAmounts", defaultAmountsByType());
        model.addAttribute("pageTitle", "Nouvelle aide");
        return "aids/form";
    }

    @PostMapping("/new")
    @PreAuthorize("hasAuthority('AID_CREATE')")
    public String create(@Valid @ModelAttribute("form") AidForm form,
                         BindingResult result,
                         @AuthenticationPrincipal UserDetailsImpl principal,
                         Model model,
                         RedirectAttributes ra) {
        if (result.hasErrors()) {
            model.addAttribute("members", searchMemberUseCase.findAllActive());
            model.addAttribute("types", AidType.values());
            model.addAttribute("defaultAmounts", defaultAmountsByType());
            model.addAttribute("pageTitle", "Nouvelle aide");
            return "aids/form";
        }

        try {
            AidType type = parseType(form.getType());
            Aid aid = createAidUseCase.execute(
                form.getBeneficiaryId(), type, form.getAidDate(),
                form.getTotalAmount(), form.getSharePerMember(),
                form.getDescription(), principal.getAppUser());

            String memberName = searchMemberUseCase.findById(form.getBeneficiaryId()).getFullName();
            ra.addFlashAttribute("successMessage",
                "Aide « " + aid.getType().label() + " » de "
              + java.text.NumberFormat.getIntegerInstance().format(form.getTotalAmount())
              + " FCFA enregistrée pour " + memberName + ".");
            return "redirect:/aids/" + aid.getId();
        } catch (BusinessRuleException | IllegalArgumentException e) {
            model.addAttribute("errorMessage", e.getMessage());
            model.addAttribute("members", searchMemberUseCase.findAllActive());
            model.addAttribute("types", AidType.values());
            model.addAttribute("defaultAmounts", defaultAmountsByType());
            model.addAttribute("pageTitle", "Nouvelle aide");
            return "aids/form";
        }
    }

    /** Type proposé par le formulaire ; absent ou inconnu → autre. */
    private AidType parseType(String raw) {
        if (raw == null || raw.isBlank()) {
            return AidType.autre;
        }
        try {
            return AidType.valueOf(raw);
        } catch (IllegalArgumentException unknown) {
            throw new BusinessRuleException("Type d'aide inconnu : " + raw);
        }
    }

    private Map<String, BigDecimal> defaultAmountsByType() {
        return java.util.Arrays.stream(AidType.values())
            .collect(java.util.stream.Collectors.toMap(Enum::name, AidType::defaultAmount));
    }

    // ── Recouvrement direct ─────────────────────────────────

    /**
     * Un membre verse lui-même sa part, en séance. Le versement partiel est
     * accepté ; la part reste due pour le solde.
     */
    @PostMapping("/contributions/{contributionId}/recover")
    @PreAuthorize("hasAuthority('AID_RECORD_RECOVERY')")
    public String recover(@PathVariable Long contributionId,
                          @RequestParam(required = false) BigDecimal amount,
                          @AuthenticationPrincipal UserDetailsImpl principal,
                          RedirectAttributes ra) {
        try {
            AidContribution contribution =
                recordAidRecoveryUseCase.execute(contributionId, amount, principal.getAppUser());
            ra.addFlashAttribute("successMessage", "Recouvrement enregistré.");
            return "redirect:/aids/" + contribution.getAidId();
        } catch (BusinessRuleException | IllegalArgumentException e) {
            Long aidId = aidContributionRepository.findById(contributionId)
                .map(AidContribution::getAidId).orElse(null);
            ra.addFlashAttribute("errorMessage", e.getMessage());
            return aidId != null ? "redirect:/aids/" + aidId : "redirect:/aids";
        }
    }
}
