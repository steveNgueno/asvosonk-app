package org.asvosonk.bank.infrastructure.persistence.mapper;

import org.asvosonk.bank.domain.model.Loan;
import org.asvosonk.bank.infrastructure.persistence.entity.LoanEntity;

public class LoanMapper {

    public static Loan toDomain(LoanEntity entity) {
        if (entity == null) return null;
        return new Loan(
            entity.getId(),
            entity.getMemberId(),
            entity.getLoanDate(),
            entity.getAmount(),
            entity.getInterestRate(),
            entity.getDurationMonths(),
            entity.getDueDate(),
            entity.getTotalDue(),
            entity.getStatus(),
            entity.getSessionId(),
            entity.getCreatedAt(),
            entity.getUpdatedAt()
        );
    }

    public static LoanEntity toEntity(Loan domain) {
        if (domain == null) return null;
        LoanEntity entity = new LoanEntity();
        entity.setId(domain.getId());
        entity.setMemberId(domain.getMemberId());
        entity.setLoanDate(domain.getLoanDate());
        entity.setAmount(domain.getAmount());
        entity.setInterestRate(domain.getInterestRate());
        entity.setDurationMonths(domain.getDurationMonths());
        entity.setDueDate(domain.getDueDate());
        entity.setTotalDue(domain.getTotalDue());
        entity.setStatus(domain.getStatus());
        // Le rattachement à la séance doit survivre à une réécriture : sans lui,
        // marquer l'emprunt « remboursé » repasserait par toEntity et effacerait
        // silencieusement la séance d'octroi.
        entity.setSessionId(domain.getSessionId());
        return entity;
    }
}
