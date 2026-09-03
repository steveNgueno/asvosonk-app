package org.asvosonk.bank.presentation.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.asvosonk.bank.application.usecase.CreateLoanUseCase;
import org.asvosonk.bank.application.usecase.GetMemberBankSummaryUseCase;
import org.asvosonk.bank.application.usecase.RecordLoanRepaymentUseCase;
import org.asvosonk.bank.application.usecase.RecordSavingUseCase;
import org.asvosonk.bank.domain.model.Loan;
import org.asvosonk.bank.domain.model.LoanRepayment;
import org.asvosonk.bank.domain.model.Saving;
import org.asvosonk.bank.domain.repository.LoanRepaymentRepository;
import org.asvosonk.bank.domain.repository.LoanRepository;
import org.asvosonk.bank.domain.repository.SavingRepository;
import org.asvosonk.bank.domain.valueobject.LoanStatus;
import org.asvosonk.bank.presentation.request.LoanForm;
import org.asvosonk.bank.presentation.request.RepaymentForm;
import org.asvosonk.bank.presentation.request.SavingForm;
import org.asvosonk.cashbox.domain.model.Cashbox;
import org.asvosonk.cashbox.domain.repository.CashboxRepository;
import org.asvosonk.cashbox.domain.valueobject.CashboxType;
import org.asvosonk.member.application.usecase.SearchMemberUseCase;
import org.asvosonk.member.domain.model.Member;
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
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Controller
@RequestMapping("/bank")
@RequiredArgsConstructor
public class BankController {

    private final SavingRepository            savingRepository;
    private final LoanRepository              loanRepository;
    private final LoanRepaymentRepository     repaymentRepository;
    private final CashboxRepository           cashboxRepository;
    private final RecordSavingUseCase         recordSavingUseCase;
    private final CreateLoanUseCase           createLoanUseCase;
    private final RecordLoanRepaymentUseCase  recordLoanRepaymentUseCase;
    private final GetMemberBankSummaryUseCase getMemberBankSummaryUseCase;
    private final SearchMemberUseCase         searchMemberUseCase;

    // ── Overview ─────────────────────────────────────────────

    @GetMapping
    @PreAuthorize("hasAuthority('BANK_VIEW')")
    public String overview(Model model) {
        Cashbox bankCashbox = cashboxRepository.findByType(CashboxType.bank)
            .orElse(null);
        BigDecimal totalBankSavings = bankCashbox != null ? bankCashbox.getBalance() : BigDecimal.ZERO;
        List<Loan> allLoans = loanRepository.findAll();
        List<Loan> activeAndOverdueLoans = allLoans.stream()
            .filter(l -> l.getStatus() != LoanStatus.repaid)
            .toList();

        // Build member name map (single batched query) and remaining balances
        Map<Long, String> memberNames = searchMemberUseCase.findNamesByIds(
            activeAndOverdueLoans.stream().map(Loan::getMemberId).distinct().toList());
        Map<Long, BigDecimal> remainingBalances = new HashMap<>();
        BigDecimal totalOutstanding = BigDecimal.ZERO;
        for (Loan loan : activeAndOverdueLoans) {
            BigDecimal totalRepaid = repaymentRepository.getTotalRepaidByLoanId(loan.getId());
            BigDecimal remaining = loan.getRemainingBalance(totalRepaid);
            remainingBalances.put(loan.getId(), remaining);
            totalOutstanding = totalOutstanding.add(remaining);
        }

        model.addAttribute("totalBankSavings", totalBankSavings);
        model.addAttribute("totalOutstanding", totalOutstanding);
        model.addAttribute("activeLoans", activeAndOverdueLoans);
        model.addAttribute("memberNames", memberNames);
        model.addAttribute("remainingBalances", remainingBalances);
        model.addAttribute("members", searchMemberUseCase.findAll());
        model.addAttribute("pageTitle", "Banque Annuelle");
        return "bank/overview";
    }

    // ── Member account detail ────────────────────────────────

    @GetMapping("/members/{id}")
    @PreAuthorize("hasAuthority('BANK_VIEW')")
    public String memberAccount(@PathVariable Long id, Model model) {
        Member member = searchMemberUseCase.findById(id);

        GetMemberBankSummaryUseCase.MemberBankSummary summary = getMemberBankSummaryUseCase.execute(id);

        // Add savings history
        List<Saving> savings = savingRepository.findByMemberIdOrderByOperationDateDesc(id);

        // Solde restant dû par emprunt en cours, et journal complet des
        // remboursements du membre : un remboursement enregistré ne laissait
        // aucune trace consultable, seule la barre de progression bougeait.
        Map<Long, BigDecimal> remainingByLoan = new HashMap<>();
        for (Loan loan : summary.getActiveLoans()) {
            remainingByLoan.put(loan.getId(),
                loan.getRemainingBalance(repaymentRepository.getTotalRepaidByLoanId(loan.getId())));
        }
        List<LoanRepayment> repayments = new ArrayList<>();
        for (Loan loan : summary.getLoanHistory()) {
            repayments.addAll(repaymentRepository.findByLoanId(loan.getId()));
        }
        repayments.sort(Comparator.comparing(LoanRepayment::getPaymentDate).reversed()
            .thenComparing(Comparator.comparing(LoanRepayment::getId).reversed()));

        model.addAttribute("member", member);
        model.addAttribute("summary", summary);
        model.addAttribute("savings", savings);
        model.addAttribute("remainingByLoan", remainingByLoan);
        model.addAttribute("repayments", repayments);
        model.addAttribute("savingForm", new SavingForm());
        model.addAttribute("loanForm", new LoanForm());
        model.addAttribute("repaymentForm", new RepaymentForm());
        model.addAttribute("pageTitle", "Compte bancaire — " + member.getFullName());
        return "bank/member-account";
    }

