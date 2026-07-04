package org.asvosonk.bank.application.usecase;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.asvosonk.bank.domain.model.Loan;
import org.asvosonk.bank.domain.repository.LoanRepository;
import org.asvosonk.bank.domain.repository.SavingRepository;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;

@Service
@RequiredArgsConstructor
public class GetMemberBankSummaryUseCase {

    private final SavingRepository savingRepository;
    private final LoanRepository loanRepository;

    public MemberBankSummary execute(Long memberId) {
        BigDecimal totalSavings = savingRepository.getTotalSavingsByMemberId(memberId);
        List<Loan> allLoans = loanRepository.findByMemberIdOrderByLoanDateDesc(memberId);
        List<Loan> activeLoans = allLoans.stream()
            .filter(Loan::isActive)
            .toList();
        boolean eligibleForLoan = totalSavings.compareTo(BigDecimal.ZERO) > 0
            && activeLoans.size() < 2;

        return new MemberBankSummary(totalSavings, activeLoans, allLoans, eligibleForLoan);
    }

    @Getter
    @RequiredArgsConstructor
    public static class MemberBankSummary {
        private final BigDecimal totalSavings;
        private final List<Loan> activeLoans;
        private final List<Loan> loanHistory;
        private final boolean eligibleForLoan;
    }
}
