package org.asvosonk.presence.infrastructure.persistence.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "presence_tour_participant",
       uniqueConstraints = {
           @UniqueConstraint(columnNames = {"tour_id", "member_id"}),
           @UniqueConstraint(columnNames = {"tour_id", "draw_order"})
       })
@Getter @Setter
@NoArgsConstructor
@EqualsAndHashCode(of = "id")
public class PresenceTourParticipantEntity {

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

    @Column(name = "session_id")
    private Long sessionId;

    /** Date d'entrée du membre dans le tour (date de début du tour, ou date d'adhésion). */
    @Column(name = "joined_at", nullable = false)
    private java.time.LocalDate joinedAt = java.time.LocalDate.now();

    /**
     * Membre ayant rejoint le tour après son démarrage : il ne participe pas aux
     * tirages et bénéficie en dernier ; à son tour, les membres qui avaient déjà
     * bénéficié avant son arrivée ne lui doivent que 1 000 FCFA.
     */
    @Column(name = "joined_mid_tour", nullable = false)
    private boolean joinedMidTour = false;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @PrePersist
    public void prePersist() { this.createdAt = LocalDateTime.now(); }
}