    /** Premier message de validation du formulaire, pour ne pas le perdre au redirect. */
    private String firstError(BindingResult result, String fallback) {
        return result.getFieldError() != null && result.getFieldError().getDefaultMessage() != null
            ? result.getFieldError().getDefaultMessage()
            : fallback;
    }

    // ── Record saving ────────────────────────────────────────

    @PostMapping("/members/{id}/save")
    @PreAuthorize("hasAuthority('BANK_SAVING_RECORD')")
    public String recordSaving(@PathVariable Long id,
                               @Valid @ModelAttribute("savingForm") SavingForm form,
                               BindingResult result,
                               @AuthenticationPrincipal UserDetailsImpl principal,
                               RedirectAttributes ra) {
        // Redirection plutôt que re-rendu : la page consomme désormais plusieurs
        // collections (historiques, soldes restants) qu'un re-rendu partiel
        // laissait absentes du modèle.
        if (result.hasErrors()) {
            ra.addFlashAttribute("errorMessage",
                "Épargne non enregistrée : " + firstError(result, "montant invalide."));
            return "redirect:/bank/members/" + id;
        }

        try {
            LocalDate date = form.getOperationDate() != null ? form.getOperationDate() : LocalDate.now();
            recordSavingUseCase.execute(id, form.getAmount(), date, principal.getAppUser());
            ra.addFlashAttribute("successMessage",
                "Épargne de " + form.getAmount() + " FCFA enregistrée.");
        } catch (Exception e) {
            ra.addFlashAttribute("errorMessage", e.getMessage());
        }
        return "redirect:/bank/members/" + id;
    }

    // ── Create loan ──────────────────────────────────────────

    @PostMapping("/members/{id}/loan")
    @PreAuthorize("hasAuthority('BANK_LOAN_CREATE')")
    public String createLoan(@PathVariable Long id,
                             @Valid @ModelAttribute("loanForm") LoanForm form,
                             BindingResult result,
                             @AuthenticationPrincipal UserDetailsImpl principal,
                             RedirectAttributes ra) {
        if (result.hasErrors()) {
            ra.addFlashAttribute("errorMessage",
                "Emprunt non enregistré : " + firstError(result, "montant invalide."));
            return "redirect:/bank/members/" + id;
        }

        try {
            Loan loan = createLoanUseCase.execute(id, form.getAmount(), principal.getAppUser());
            ra.addFlashAttribute("successMessage",
                "Emprunt de " + form.getAmount() + " FCFA créé (total dû : " + loan.getTotalDue() + " FCFA).");
        } catch (Exception e) {
            ra.addFlashAttribute("errorMessage", e.getMessage());
        }
        return "redirect:/bank/members/" + id;
    }

    // ── Record loan repayment ────────────────────────────────

    @PostMapping("/loans/{id}/repay")
    @PreAuthorize("hasAuthority('BANK_LOAN_REPAYMENT')")
    public String repayLoan(@PathVariable Long id,
                            @Valid @ModelAttribute("repaymentForm") RepaymentForm form,
                            BindingResult result,
                            @AuthenticationPrincipal UserDetailsImpl principal,
                            RedirectAttributes ra) {
        if (result.hasErrors()) {
            ra.addFlashAttribute("errorMessage",
                "Remboursement non enregistré : " + firstError(result, "montant invalide."));
            Loan loan = loanRepository.findById(id).orElse(null);
            if (loan != null) {
                return "redirect:/bank/members/" + loan.getMemberId();
            }
            return "redirect:/bank";
        }

        try {
            LoanRepayment repayment = recordLoanRepaymentUseCase.execute(id, form.getAmount(), principal.getAppUser());
            ra.addFlashAttribute("successMessage",
                "Remboursement de " + form.getAmount() + " FCFA enregistré.");

            // Redirect to the loan owner's account page
            Loan loan = loanRepository.findById(id).orElse(null);
            if (loan != null) {
                return "redirect:/bank/members/" + loan.getMemberId();
            }
        } catch (Exception e) {
            ra.addFlashAttribute("errorMessage", e.getMessage());
        }
        return "redirect:/bank";
    }
}
