package org.asvosonk.bank.domain.model;

import lombok.EqualsAndHashCode;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Getter
@EqualsAndHashCode(of = "id")
public class Saving {

    private final Long id;
    private final Long memberId;
    private final LocalDate operationDate;
    private final BigDecimal amount;
    /** Séance de saisie, ou null pour une épargne enregistrée hors séance. */
    private final Long sessionId;
    private final LocalDateTime createdAt;

    public Saving(Long id, Long memberId, LocalDate operationDate,
                  BigDecimal amount, Long sessionId, LocalDateTime createdAt) {
        this.id = id;
        this.memberId = memberId;
        this.operationDate = operationDate;
        this.amount = amount;
        this.sessionId = sessionId;
        this.createdAt = createdAt;
    }
}
