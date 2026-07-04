package org.asvosonk.bank.domain.model;

import lombok.EqualsAndHashCode;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Getter
@EqualsAndHashCode(of = "id")
public class LoanRepayment {

    private final Long id;
    private final Long loanId;
    private final LocalDate paymentDate;
    private final BigDecimal amount;
    private final LocalDateTime createdAt;

    public LoanRepayment(Long id, Long loanId, LocalDate paymentDate,
                         BigDecimal amount, LocalDateTime createdAt) {
        this.id = id;
        this.loanId = loanId;
        this.paymentDate = paymentDate;
        this.amount = amount;
        this.createdAt = createdAt;
    }
}
