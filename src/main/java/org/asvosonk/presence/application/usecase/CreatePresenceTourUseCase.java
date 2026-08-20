package org.asvosonk.presence.application.usecase;

import lombok.RequiredArgsConstructor;
import org.asvosonk.common.domain.exception.BusinessRuleException;
import org.asvosonk.member.domain.model.Member;
import org.asvosonk.member.domain.repository.MemberRepository;
import org.asvosonk.presence.domain.model.PresenceTour;
import org.asvosonk.presence.domain.model.PresenceTourParticipant;
import org.asvosonk.presence.domain.repository.PresenceTourParticipantRepository;
import org.asvosonk.presence.domain.repository.PresenceTourRepository;
import org.asvosonk.presence.domain.valueobject.PresenceTourStatus;
import org.asvosonk.session.application.usecase.RequireOpenSessionUseCase;
import org.asvosonk.session.infrastructure.persistence.entity.MeetingSessionEntity;
import org.asvosonk.session.infrastructure.persistence.repository.SpringDataMeetingSessionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Ouverture d'un tour de présence.
 *
 * <p>La cotisation de présence est obligatoire pour tous : un tour regroupe donc
 * <strong>tous les membres actifs</strong> au moment de son ouverture. Il n'y a
 * pas d'ordre de passage à saisir — le bénéficiaire est tiré au sort à chaque
 * séance parmi ceux qui n'ont pas encore bénéficié. L'ordre enregistré ici ne
 * sert qu'à départager les arrivants en cours de tour, qui passent en dernier.</p>
 */
@Service
@RequiredArgsConstructor
public class CreatePresenceTourUseCase {

    private final PresenceTourRepository            tourRepository;
    private final PresenceTourParticipantRepository participantRepository;
    private final MemberRepository                  memberRepository;
    private final RequireOpenSessionUseCase         requireOpenSession;
    private final SpringDataMeetingSessionRepository sessionRepository;

    /**
     * Ouvre un tour avec tous les membres actifs.
     *
     * @param startDate date de démarrage du tour
     */
    @Transactional
    public PresenceTour execute(LocalDate startDate) {
        if (tourRepository.findCurrentOpenTour().isPresent()) {
            throw new BusinessRuleException(
                "Un tour de présence est déjà en cours. Clôturez-le avant d'en créer un nouveau.");
        }

        List<Member> members = memberRepository.findAllActive();
        if (members.size() < 2) {
            throw new BusinessRuleException(
                "Un tour de présence demande au moins 2 membres actifs.");
        }

        PresenceTour tour = new PresenceTour(
            null, startDate, null, PresenceTourStatus.open, LocalDateTime.now());
        PresenceTour savedTour = tourRepository.save(tour);

        int order = 1;
        for (Member member : members) {
            participantRepository.save(new PresenceTourParticipant(
                null, savedTour.getId(), member.getId(),
                order++, false, null, startDate, false, LocalDateTime.now()));
        }
        return savedTour;
    }

    /**
     * Rattache un membre qui vient d'adhérer au tour en cours.
     *
     * <p>Il ne participe pas aux tirages et bénéficiera en dernier ; à son tour,
     * les membres qui avaient déjà bénéficié avant son arrivée ne lui devront que
     * 1 000 FCFA (boisson + développement), puisqu'il n'a pas cotisé pour eux.</p>
     *
     * <p>La date retenue est celle de son <strong>entrée dans le tour</strong>, et
     * non la date d'adhésion saisie dans sa fiche : les deux diffèrent dès qu'on
     * enregistre un membre avec une date d'adhésion antérieure, et c'est bien
     * l'entrée dans le tour qui départage les bénéficiaires « d'avant » de ceux
     * « d'après ».</p>
     *
     * <p>Elle est calculée pour tomber juste après la dernière séance déjà tenue :
     * tous ceux qui ont déjà bénéficié l'ont fait avant son arrivée, tous ceux qui
     * bénéficieront ensuite l'auront fait après. Si une séance est en cours, c'est
     * sa date — le membre arrive pendant cette réunion, dont le bénéficiaire n'est
     * pas encore servi.</p>
     *
     * @return le participant créé, ou {@code null} si aucun tour n'est ouvert
     */
    @Transactional
    public PresenceTourParticipant addLateMember(Long memberId, LocalDate ignoredJoinDate) {
        PresenceTour openTour = tourRepository.findCurrentOpenTour().orElse(null);
        if (openTour == null) {
            return null;
        }
        if (participantRepository.findByTourIdAndMemberId(openTour.getId(), memberId).isPresent()) {
            return null;
        }

        List<PresenceTourParticipant> participants = participantRepository.findByTourId(openTour.getId());
        int lastOrder = participants.stream()
            .mapToInt(PresenceTourParticipant::getDrawOrder)
            .max()
            .orElse(0);

        return participantRepository.save(new PresenceTourParticipant(
            null, openTour.getId(), memberId,
            lastOrder + 1, false, null, arrivalDate(openTour, participants), true, LocalDateTime.now()));
    }

    /** Date d'entrée dans le tour : voir {@link #addLateMember}. */
    private LocalDate arrivalDate(PresenceTour tour, List<PresenceTourParticipant> participants) {
        LocalDate openSessionDate = requireOpenSession.find()
            .map(MeetingSessionEntity::getSessionDate)
            .orElse(null);
        if (openSessionDate != null && !openSessionDate.isBefore(tour.getStartDate())) {
            return openSessionDate;
        }

        // Dernière séance au cours de laquelle un membre a bénéficié, + 1 jour.
        LocalDate lastServed = participants.stream()
            .filter(PresenceTourParticipant::isHasBenefited)
            .map(PresenceTourParticipant::getSessionId)
            .filter(Objects::nonNull)
            .map(sessionRepository::findById)
            .flatMap(Optional::stream)
            .map(MeetingSessionEntity::getSessionDate)
            .max(LocalDate::compareTo)
            .orElse(null);

        return lastServed != null ? lastServed.plusDays(1) : tour.getStartDate();
    }
}
