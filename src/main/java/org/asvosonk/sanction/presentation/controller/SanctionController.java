package org.asvosonk.sanction.presentation.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.asvosonk.cashbox.application.usecase.DepositMoneyUseCase;
import org.asvosonk.cashbox.domain.valueobject.CashboxType;
import org.asvosonk.cashbox.domain.valueobject.MovementOrigin;
import org.asvosonk.member.application.usecase.SearchMemberUseCase;
import org.asvosonk.sanction.application.usecase.CancelSanctionUseCase;
import org.asvosonk.sanction.application.usecase.CreateSanctionUseCase;
import org.asvosonk.sanction.application.usecase.ListSanctionsUseCase;
import org.asvosonk.sanction.application.usecase.PaySanctionUseCase;
import org.asvosonk.sanction.domain.model.Sanction;
import org.asvosonk.sanction.domain.valueobject.SanctionOrigin;
import org.asvosonk.sanction.domain.valueobject.SanctionStatus;
import org.asvosonk.sanction.presentation.request.ManualSanctionForm;
import org.asvosonk.security.application.service.UserDetailsImpl;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Controller
@RequestMapping("/sanctions")
@RequiredArgsConstructor
public class SanctionController {

    private final ListSanctionsUseCase  listSanctionsUseCase;
    private final CreateSanctionUseCase createSanctionUseCase;
    private final PaySanctionUseCase    paySanctionUseCase;
    private final CancelSanctionUseCase cancelSanctionUseCase;
    private final DepositMoneyUseCase   depositMoneyUseCase;
    private final SearchMemberUseCase   searchMemberUseCase;

    @GetMapping
    @PreAuthorize("hasAuthority('SANCTION_VIEW')")
    public String list(@RequestParam(required = false) SanctionStatus status,
                       @RequestParam(required = false) Long memberId,
                       Model model) {
        List<Sanction> sanctions;
        if (memberId != null) {
            sanctions = listSanctionsUseCase.findByMember(memberId);
        } else if (status != null) {
            sanctions = listSanctionsUseCase.findByStatus(status);
        } else {
            sanctions = listSanctionsUseCase.findAll();
        }

        BigDecimal unpaidTotal = BigDecimal.ZERO;
        long unpaidCount = 0;
        for (Sanction s : sanctions) {
            if (s.getStatus() == SanctionStatus.unpaid) {
                unpaidTotal = unpaidTotal.add(s.getAmount());
                unpaidCount++;
            }
        }

        // Build member name lookup
        Map<Long, String> memberNames = sanctions.stream()
            .map(Sanction::getMemberId)
            .distinct()
            .collect(Collectors.toMap(
                java.util.function.Function.identity(),
                id -> searchMemberUseCase.findById(id).getFullName()
            ));

        model.addAttribute("sanctions", sanctions);
        model.addAttribute("memberNames", memberNames);
        model.addAttribute("unpaidTotal", unpaidTotal);
        model.addAttribute("unpaidCount", unpaidCount);
        model.addAttribute("members", searchMemberUseCase.findAll());
        model.addAttribute("statuses", SanctionStatus.values());
        model.addAttribute("pageTitle", "Sanctions");
        return "sanctions/list";
    }

    @GetMapping("/new")
    @PreAuthorize("hasAuthority('SANCTION_CREATE_MANUAL')")
    public String newForm(Model model) {
        model.addAttribute("form", new ManualSanctionForm());
        model.addAttribute("members", searchMemberUseCase.findAllActive());
        model.addAttribute("pageTitle", "Nouvelle sanction disciplinaire");
        return "sanctions/form";
    }

    @PostMapping("/new")
    @PreAuthorize("hasAuthority('SANCTION_CREATE_MANUAL')")
    public String create(@Valid @ModelAttribute("form") ManualSanctionForm form,
                         BindingResult result,
                         @AuthenticationPrincipal UserDetailsImpl principal,
                         Model model,
                         RedirectAttributes ra) {
        if (result.hasErrors()) {
            model.addAttribute("members", searchMemberUseCase.findAllActive());
            model.addAttribute("pageTitle", "Nouvelle sanction disciplinaire");
            return "sanctions/form";
        }

        Sanction sanction = createSanctionUseCase.execute(
            form.getMemberId(), LocalDate.now(), form.getAmount(),
            form.getReason(), SanctionOrigin.manual, null);

        String memberName = searchMemberUseCase.findById(form.getMemberId()).getFullName();
        ra.addFlashAttribute("successMessage",
            "Sanction disciplinaire de " + form.getAmount() + " FCFA enregistrée pour "
                + memberName + ".");
        return "redirect:/sanctions";
    }

    @PostMapping("/{id}/pay")
    @PreAuthorize("hasAuthority('SANCTION_RECORD_PAYMENT')")
    public String pay(@PathVariable Long id,
                      @AuthenticationPrincipal UserDetailsImpl principal,
                      RedirectAttributes ra) {
        try {
            Sanction sanction = paySanctionUseCase.execute(id);

            depositMoneyUseCase.execute(CashboxType.sanction, sanction.getAmount(),
                "Paiement cash sanction #" + id + " — " + sanction.getReason(),
                MovementOrigin.sanction, null, null, null, principal.getAppUser());

            ra.addFlashAttribute("successMessage",
                "Paiement de " + sanction.getAmount() + " FCFA enregistré pour la sanction #" + id + ".");
        } catch (Exception e) {
            ra.addFlashAttribute("errorMessage", e.getMessage());
        }
        return "redirect:/sanctions";
    }

    @PostMapping("/{id}/cancel")
    @PreAuthorize("hasAuthority('SANCTION_CANCEL')")
    public String cancel(@PathVariable Long id,
                         RedirectAttributes ra) {
        try {
            cancelSanctionUseCase.execute(id);
            ra.addFlashAttribute("successMessage", "Sanction #" + id + " annulée.");
        } catch (Exception e) {
            ra.addFlashAttribute("errorMessage", e.getMessage());
        }
        return "redirect:/sanctions";
    }
}
