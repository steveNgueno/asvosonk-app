package org.asvosonk.bank.application.usecase;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.asvosonk.bank.domain.model.Loan;
import org.asvosonk.bank.domain.repository.LoanRepaymentRepository;
import org.asvosonk.bank.domain.repository.LoanRepository;
import org.asvosonk.bank.domain.repository.SavingRepository;
import org.asvosonk.member.domain.repository.MemberRepository;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class GetMemberBankSummaryUseCase {

    private final SavingRepository savingRepository;
    private final LoanRepository loanRepository;
    private final LoanRepaymentRepository loanRepaymentRepository;
    private final MemberRepository memberRepository;

    public MemberBankSummary execute(Long memberId) {
        BigDecimal totalSavings = savingRepository.getTotalSavingsByMemberId(memberId);
        List<Loan> allLoans = loanRepository.findByMemberIdOrderByLoanDateDesc(memberId);
        List<Loan> activeLoans = allLoans.stream()
            .filter(Loan::isOutstanding)
            .toList();
        // F-47 — mirror the actual rule in CreateLoanUseCase: the member must be
        // active. Omitting it let the UI advertise eligibility for a member the
        // loan use case would then reject.
        boolean isActive = memberRepository.findById(memberId)
            .map(org.asvosonk.member.domain.model.Member::isActive)
            .orElse(false);
        boolean eligibleForLoan = isActive
            && totalSavings.compareTo(BigDecimal.ZERO) > 0
            && activeLoans.size() < 2;

        // F-55 — the progress bar needs the real repayment ratio per loan, not
        // a 0%/100% guess based on isOverdue(). Computed here (not in the
        // template) to keep the arithmetic in testable Java code.
        Map<Long, Integer> repaymentPercentByLoanId = new HashMap<>();
        for (Loan loan : activeLoans) {
            BigDecimal repaid = loanRepaymentRepository.getTotalRepaidByLoanId(loan.getId());
            int pct = loan.getTotalDue().signum() == 0 ? 0
                : repaid.multiply(BigDecimal.valueOf(100))
                    .divide(loan.getTotalDue(), 0, java.math.RoundingMode.DOWN)
                    .min(BigDecimal.valueOf(100))
                    .intValue();
            repaymentPercentByLoanId.put(loan.getId(), pct);
        }

        return new MemberBankSummary(totalSavings, activeLoans, allLoans, eligibleForLoan, repaymentPercentByLoanId);
    }

    @Getter
    @RequiredArgsConstructor
    public static class MemberBankSummary {
        private final BigDecimal totalSavings;
        private final List<Loan> activeLoans;
        private final List<Loan> loanHistory;
        private final boolean eligibleForLoan;
        private final Map<Long, Integer> repaymentPercentByLoanId;
    }
}
