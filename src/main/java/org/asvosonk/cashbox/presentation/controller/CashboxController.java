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
@RequestMapping("/cashboxes")
@RequiredArgsConstructor
public class CashboxController {

    private final GenerateBalanceUseCase    generateBalanceUseCase;
    private final DepositMoneyUseCase       depositMoneyUseCase;
    private final WithdrawMoneyUseCase      withdrawMoneyUseCase;
    private final CashboxMovementRepository movementRepository;

    @GetMapping
    @PreAuthorize("hasAuthority('CASHBOX_VIEW')")
    public String overview(Model model) {
        Map<CashboxType, BigDecimal> balances = generateBalanceUseCase.execute();
        var recentMovements = movementRepository.findRecentMovements(10);

        model.addAttribute("balances", balances);
        model.addAttribute("recentMovements", recentMovements);
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
        List<CashboxMovement> all = movementRepository.findAllByOrderByMovementDateDesc();

        List<CashboxMovement> filtered = all.stream()
            .filter(m -> type == null || m.getCashbox().getType() == type)
            .filter(m -> dateFrom == null || !m.getMovementDate().toLocalDate().isBefore(dateFrom))
            .filter(m -> dateTo == null || !m.getMovementDate().toLocalDate().isAfter(dateTo))
            .collect(Collectors.toList());

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
        model.addAttribute("cashboxTypes", CashboxType.values());
        model.addAttribute("totalEntries", totalEntries);
        model.addAttribute("totalExits", totalExits);
        model.addAttribute("currentBalance", currentBalance);
        model.addAttribute("pageTitle", "Historique des mouvements");
        return "cashboxes/movements";
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
