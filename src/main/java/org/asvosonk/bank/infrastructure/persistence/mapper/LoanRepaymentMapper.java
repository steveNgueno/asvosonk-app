package org.asvosonk.bank.infrastructure.persistence.mapper;

import org.asvosonk.bank.domain.model.LoanRepayment;
import org.asvosonk.bank.infrastructure.persistence.entity.LoanRepaymentEntity;

public class LoanRepaymentMapper {

    public static LoanRepayment toDomain(LoanRepaymentEntity entity) {
        if (entity == null) return null;
        return new LoanRepayment(
            entity.getId(),
            entity.getLoanId(),
            entity.getPaymentDate(),
            entity.getAmount(),
            entity.getSessionId(),
            entity.getCreatedAt()
        );
    }

    public static LoanRepaymentEntity toEntity(LoanRepayment domain) {
        if (domain == null) return null;
        LoanRepaymentEntity entity = new LoanRepaymentEntity();
        entity.setId(domain.getId());
        entity.setLoanId(domain.getLoanId());
        entity.setPaymentDate(domain.getPaymentDate());
        entity.setAmount(domain.getAmount());
        entity.setSessionId(domain.getSessionId());
        return entity;
    }
}
