package org.asvosonk.bank.application.usecase;

import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.asvosonk.bank.domain.model.Loan;
import org.asvosonk.bank.domain.repository.LoanRepaymentRepository;
import org.asvosonk.bank.domain.repository.LoanRepository;
import org.asvosonk.bank.domain.repository.SavingRepository;
import org.asvosonk.cashbox.application.usecase.WithdrawMoneyUseCase;
import org.asvosonk.cashbox.domain.valueobject.CashboxType;
import org.asvosonk.cashbox.domain.valueobject.MovementOrigin;
import org.asvosonk.common.domain.exception.BusinessRuleException;
import org.asvosonk.member.domain.model.Member;
import org.asvosonk.member.domain.repository.MemberRepository;
import org.asvosonk.member.infrastructure.persistence.entity.MemberEntity;
import org.asvosonk.security.domain.model.AppUser;
import org.asvosonk.session.infrastructure.persistence.entity.MeetingSessionEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

@Service
@RequiredArgsConstructor
public class CreateLoanUseCase {

    // F-18 — a loan must be backed by the member's savings: bureau decision,
    // outstanding capital + new amount <= totalSavings * LOAN_TO_SAVINGS_RATIO (3x).
    private static final BigDecimal LOAN_TO_SAVINGS_RATIO = new BigDecimal("3");

    private final LoanRepository loanRepository;
    private final LoanRepaymentRepository loanRepaymentRepository;
    private final SavingRepository savingRepository;
    private final MemberRepository memberRepository;
    private final WithdrawMoneyUseCase withdrawMoneyUseCase;
    private final EntityManager entityManager;

    /**
     * Montant que le membre peut encore emprunter : 3 × son épargne, moins le
     * capital encore dû sur ses emprunts en cours (intérêts exclus).
     */
    @Transactional(readOnly = true)
    public BigDecimal loanCapacity(Long memberId) {
        return loanCapacity(savingRepository.getTotalSavingsByMemberId(memberId), outstandingCapital(memberId));
    }

    private static BigDecimal loanCapacity(BigDecimal totalSavings, BigDecimal outstandingCapital) {
        return totalSavings.multiply(LOAN_TO_SAVINGS_RATIO).subtract(outstandingCapital).max(BigDecimal.ZERO);
    }

    private BigDecimal outstandingCapital(Long memberId) {
        return loanRepository.findByMemberIdOrderByLoanDateDesc(memberId).stream()
            .filter(Loan::isOutstanding)
            .map(loan -> loan.getOutstandingCapital(loanRepaymentRepository.getTotalRepaidByLoanId(loan.getId())))
            .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    @Transactional
    public Loan execute(Long memberId, BigDecimal amount, AppUser user) {
        return execute(memberId, amount, null, user);
    }

    @Transactional
    public Loan execute(Long memberId, BigDecimal amount,
                        MeetingSessionEntity session, AppUser user) {
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

        // Le plafond porte sur l'endettement total, pas sur chaque emprunt : deux
        // emprunts de 3 × l'épargne chacun portaient la dette à 6 × l'épargne.
        BigDecimal outstandingCapital = outstandingCapital(memberId);
        BigDecimal capacity = loanCapacity(totalSavings, outstandingCapital);
        if (amount.compareTo(capacity) > 0) {
            throw new BusinessRuleException(
                "L'endettement ne peut pas dépasser 3 fois l'épargne du membre ("
                    + totalSavings.multiply(LOAN_TO_SAVINGS_RATIO).toBigInteger() + " FCFA). "
                    + "Capital encore dû sur les emprunts en cours : " + outstandingCapital.toBigInteger()
                    + " FCFA ; montant empruntable aujourd'hui : " + capacity.toBigInteger() + " FCFA.");
        }

        long activeLoans = loanRepository.countActiveLoans(memberId);
        if (activeLoans >= 2) {
            throw new BusinessRuleException(
                "Maximum 2 emprunts simultanés autorisés. (" + activeLoans + " en cours)");
        }

        // Le rattachement à la séance est porté par l'emprunt lui-même : le
        // rapport de séance chiffre dessus le montant décaissé.
        Loan loan = Loan.createNew(memberId, amount, session != null ? session.getId() : null);
        Loan saved = loanRepository.save(loan);

        // F-52 — trace the cashbox withdrawal back to the member and the loan.
        String reason = "Emprunt membre " + member.getFullName();
        withdrawMoneyUseCase.execute(CashboxType.bank, amount, reason,
            MovementOrigin.annual_bank, session,
            entityManager.getReference(MemberEntity.class, memberId), saved.getId(), user);

        return saved;
    }
}
