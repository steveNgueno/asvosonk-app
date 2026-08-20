package org.asvosonk.cashbox.presentation.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.asvosonk.cashbox.application.usecase.DepositMoneyUseCase;
import org.asvosonk.cashbox.application.usecase.GenerateBalanceUseCase;
import org.asvosonk.cashbox.application.usecase.WithdrawMoneyUseCase;
import org.asvosonk.cashbox.domain.model.CashboxMovement;
import org.asvosonk.cashbox.domain.repository.CashboxMovementRepository;
import org.asvosonk.cashbox.domain.valueobject.CashboxType;
import org.asvosonk.cashbox.domain.valueobject.MovementDirection;
import org.asvosonk.cashbox.domain.valueobject.MovementOrigin;
import org.asvosonk.cashbox.presentation.request.ManualMovementForm;
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
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@Controller
@RequestMapping("/cashboxes")
@RequiredArgsConstructor
public class CashboxController {

    private final GenerateBalanceUseCase    generateBalanceUseCase;
    private final DepositMoneyUseCase       depositMoneyUseCase;
    private final WithdrawMoneyUseCase      withdrawMoneyUseCase;
    private final CashboxMovementRepository movementRepository;
    private final SearchMemberUseCase       searchMemberUseCase;

    @GetMapping
    @PreAuthorize("hasAuthority('CASHBOX_VIEW')")
    public String overview(Model model) {
        Map<CashboxType, BigDecimal> balances = generateBalanceUseCase.execute();
        List<CashboxMovement> recentMovements = movementRepository.findRecentMovements(10);

        model.addAttribute("balances", balances);
        model.addAttribute("totalBalance", balances.values().stream()
            .reduce(BigDecimal.ZERO, BigDecimal::add));
        model.addAttribute("recentMovements", recentMovements);
        model.addAttribute("memberNames", namesOf(recentMovements));
        model.addAttribute("cashboxTypes", CashboxType.values());
        model.addAttribute("pageTitle", "Caisses");
        return "cashboxes/overview";
    }

    // ── Movements history ────────────────────────────────────

    @GetMapping("/movements")
    @PreAuthorize("hasAuthority('CASHBOX_VIEW')")
    public String movements(@RequestParam(required = false) LocalDate dateFrom,
                            @RequestParam(required = false) LocalDate dateTo,
                            @RequestParam(required = false) CashboxType type,
                            Model model) {
        // F-48 — filter in SQL instead of loading the whole table into memory.
        List<CashboxMovement> filtered = movementRepository.findFiltered(type, dateFrom, dateTo);

        Map<CashboxType, BigDecimal> balances = generateBalanceUseCase.execute();
        BigDecimal totalEntries = filtered.stream()
            .filter(m -> m.getDirection() == MovementDirection.in)
            .map(CashboxMovement::getAmount)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal totalExits = filtered.stream()
            .filter(m -> m.getDirection() == MovementDirection.out)
            .map(CashboxMovement::getAmount)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal currentBalance = balances.values().stream()
            .reduce(BigDecimal.ZERO, BigDecimal::add);

        model.addAttribute("movements", filtered);
        model.addAttribute("memberNames", namesOf(filtered));
        model.addAttribute("cashboxTypes", CashboxType.values());
        model.addAttribute("totalEntries", totalEntries);
        model.addAttribute("totalExits", totalExits);
        model.addAttribute("currentBalance", currentBalance);
        model.addAttribute("pageTitle", "Historique des mouvements");
        return "cashboxes/movements";
    }

    /**
     * Member names for a movement list, so the tables can show a person instead
     * of a raw "#42" identifier. Resolved in one query.
     */
    private Map<Long, String> namesOf(List<CashboxMovement> movements) {
        return searchMemberUseCase.findNamesByIds(movements.stream()
            .map(CashboxMovement::getMemberId)
            .filter(java.util.Objects::nonNull)
            .distinct()
            .toList());
    }

    // ── Manual deposit ───────────────────────────────────────

    @PostMapping("/{type}/deposit")
    @PreAuthorize("hasAuthority('CASHBOX_MANUAL_MOVEMENT')")
    public String deposit(@PathVariable CashboxType type,
                          @Valid @ModelAttribute ManualMovementForm form,
                          BindingResult result,
                          @AuthenticationPrincipal UserDetailsImpl principal,
                          RedirectAttributes ra) {
        if (result.hasErrors()) {
            ra.addFlashAttribute("errorMessage",
                "Données invalides : " + (result.getFieldError() != null
                    ? result.getFieldError().getDefaultMessage() : "motif ou montant invalide"));
            return "redirect:/cashboxes";
        }

        CashboxMovement movement = depositMoneyUseCase.execute(
            type, form.getAmount(), form.getReason(),
            MovementOrigin.manual, null, null, null, principal.getAppUser());

        if (movement != null) {
            ra.addFlashAttribute("successMessage",
                "Entrée de " + form.getAmount() + " FCFA enregistrée dans " + type.label()
                    + ". Nouveau solde : " + movement.getCashbox().getBalance() + " FCFA");
        }
        return "redirect:/cashboxes";
    }

    // ── Manual withdrawal ────────────────────────────────────

    @PostMapping("/{type}/withdraw")
    @PreAuthorize("hasAuthority('CASHBOX_MANUAL_MOVEMENT')")
    public String withdraw(@PathVariable CashboxType type,
                           @Valid @ModelAttribute ManualMovementForm form,
                           BindingResult result,
                           @AuthenticationPrincipal UserDetailsImpl principal,
                           RedirectAttributes ra) {
        if (result.hasErrors()) {
            ra.addFlashAttribute("errorMessage",
                "Données invalides : " + (result.getFieldError() != null
                    ? result.getFieldError().getDefaultMessage() : "motif ou montant invalide"));
            return "redirect:/cashboxes";
        }

        CashboxMovement movement = withdrawMoneyUseCase.execute(
            type, form.getAmount(), form.getReason(),
            MovementOrigin.manual, null, null, null, principal.getAppUser());

        if (movement != null) {
            ra.addFlashAttribute("successMessage",
                "Sortie de " + form.getAmount() + " FCFA enregistrée dans " + type.label()
                    + ". Nouveau solde : " + movement.getCashbox().getBalance() + " FCFA");
        }
        return "redirect:/cashboxes";
    }
}
