package org.asvosonk.tontine.infrastructure.persistence.entity;

import jakarta.persistence.*;
import lombok.*;
import org.asvosonk.tontine.domain.valueobject.DebtStatus;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
// F-61 — The unique constraint lives in the DB migration (V1). Declaring it here
// too only affects Hibernate schema generation (create/update), which this app
// never uses (ddl-auto=validate). Keeping it was dead config that would become a
// trap if ddl-auto were ever switched to update.
@Table(name = "tontine_debt")
@Getter @Setter
@NoArgsConstructor
@EqualsAndHashCode(of = "id")
public class TontineDebtEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tour_id", nullable = false)
    private Long tourId;

    @Column(name = "debtor_id", nullable = false)
    private Long debtorId;

    @Column(name = "creditor_id", nullable = false)
    private Long creditorId;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal amount;

    @Column(name = "origin_session_id", nullable = false)
    private Long originSessionId;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(nullable = false, columnDefinition = "debt_status")
    private DebtStatus status = DebtStatus.owed;

    @Column(name = "repayment_session_id")
    private Long repaymentSessionId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt = LocalDateTime.now();

    @PrePersist
    public void prePersist() {
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    public void preUpdate() { this.updatedAt = LocalDateTime.now(); }
}
