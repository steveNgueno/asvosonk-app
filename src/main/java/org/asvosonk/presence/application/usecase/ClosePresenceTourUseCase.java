package org.asvosonk.presence.application.usecase;

import lombok.RequiredArgsConstructor;
import org.asvosonk.common.domain.exception.BusinessRuleException;
import org.asvosonk.common.domain.exception.ResourceNotFoundException;
import org.asvosonk.presence.domain.model.PresenceTour;
import org.asvosonk.presence.domain.model.PresenceTourParticipant;
import org.asvosonk.presence.domain.repository.PresenceTourParticipantRepository;
import org.asvosonk.presence.domain.repository.PresenceTourRepository;
import org.asvosonk.session.infrastructure.persistence.entity.MeetingSessionEntity;
import org.asvosonk.session.infrastructure.persistence.repository.SpringDataMeetingSessionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.Objects;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class ClosePresenceTourUseCase {

    private final PresenceTourRepository tourRepository;
    private final PresenceTourParticipantRepository participantRepository;
    private final SpringDataMeetingSessionRepository sessionRepository;

    /**
     * Close a presence tour once every participant has benefited.
     *
     * <p>F-08 — the "all have benefited" rule used to live only in the UI (the
     * close button was hidden), so a forged POST could close a tour mid-rotation
     * and deprive the remaining members. It is now enforced here. The automatic
     * close triggered by {@link MarkPresenceBenefitedUseCase} runs only when the
     * count is already complete, so this guard never blocks the happy path.
     */
    @Transactional
    public PresenceTour execute(Long tourId) {
        PresenceTour tour = tourRepository.findById(tourId)
            .orElseThrow(() -> new ResourceNotFoundException("Tour de présence", tourId));

        if (tour.isClosed()) {
            throw new BusinessRuleException("Ce tour de présence est déjà clôturé.");
        }

        long total = participantRepository.findByTourId(tourId).size();
        long benefited = participantRepository.countByTourIdAndHasBenefited(tourId, true);
        if (total == 0 || benefited < total) {
            throw new BusinessRuleException(
                "Impossible de clôturer : tous les participants n'ont pas encore bénéficié ("
              + benefited + "/" + total + ").");
        }

        tour.close(lastServedSessionDate(tourId));
        return tourRepository.save(tour);
    }

    /**
     * Le tour s'achève à la séance où son dernier bénéficiaire a été servi, et
     * non le jour où l'on clôture : sinon une clôture tardive (ou un début de
     * tour daté dans le futur) produisait une date de fin sans rapport avec le
     * déroulé, voire antérieure au début. Faute de séance connue, la date du jour.
     */
    private LocalDate lastServedSessionDate(Long tourId) {
        return participantRepository.findByTourId(tourId).stream()
            .map(PresenceTourParticipant::getSessionId)
            .filter(Objects::nonNull)
            .distinct()
            .map(sessionRepository::findById)
            .flatMap(Optional::stream)
            .map(MeetingSessionEntity::getSessionDate)
            .max(LocalDate::compareTo)
            .orElse(LocalDate.now());
    }
}
