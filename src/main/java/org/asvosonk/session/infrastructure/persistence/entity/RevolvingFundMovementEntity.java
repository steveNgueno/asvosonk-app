package org.asvosonk.session.infrastructure.persistence.entity;

import jakarta.persistence.*;
import lombok.*;
import org.asvosonk.session.domain.valueobject.FundMovementType;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "revolving_fund_movement")
@Getter @Setter
@NoArgsConstructor
@EqualsAndHashCode(of = "id")
public class RevolvingFundMovementEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "fund_id", nullable = false)
    private RevolvingFundEntity fund;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "session_id", nullable = false)
    private MeetingSessionEntity session;

    @Enumerated(EnumType.STRING)
    @Column(name = "movement_type", nullable = false, columnDefinition = "fund_movement_type")
    private FundMovementType movementType;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal amount;

    @Column(name = "is_recovered", nullable = false)
    private boolean recovered = false;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();
}
