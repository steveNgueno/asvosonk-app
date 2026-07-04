package org.asvosonk.member.infrastructure.persistence.entity;

import jakarta.persistence.*;
import lombok.*;
import org.asvosonk.member.domain.valueobject.FeeType;
import org.asvosonk.member.infrastructure.persistence.entity.MemberEntity;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "membership_fee",
       uniqueConstraints = @UniqueConstraint(columnNames = {"member_id", "fee_type"}))
@Getter @Setter
@NoArgsConstructor
@EqualsAndHashCode(of = "id")
public class MembershipFee {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "member_id", nullable = false)
    private MemberEntity member;

    @Enumerated(EnumType.STRING)
    @Column(name = "fee_type", nullable = false, columnDefinition = "fee_type")
    private FeeType feeType;

    @Column(name = "amount_due", nullable = false, precision = 10, scale = 2)
    private BigDecimal amountDue;

    @Column(name = "amount_paid", nullable = false, precision = 10, scale = 2)
    private BigDecimal amountPaid = BigDecimal.ZERO;

    @Column(name = "payment_date")
    private LocalDate paymentDate;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt = LocalDateTime.now();

    @PreUpdate
    public void preUpdate() { this.updatedAt = LocalDateTime.now(); }

    // ── Helpers ──────────────────────────────────────────────

    public BigDecimal getBalance() {
        return amountDue.subtract(amountPaid);
    }

    public boolean isFullyPaid() {
        return amountPaid.compareTo(amountDue) >= 0;
    }
}
