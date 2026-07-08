package org.asvosonk.sanction.infrastructure.persistence.entity;

import jakarta.persistence.*;
import lombok.*;
import org.asvosonk.member.infrastructure.persistence.entity.MemberEntity;
import org.asvosonk.sanction.domain.valueobject.SanctionOrigin;
import org.asvosonk.sanction.domain.valueobject.SanctionStatus;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "sanction")
@Getter @Setter
@NoArgsConstructor
@EqualsAndHashCode(of = "id")
public class SanctionEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "member_id", nullable = false)
    private MemberEntity member;

    @Column(name = "sanction_date", nullable = false)
    private LocalDate sanctionDate;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal amount;

    @Column(nullable = false)
    private String reason;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(nullable = false, columnDefinition = "sanction_origin")
    private SanctionOrigin origin = SanctionOrigin.manual;

    @Column(name = "reference_id")
    private Long referenceId;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(nullable = false, columnDefinition = "sanction_status")
    private SanctionStatus status = SanctionStatus.unpaid;

    @Column(name = "payment_date")
    private LocalDate paymentDate;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt = LocalDateTime.now();

    @PreUpdate
    public void preUpdate() { this.updatedAt = LocalDateTime.now(); }

    public boolean isUnpaid() { return status == SanctionStatus.unpaid; }
}
