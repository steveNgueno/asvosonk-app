package org.asvosonk.aid.infrastructure.persistence.entity;

import jakarta.persistence.*;
import lombok.*;
import org.asvosonk.aid.domain.valueobject.AidContributionStatus;
import org.asvosonk.aid.domain.valueobject.AidPaymentMode;
import org.asvosonk.member.infrastructure.persistence.entity.MemberEntity;
import org.asvosonk.session.infrastructure.persistence.entity.MeetingSessionEntity;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "aid_contribution")
@Getter @Setter
@NoArgsConstructor
@EqualsAndHashCode(of = "id")
public class AidContributionEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "aid_id", nullable = false)
    private AidEntity aid;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "member_id", nullable = false)
    private MemberEntity member;

    /** Part fixée à la création de l'aide (instantané). */
    @Column(name = "amount_due", nullable = false, precision = 10, scale = 2)
    private BigDecimal amountDue = BigDecimal.ZERO;

    /** Montant déjà recouvré : une retenue sur tontine peut n'en couvrir qu'une partie. */
    @Column(name = "amount_paid", nullable = false, precision = 10, scale = 2)
    private BigDecimal amountPaid = BigDecimal.ZERO;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(nullable = false, columnDefinition = "aid_contribution_status")
    private AidContributionStatus status = AidContributionStatus.owed;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name = "payment_mode", columnDefinition = "aid_payment_mode")
    private AidPaymentMode paymentMode;

    @Column(name = "payment_date")
    private LocalDate paymentDate;

    /** Séance au cours de laquelle la part a été recouverte (entrée du jour). */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "session_id")
    private MeetingSessionEntity session;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt = LocalDateTime.now();

    @PreUpdate
    public void preUpdate() { this.updatedAt = LocalDateTime.now(); }

    public boolean isOwed() { return status == AidContributionStatus.owed; }

    /** Reste à recouvrir sur cette part. */
    public BigDecimal remaining() {
        return amountDue.subtract(amountPaid).max(BigDecimal.ZERO);
    }

    /**
     * Encaisse {@code collected} sur cette part et la solde si elle est
     * entièrement couverte.
     *
     * @return le montant réellement encaissé
     */
    public BigDecimal collect(BigDecimal collected, AidPaymentMode mode, LocalDate paidOn) {
        BigDecimal taken = collected.min(remaining()).max(BigDecimal.ZERO);
        if (taken.signum() <= 0) {
            return BigDecimal.ZERO;
        }
        this.amountPaid = this.amountPaid.add(taken);
        if (this.amountPaid.compareTo(this.amountDue) >= 0) {
            this.amountPaid = this.amountDue;
            this.status = AidContributionStatus.paid;
            this.paymentDate = paidOn;
        }
        this.paymentMode = mode;
        return taken;
    }
}
