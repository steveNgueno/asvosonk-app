package org.asvosonk.bank.application.usecase;

import lombok.RequiredArgsConstructor;
import org.asvosonk.bank.domain.model.Loan;
import org.asvosonk.bank.domain.repository.LoanRepository;
import org.asvosonk.bank.domain.repository.SavingRepository;
import org.asvosonk.cashbox.application.usecase.WithdrawMoneyUseCase;
import org.asvosonk.cashbox.domain.valueobject.CashboxType;
import org.asvosonk.cashbox.domain.valueobject.MovementOrigin;
import org.asvosonk.common.domain.exception.BusinessRuleException;
import org.asvosonk.member.domain.model.Member;
import org.asvosonk.member.domain.repository.MemberRepository;
import org.asvosonk.security.domain.model.AppUser;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

@Service
@RequiredArgsConstructor
public class CreateLoanUseCase {

    private final LoanRepository loanRepository;
    private final SavingRepository savingRepository;
    private final MemberRepository memberRepository;
    private final WithdrawMoneyUseCase withdrawMoneyUseCase;

    @Transactional
    public Loan execute(Long memberId, BigDecimal amount, AppUser user) {
        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessRuleException("Le montant de l'emprunt doit être positif.");
        }

        Member member = memberRepository.findById(memberId)
            .orElseThrow(() -> new BusinessRuleException("Membre introuvable."));

        if (!member.isActive()) {
            throw new BusinessRuleException("Le membre doit être actif pour emprunter.");
        }

        BigDecimal totalSavings = savingRepository.getTotalSavingsByMemberId(memberId);
        if (totalSavings.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessRuleException(
                "Le membre doit avoir des épargnes pour être éligible à un emprunt.");
        }

        long activeLoans = loanRepository.countActiveLoans(memberId);
        if (activeLoans >= 2) {
            throw new BusinessRuleException(
                "Maximum 2 emprunts simultanés autorisés. (" + activeLoans + " en cours)");
        }

        Loan loan = Loan.createNew(memberId, amount);
        Loan saved = loanRepository.save(loan);

        String reason = "Emprunt membre " + member.getFullName();
        withdrawMoneyUseCase.execute(CashboxType.bank, amount, reason,
            MovementOrigin.annual_bank, null, null, null, user);

        return saved;
    }
}
