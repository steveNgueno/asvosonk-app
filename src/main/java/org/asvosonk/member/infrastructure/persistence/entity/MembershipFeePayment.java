package org.asvosonk.member.infrastructure.persistence.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.asvosonk.security.infrastructure.persistence.entity.AppUserEntity;
import org.asvosonk.session.infrastructure.persistence.entity.MeetingSessionEntity;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Un versement de frais d'adhésion.
 *
 * <p>Un frais se règle par avances successives : chaque versement est consigné
 * ici, rattaché à la séance au cours de laquelle il a été remis. Ce rattachement
 * est obligatoire — les frais ne se paient qu'en séance — et c'est lui qui fait
 * entrer la somme dans les entrées du jour, donc dans le total remis au
 * trésorier. L'argent ne passe par aucune caisse : il va directement au
 * trésorier.</p>
 */
@Entity
@Table(name = "membership_fee_payment")
@Getter
@Setter
public class MembershipFeePayment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "fee_id", nullable = false)
    private MembershipFee fee;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "session_id", nullable = false)
    private MeetingSessionEntity session;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal amount;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "recorded_by")
    private AppUserEntity recordedBy;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();
}
