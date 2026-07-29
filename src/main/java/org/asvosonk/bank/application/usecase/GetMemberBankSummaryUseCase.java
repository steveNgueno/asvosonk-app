package org.asvosonk.bank.application.usecase;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.asvosonk.bank.domain.model.Loan;
import org.asvosonk.bank.domain.repository.LoanRepository;
import org.asvosonk.bank.domain.repository.SavingRepository;
import org.asvosonk.member.domain.repository.MemberRepository;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;

@Service
@RequiredArgsConstructor
public class GetMemberBankSummaryUseCase {

    private final SavingRepository savingRepository;
    private final LoanRepository loanRepository;
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
