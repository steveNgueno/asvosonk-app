package org.asvosonk.tontine.infrastructure.persistence.entity;

import jakarta.persistence.*;
import lombok.*;
import org.asvosonk.tontine.domain.valueobject.PaymentStatus;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "tontine_contribution",
       uniqueConstraints = @UniqueConstraint(columnNames = {"tour_id", "session_id", "contributor_id", "beneficiary_id"}))
@Getter @Setter
@NoArgsConstructor
@EqualsAndHashCode(of = "id")
public class TontineContributionEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tour_id", nullable = false)
    private Long tourId;

    @Column(name = "session_id", nullable = false)
    private Long sessionId;

    @Column(name = "contributor_id", nullable = false)
    private Long contributorId;

    @Column(name = "beneficiary_id", nullable = false)
    private Long beneficiaryId;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal amount = BigDecimal.ZERO;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(nullable = false, columnDefinition = "payment_status")
    private PaymentStatus status = PaymentStatus.paid;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @PrePersist
    public void prePersist() { this.createdAt = LocalDateTime.now(); }
}
