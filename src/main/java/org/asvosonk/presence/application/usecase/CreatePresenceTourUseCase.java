package org.asvosonk.presence.application.usecase;

import lombok.RequiredArgsConstructor;
import org.asvosonk.common.domain.exception.BusinessRuleException;
import org.asvosonk.presence.domain.model.PresenceTour;
import org.asvosonk.presence.domain.model.PresenceTourParticipant;
import org.asvosonk.presence.domain.repository.PresenceTourParticipantRepository;
import org.asvosonk.presence.domain.repository.PresenceTourRepository;
import org.asvosonk.presence.domain.valueobject.PresenceTourStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class CreatePresenceTourUseCase {

    private final PresenceTourRepository tourRepository;
    private final PresenceTourParticipantRepository participantRepository;

    /**
     * Creates a new presence tour with participants.
     *
     * @param startDate   tour start date
     * @param memberIds   list of member IDs participating
     * @param drawOrders  corresponding draw order for each member
     * @return the created PresenceTour
     */
    @Transactional
    public PresenceTour execute(LocalDate startDate, List<Long> memberIds, List<Integer> drawOrders) {
        // Precondition: no open tour already exists
        if (tourRepository.findCurrentOpenTour().isPresent()) {
            throw new BusinessRuleException("Un tour de présence est déjà en cours. " +
                "Clôturez-le avant d'en créer un nouveau.");
        }

        // Precondition: at least 2 members
        if (memberIds == null || memberIds.size() < 2) {
            throw new BusinessRuleException("Le tour doit comporter au moins 2 participants.");
        }

        if (drawOrders == null || drawOrders.size() != memberIds.size()) {
            throw new BusinessRuleException("L'ordre de tirage doit être fourni pour chaque participant.");
        }

        // Precondition: draw orders must be unique
        long distinctOrders = drawOrders.stream().distinct().count();
        if (distinctOrders != memberIds.size()) {
            throw new BusinessRuleException(
                "Deux participants ne peuvent pas avoir le même ordre de passage.");
        }

        // Create tour
        PresenceTour tour = new PresenceTour(null, startDate, null, PresenceTourStatus.open, LocalDateTime.now());
        PresenceTour savedTour = tourRepository.save(tour);

        // Create participants
        List<PresenceTourParticipant> participants = new ArrayList<>();
        for (int i = 0; i < memberIds.size(); i++) {
            PresenceTourParticipant participant = new PresenceTourParticipant(
                null, savedTour.getId(), memberIds.get(i),
                drawOrders.get(i), false, null, LocalDateTime.now());
            participantRepository.save(participant);
            participants.add(participant);
        }

        return savedTour;
    }

}
