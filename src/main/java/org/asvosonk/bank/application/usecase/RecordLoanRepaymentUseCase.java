package org.asvosonk.bank.application.usecase;

import lombok.RequiredArgsConstructor;
import org.asvosonk.bank.domain.model.Loan;
import org.asvosonk.bank.domain.model.LoanRepayment;
import org.asvosonk.bank.domain.repository.LoanRepaymentRepository;
import org.asvosonk.bank.domain.repository.LoanRepository;
import org.asvosonk.cashbox.application.usecase.DepositMoneyUseCase;
import org.asvosonk.cashbox.domain.valueobject.CashboxType;
import org.asvosonk.cashbox.domain.valueobject.MovementOrigin;
import org.asvosonk.common.domain.exception.BusinessRuleException;
import org.asvosonk.common.domain.exception.ResourceNotFoundException;
import org.asvosonk.member.domain.model.Member;
import org.asvosonk.member.domain.repository.MemberRepository;
import org.asvosonk.security.domain.model.AppUser;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class RecordLoanRepaymentUseCase {

    private final LoanRepository loanRepository;
    private final LoanRepaymentRepository repaymentRepository;
    private final MemberRepository memberRepository;
    private final DepositMoneyUseCase depositMoneyUseCase;

    @Transactional
    public LoanRepayment execute(Long loanId, BigDecimal amount, AppUser user) {
        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessRuleException("Le montant du remboursement doit être positif.");
        }

        Loan loan = loanRepository.findById(loanId)
            .orElseThrow(() -> new ResourceNotFoundException("Emprunt", loanId));

        if (loan.isRepaid()) {
            throw new BusinessRuleException("Cet emprunt est déjà remboursé.");
        }

        // F-19 — Reject over-repayment. Without this, any positive amount was
        // accepted and deposited into the bank cashbox, so a repayment larger
        // than what remains due over-credited the treasury and left the loan
        // "repaid" while the books showed more cash than was actually owed.
        BigDecimal alreadyRepaid = repaymentRepository.getTotalRepaidByLoanId(loanId);
        BigDecimal remaining = loan.getRemainingBalance(alreadyRepaid);
        if (amount.compareTo(remaining) > 0) {
            throw new BusinessRuleException(
                "Le remboursement dépasse le solde restant dû (" + remaining + " FCFA).");
        }

        // Record repayment
        LoanRepayment repayment = new LoanRepayment(
            null, loanId, LocalDate.now(), amount, LocalDateTime.now());
        LoanRepayment saved = repaymentRepository.save(repayment);

        // Check if fully repaid
        BigDecimal totalRepaid = alreadyRepaid.add(amount);
        if (totalRepaid.compareTo(loan.getTotalDue()) >= 0) {
            loan.markAsRepaid();
            loanRepository.save(loan);
        }

        // Get member for reason
        Member member = memberRepository.findById(loan.getMemberId())
            .orElse(null);
        String memberName = member != null ? member.getFullName() : "Inconnu";

        String reason = "Remboursement emprunt #" + loanId + " — " + memberName;
        depositMoneyUseCase.execute(CashboxType.bank, amount, reason,
            MovementOrigin.annual_bank, null, null, null, user);

        return saved;
    }
}
