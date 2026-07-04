package org.asvosonk.tontine.infrastructure.persistence.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "tontine_participant",
       uniqueConstraints = {
           @UniqueConstraint(columnNames = {"tour_id", "member_id"}),
           @UniqueConstraint(columnNames = {"tour_id", "draw_order"})
       })
@Getter @Setter
@NoArgsConstructor
@EqualsAndHashCode(of = "id")
public class TontineParticipantEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tour_id", nullable = false)
    private Long tourId;

    @Column(name = "member_id", nullable = false)
    private Long memberId;

    @Column(name = "draw_order", nullable = false)
    private int drawOrder;

    @Column(name = "has_benefited", nullable = false)
    private boolean hasBenefited = false;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @PrePersist
    public void prePersist() { this.createdAt = LocalDateTime.now(); }
}
