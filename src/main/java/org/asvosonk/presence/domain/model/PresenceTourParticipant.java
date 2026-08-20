package org.asvosonk.presence.domain.model;

import lombok.EqualsAndHashCode;
import lombok.Getter;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Participation d'un membre à un tour de présence.
 *
 * <p>Deux catégories de participants :
 * <ul>
 *   <li>les <strong>fondateurs</strong> du tour, présents à son démarrage : ce sont
 *       eux qui participent au tirage au sort hebdomadaire tant qu'ils n'ont pas
 *       bénéficié ;</li>
 *   <li>les <strong>arrivants en cours de tour</strong> ({@link #joinedMidTour}) :
 *       ils ne participent pas aux tirages et bénéficient en dernier, dans leur
 *       ordre d'arrivée. À leur tour, les membres ayant déjà bénéficié avant leur
 *       arrivée ne doivent que 1 000 FCFA (boisson + développement), puisque
 *       l'arrivant n'avait pas cotisé pour eux.</li>
 * </ul>
 */
@Getter
@EqualsAndHashCode(of = "id")
public class PresenceTourParticipant {

    private final Long id;
    private final Long tourId;
    private final Long memberId;
    private int drawOrder;
    private boolean hasBenefited;
    private Long sessionId;
    private final LocalDate joinedAt;
    private final boolean joinedMidTour;
    private final LocalDateTime createdAt;

    public PresenceTourParticipant(Long id, Long tourId, Long memberId,
                                   int drawOrder, boolean hasBenefited,
                                   Long sessionId, LocalDate joinedAt,
                                   boolean joinedMidTour, LocalDateTime createdAt) {
        this.id = id;
        this.tourId = tourId;
        this.memberId = memberId;
        this.drawOrder = drawOrder;
        this.hasBenefited = hasBenefited;
        this.sessionId = sessionId;
        this.joinedAt = joinedAt;
        this.joinedMidTour = joinedMidTour;
        this.createdAt = createdAt;
    }

    // ── Business methods ────────────────────────────────────

    public void markAsBenefited(Long sessionId) {
        this.hasBenefited = true;
        this.sessionId = sessionId;
    }

    /** Éligible au tirage hebdomadaire : fondateur du tour et pas encore bénéficiaire. */
    public boolean isDrawEligible() {
        return !hasBenefited && !joinedMidTour;
    }
}
