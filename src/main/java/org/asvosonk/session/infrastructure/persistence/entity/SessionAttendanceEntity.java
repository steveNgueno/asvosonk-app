package org.asvosonk.session.infrastructure.persistence.entity;

import jakarta.persistence.*;
import lombok.*;
import org.asvosonk.member.infrastructure.persistence.entity.MemberEntity;
import org.asvosonk.session.domain.valueobject.AttendanceStatus;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "session_attendance",
       uniqueConstraints = @UniqueConstraint(columnNames = {"session_id", "member_id"}))
@Getter @Setter
@NoArgsConstructor
@EqualsAndHashCode(of = "id")
public class SessionAttendanceEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "session_id", nullable = false)
    private MeetingSessionEntity session;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "member_id", nullable = false)
    private MemberEntity member;

    @Column(name = "is_present", nullable = false)
    private boolean present = false;

    @Column(name = "amount_paid", nullable = false, precision = 10, scale = 2)
    private BigDecimal amountPaid = BigDecimal.ZERO;

    /**
     * Montant réellement dû par le membre pour cette séance : 2 000 FCFA en
     * temps normal, 1 000 FCFA (boisson + développement, sans part de tontine)
     * lorsque le bénéficiaire du jour a rejoint le tour après que ce membre a
     * déjà bénéficié.
     */
    @Column(name = "amount_due", nullable = false, precision = 10, scale = 2)
    private BigDecimal amountDue = new BigDecimal("2000");

    /**
     * Séance au cours de laquelle un échec sur CETTE séance a été recouvert.
     * Tant qu'elle est nulle et que le statut est {@code default_status}, la
     * séance reste due par le membre.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "recovered_session_id")
    private MeetingSessionEntity recoveredSession;

    @Column(name = "covered_by_fund", nullable = false)
    private boolean coveredByFund = false;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name = "attendance_status", nullable = false, columnDefinition = "attendance_status")
    private AttendanceStatus attendanceStatus = AttendanceStatus.up_to_date;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt = LocalDateTime.now();

    @PreUpdate
    public void preUpdate() { this.updatedAt = LocalDateTime.now(); }

    /** Part de tontine portée par cette cotisation (1 000 FCFA, ou 0 si le membre ne doit que la boisson + le développement). */
    public BigDecimal tontineShare() {
        return amountDue.subtract(new BigDecimal("1000")).max(BigDecimal.ZERO);
    }

    /** Échec de cotisation encore dû (ni recouvert en espèces, ni repris par le fond). */
    public boolean isOpenFailure() {
        return attendanceStatus == AttendanceStatus.default_status && recoveredSession == null;
    }
}
