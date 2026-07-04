package org.asvosonk.tontine.application.usecase;

import lombok.RequiredArgsConstructor;
import org.asvosonk.common.domain.exception.BusinessRuleException;
import org.asvosonk.tontine.domain.model.TontineParticipant;
import org.asvosonk.tontine.domain.model.TontineTour;
import org.asvosonk.tontine.domain.repository.TontineParticipantRepository;
import org.asvosonk.tontine.domain.repository.TontineTourRepository;
import org.asvosonk.tontine.domain.valueobject.TontineTourStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class CreateTourUseCase {

    private final TontineTourRepository tourRepository;
    private final TontineParticipantRepository participantRepository;

    /**
     * Creates a new tontine tour with participants.
     *
     * @param startDate    tour start date
     * @param memberIds    list of member IDs participating
     * @param drawOrders   corresponding draw order for each member
     * @return the created TontineTour
     */
    @Transactional
    public TontineTour execute(LocalDate startDate, List<Long> memberIds, List<Integer> drawOrders) {
        // Precondition: no open tour already exists
        if (tourRepository.findCurrentOpenTour().isPresent()) {
            throw new BusinessRuleException("Un tour de grande tontine est déjà en cours. " +
                "Clôturez-le avant d'en créer un nouveau.");
        }

        // Precondition: at least 2 members
        if (memberIds == null || memberIds.size() < 2) {
            throw new BusinessRuleException("Le tour doit comporter au moins 2 participants.");
        }

        if (drawOrders == null || drawOrders.size() != memberIds.size()) {
            throw new BusinessRuleException("L'ordre de tirage doit être fourni pour chaque participant.");
        }

        // Create tour
        TontineTour tour = new TontineTour(null, startDate, null, TontineTourStatus.open, LocalDateTime.now());
        TontineTour savedTour = tourRepository.save(tour);

        // Create participants
        List<TontineParticipant> participants = new ArrayList<>();
        for (int i = 0; i < memberIds.size(); i++) {
            TontineParticipant participant = new TontineParticipant(
                null, savedTour.getId(), memberIds.get(i),
                drawOrders.get(i), false, LocalDateTime.now());
            participantRepository.save(participant);
            participants.add(participant);
        }

        return savedTour;
    }
}
