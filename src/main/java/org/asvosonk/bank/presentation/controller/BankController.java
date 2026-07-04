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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

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

        // Build member name map and remaining balances
        Map<Long, String> memberNames = new HashMap<>();
        Map<Long, BigDecimal> remainingBalances = new HashMap<>();
        for (Loan loan : activeAndOverdueLoans) {
            Member member = searchMemberUseCase.findById(loan.getMemberId());
            memberNames.put(loan.getMemberId(), member.getFullName());
            BigDecimal totalRepaid = repaymentRepository.getTotalRepaidByLoanId(loan.getId());
            remainingBalances.put(loan.getId(), loan.getRemainingBalance(totalRepaid));
        }

        model.addAttribute("totalBankSavings", totalBankSavings);
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

        model.addAttribute("member", member);
        model.addAttribute("summary", summary);
        model.addAttribute("savings", savings);
        model.addAttribute("savingForm", new SavingForm());
        model.addAttribute("loanForm", new LoanForm());
        model.addAttribute("repaymentForm", new RepaymentForm());
        model.addAttribute("pageTitle", "Compte bancaire — " + member.getFullName());
        return "bank/member-account";
    }

    // ── Record saving ────────────────────────────────────────

    @PostMapping("/members/{id}/save")
    @PreAuthorize("hasAuthority('BANK_SAVING_RECORD')")
    public String recordSaving(@PathVariable Long id,
                               @Valid @ModelAttribute("savingForm") SavingForm form,
                               BindingResult result,
                               @AuthenticationPrincipal UserDetailsImpl principal,
                               Model model,
                               RedirectAttributes ra) {
        if (result.hasErrors()) {
            Member member = searchMemberUseCase.findById(id);
            GetMemberBankSummaryUseCase.MemberBankSummary summary = getMemberBankSummaryUseCase.execute(id);
            model.addAttribute("member", member);
            model.addAttribute("summary", summary);
            model.addAttribute("loanForm", new LoanForm());
            model.addAttribute("repaymentForm", new RepaymentForm());
            model.addAttribute("pageTitle", "Compte bancaire — " + member.getFullName());
            return "bank/member-account";
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
                             Model model,
                             RedirectAttributes ra) {
        if (result.hasErrors()) {
            Member member = searchMemberUseCase.findById(id);
            GetMemberBankSummaryUseCase.MemberBankSummary summary = getMemberBankSummaryUseCase.execute(id);
            model.addAttribute("member", member);
            model.addAttribute("summary", summary);
            model.addAttribute("savingForm", new SavingForm());
            model.addAttribute("repaymentForm", new RepaymentForm());
            model.addAttribute("pageTitle", "Compte bancaire — " + member.getFullName());
            return "bank/member-account";
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
            ra.addFlashAttribute("errorMessage", "Montant de remboursement invalide.");
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
