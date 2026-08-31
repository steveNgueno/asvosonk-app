package org.asvosonk.aid.infrastructure.persistence.entity;

import jakarta.persistence.*;
import lombok.*;
import org.asvosonk.aid.domain.valueobject.AidStatus;
import org.asvosonk.aid.domain.valueobject.AidType;
import org.asvosonk.member.infrastructure.persistence.entity.MemberEntity;
import org.asvosonk.session.infrastructure.persistence.entity.MeetingSessionEntity;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "aid")
@Getter @Setter
@NoArgsConstructor
@EqualsAndHashCode(of = "id")
public class AidEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "beneficiary_id", nullable = false)
    private MemberEntity beneficiary;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name = "aid_type", nullable = false, columnDefinition = "aid_type")
    private AidType type = AidType.autre;

    @Column(name = "aid_date", nullable = false)
    private LocalDate aidDate;

    @Column
    private String description;

    /** Somme remise au membre concerné. */
    @Column(name = "total_amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal totalAmount;

    /** Part que chaque membre concerné doit recouvrer (fixée par la réunion). */
    @Column(name = "share_per_member", nullable = false, precision = 10, scale = 2)
    private BigDecimal sharePerMember = BigDecimal.ZERO;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(nullable = false, columnDefinition = "aid_status")
    private AidStatus status = AidStatus.in_progress;

    /** Séance au cours de laquelle l'aide a été enregistrée. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "session_id")
    private MeetingSessionEntity session;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt = LocalDateTime.now();

    @PreUpdate
    public void preUpdate() { this.updatedAt = LocalDateTime.now(); }
}
