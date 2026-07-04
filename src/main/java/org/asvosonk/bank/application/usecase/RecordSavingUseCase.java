package org.asvosonk.bank.application.usecase;

import lombok.RequiredArgsConstructor;
import org.asvosonk.bank.domain.model.Saving;
import org.asvosonk.bank.domain.repository.SavingRepository;
import org.asvosonk.cashbox.application.usecase.DepositMoneyUseCase;
import org.asvosonk.cashbox.domain.valueobject.CashboxType;
import org.asvosonk.cashbox.domain.valueobject.MovementOrigin;
import org.asvosonk.common.domain.exception.BusinessRuleException;
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
public class RecordSavingUseCase {

    private final SavingRepository savingRepository;
    private final MemberRepository memberRepository;
    private final DepositMoneyUseCase depositMoneyUseCase;

    @Transactional
    public Saving execute(Long memberId, BigDecimal amount, LocalDate operationDate, AppUser user) {
        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessRuleException("Le montant d'épargne doit être positif.");
        }

        Member member = memberRepository.findById(memberId)
            .orElseThrow(() -> new BusinessRuleException("Membre introuvable."));

        if (!member.isActive()) {
            throw new BusinessRuleException("Le membre n'est pas actif.");
        }

        Saving saving = new Saving(null, memberId, operationDate, amount, LocalDateTime.now());
        Saving saved = savingRepository.save(saving);

        String reason = "Épargne membre " + member.getFullName();
        depositMoneyUseCase.execute(CashboxType.bank, amount, reason,
            MovementOrigin.annual_bank, null, null, null, user);

        return saved;
    }
}
