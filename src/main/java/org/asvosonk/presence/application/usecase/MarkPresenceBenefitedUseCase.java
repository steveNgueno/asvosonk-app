package org.asvosonk.presence.application.usecase;

import lombok.RequiredArgsConstructor;
import org.asvosonk.common.domain.exception.BusinessRuleException;
import org.asvosonk.common.domain.exception.ResourceNotFoundException;
import org.asvosonk.presence.domain.model.PresenceTour;
import org.asvosonk.presence.domain.model.PresenceTourParticipant;
import org.asvosonk.presence.domain.repository.PresenceTourParticipantRepository;
import org.asvosonk.presence.domain.repository.PresenceTourRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class MarkPresenceBenefitedUseCase {

    private final PresenceTourParticipantRepository participantRepository;
    private final PresenceTourRepository            tourRepository;
    private final GetPresenceTourSummaryUseCase     summary;
    private final ClosePresenceTourUseCase          closePresenceTourUseCase;

    /**
     * Enregistre qu'un membre a bénéficié de la tontine de présence, puis clôture
     * le tour si plus personne n'attend.
     *
     * <p>Invariants vérifiés côté serveur (une interface qui masque un bouton ne
     * protège de rien) :</p>
     * <ul>
     *   <li>le tour doit être ouvert ;</li>
     *   <li>le membre ne doit pas avoir déjà bénéficié dans ce tour ;</li>
     *   <li>le membre doit faire partie des bénéficiaires éligibles : tant qu'un
     *       membre présent au démarrage du tour n'a pas été servi, un arrivant en
     *       cours de tour ne peut pas passer avant lui.</li>
     * </ul>
     */
    @Transactional
    public PresenceTourParticipant execute(Long tourId, Long memberId, Long sessionId) {
        PresenceTour tour = tourRepository.findById(tourId)
            .orElseThrow(() -> new ResourceNotFoundException("Tour de présence", tourId));
        if (tour.isClosed()) {
            throw new BusinessRuleException("Ce tour de présence est clôturé.");
        }

        PresenceTourParticipant participant = participantRepository
            .findByTourIdAndMemberId(tourId, memberId)
            .orElseThrow(() -> new ResourceNotFoundException(
                "Participant introuvable pour le tour " + tourId));

        if (participant.isHasBenefited()) {
            throw new BusinessRuleException(
                "Ce membre a déjà bénéficié de la tontine de présence dans ce tour.");
        }

        List<PresenceTourParticipant> eligible = summary.findEligibleBeneficiaries(tourId);
        boolean allowed = eligible.stream().anyMatch(p -> p.getMemberId().equals(memberId));
        if (!allowed) {
            throw new BusinessRuleException(
                "Ce membre n'est pas éligible : les membres présents au démarrage du tour "
              + "doivent tous avoir bénéficié avant les arrivants en cours de tour.");
        }

        participant.markAsBenefited(sessionId);
        PresenceTourParticipant saved = participantRepository.save(participant);

        long total = participantRepository.findByTourId(tourId).size();
        long benefited = participantRepository.countByTourIdAndHasBenefited(tourId, true);
        if (total == benefited) {
            closePresenceTourUseCase.execute(tourId);
        }

        return saved;
    }
}
