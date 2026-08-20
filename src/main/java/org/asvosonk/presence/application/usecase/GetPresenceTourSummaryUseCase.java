package org.asvosonk.presence.application.usecase;

import lombok.RequiredArgsConstructor;
import org.asvosonk.presence.domain.model.PresenceTour;
import org.asvosonk.presence.domain.model.PresenceTourParticipant;
import org.asvosonk.presence.domain.repository.PresenceTourParticipantRepository;
import org.asvosonk.presence.domain.repository.PresenceTourRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;

/**
 * Lectures du module « tour de présence ».
 *
 * <p>Le bénéficiaire de la tontine de présence est tiré au sort à chaque séance
 * parmi les membres qui n'ont pas encore bénéficié du tour en cours. Les membres
 * ayant rejoint l'association en cours de tour ne participent pas à ces tirages :
 * ils bénéficient en dernier, dans leur ordre d'arrivée.</p>
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class GetPresenceTourSummaryUseCase {

    private final PresenceTourRepository tourRepository;
    private final PresenceTourParticipantRepository participantRepository;

    public List<PresenceTour> findAllTours() {
        return tourRepository.findAllByOrderByStartDateDesc();
    }

    public PresenceTour findTourById(Long id) {
        return tourRepository.findById(id)
            .orElseThrow(() -> new IllegalArgumentException("Tour de présence introuvable : " + id));
    }

    public PresenceTour findCurrentOpenTour() {
        return tourRepository.findCurrentOpenTour().orElse(null);
    }

    public List<PresenceTourParticipant> findParticipantsByTourId(Long tourId) {
        return participantRepository.findByTourIdOrderByDrawOrder(tourId);
    }

    public List<PresenceTourParticipant> findNotBenefitedYet(Long tourId) {
        return participantRepository.findByTourIdOrderByDrawOrder(tourId).stream()
            .filter(p -> !p.isHasBenefited())
            .toList();
    }

    /**
     * Membres pouvant bénéficier à la prochaine séance.
     *
     * <p>Tant qu'il reste des membres présents au démarrage du tour à servir, ce
     * sont eux — et eux seuls — qui entrent dans le tirage. Les arrivants en cours
     * de tour ne deviennent éligibles qu'une fois tous les autres servis, et dans
     * leur ordre d'arrivée.</p>
     */
    public List<PresenceTourParticipant> findEligibleBeneficiaries(Long tourId) {
        List<PresenceTourParticipant> pending = findNotBenefitedYet(tourId);

        List<PresenceTourParticipant> founders = pending.stream()
            .filter(PresenceTourParticipant::isDrawEligible)
            .toList();
        if (!founders.isEmpty()) {
            return founders;
        }

        return pending.stream()
            .sorted(Comparator.comparing(PresenceTourParticipant::getJoinedAt)
                              .thenComparing(PresenceTourParticipant::getDrawOrder))
            .toList();
    }

    public boolean allParticipantsBenefited(Long tourId) {
        // Un tour sans participant n'est pas un tour terminé : allMatch renverrait
        // true sur une liste vide et permettrait de le clôturer.
        var participants = participantRepository.findByTourIdOrderByDrawOrder(tourId);
        return !participants.isEmpty()
            && participants.stream().allMatch(PresenceTourParticipant::isHasBenefited);
    }

    public long countBenefited(Long tourId) {
        return participantRepository.countByTourIdAndHasBenefited(tourId, true);
    }
}
