package org.asvosonk.bank.infrastructure.persistence.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "saving")
@Getter @Setter
@NoArgsConstructor
@EqualsAndHashCode(of = "id")
public class SavingEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "member_id", nullable = false)
    private Long memberId;

    @Column(name = "operation_date", nullable = false)
    private LocalDate operationDate;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal amount;

    /** Séance au cours de laquelle l'épargne a été saisie, null si hors séance. */
    @Column(name = "session_id")
    private Long sessionId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @PrePersist
    public void prePersist() { this.createdAt = LocalDateTime.now(); }
}
