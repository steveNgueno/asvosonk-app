package org.asvosonk.tontine.infrastructure.persistence.entity;

import jakarta.persistence.*;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Recouvrement d'un échec de Grande Tontine : versement tardif, en séance
 * {@code sessionId}, de la cotisation que {@code contributorId} n'avait pas
 * faite à {@code beneficiaryId} lors de l'échec {@code failedContributionId}.
 */
@Entity
@Table(name = "tontine_recovery")
@Getter @Setter
@NoArgsConstructor
@EqualsAndHashCode(of = "id")
public class TontineRecoveryEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tour_id", nullable = false)
    private Long tourId;

    @Column(name = "failed_contribution_id", nullable = false, unique = true)
    private Long failedContributionId;

    @Column(name = "session_id", nullable = false)
    private Long sessionId;

    @Column(name = "contributor_id", nullable = false)
    private Long contributorId;

    @Column(name = "beneficiary_id", nullable = false)
    private Long beneficiaryId;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal amount;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @PrePersist
    public void prePersist() { this.createdAt = LocalDateTime.now(); }
}
