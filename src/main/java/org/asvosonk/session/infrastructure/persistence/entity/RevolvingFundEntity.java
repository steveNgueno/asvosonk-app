package org.asvosonk.session.infrastructure.persistence.entity;

import jakarta.persistence.*;
import lombok.*;
import org.asvosonk.member.infrastructure.persistence.entity.MemberEntity;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "revolving_fund")
@Getter @Setter
@NoArgsConstructor
@EqualsAndHashCode(of = "id")
public class RevolvingFundEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "member_id", nullable = false, unique = true)
    private MemberEntity member;

    // F-03 : le fonds démarre vide ; il est alimenté par le paiement réel du
    // frais d'adhésion 'revolving_fund' (voir RecordFeePaymentUseCase).
    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal balance = BigDecimal.ZERO;

    /** Optimistic-lock guard against concurrent balance updates (F-16). */
    @Version
    @Column(nullable = false)
    private long version;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt = LocalDateTime.now();

    @PreUpdate
    public void preUpdate() { this.updatedAt = LocalDateTime.now(); }

    public boolean hasSufficientBalance(BigDecimal amount) {
        return balance.compareTo(amount) >= 0;
    }
}
