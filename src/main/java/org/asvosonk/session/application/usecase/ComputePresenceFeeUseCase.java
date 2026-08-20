package org.asvosonk.session.application.usecase;

import lombok.RequiredArgsConstructor;
import org.asvosonk.presence.domain.model.PresenceTourParticipant;
import org.asvosonk.presence.domain.repository.PresenceTourParticipantRepository;
import org.asvosonk.session.infrastructure.persistence.entity.MeetingSessionEntity;
import org.asvosonk.session.infrastructure.persistence.repository.SpringDataMeetingSessionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Montant dû par chaque membre pour la cotisation de présence d'une séance.
 *
 * <p>Le montant normal est de 2 000 FCFA (1 000 tontine + 500 boisson + 500
 * développement). Il tombe à 1 000 FCFA — boisson et développement seulement —
 * pour les membres qui avaient <strong>déjà bénéficié avant l'arrivée</strong> du
 * bénéficiaire du jour, lorsque celui-ci a rejoint le tour en cours de route :
 * ces membres n'ont jamais reçu de part de tontine de sa part, ils ne lui en
 * doivent donc pas.</p>
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ComputePresenceFeeUseCase {

    public static final BigDecimal FULL_FEE    = new BigDecimal("2000");
    public static final BigDecimal REDUCED_FEE = new BigDecimal("1000");

    private final PresenceTourParticipantRepository participantRepository;
    private final SpringDataMeetingSessionRepository sessionRepository;

    /**
     * Montant dû par membre pour la séance, indexé par identifiant de membre.
     * Les membres absents de la table doivent le montant plein.
     */
    public Map<Long, BigDecimal> feesByMember(Long tourId, Long beneficiaryMemberId) {
        Map<Long, BigDecimal> fees = new HashMap<>();
        if (tourId == null || beneficiaryMemberId == null) {
            return fees;
        }

        List<PresenceTourParticipant> participants = participantRepository.findByTourId(tourId);
        PresenceTourParticipant beneficiary = participants.stream()
            .filter(p -> beneficiaryMemberId.equals(p.getMemberId()))
            .findFirst()
            .orElse(null);

        // Bénéficiaire fondateur du tour : tout le monde doit le montant plein.
        if (beneficiary == null || !beneficiary.isJoinedMidTour() || beneficiary.getJoinedAt() == null) {
            return fees;
        }

        LocalDate arrival = beneficiary.getJoinedAt();
        List<PresenceTourParticipant> alreadyServed = participants.stream()
            .filter(PresenceTourParticipant::isHasBenefited)
            .filter(p -> p.getSessionId() != null)
            .toList();
        if (alreadyServed.isEmpty()) {
            return fees;
        }

        Map<Long, LocalDate> benefitDates = new HashMap<>();
        sessionRepository.findAllById(alreadyServed.stream()
                .map(PresenceTourParticipant::getSessionId)
                .filter(Objects::nonNull)
                .distinct()
                .toList())
            .forEach(s -> benefitDates.put(s.getId(), s.getSessionDate()));

        for (PresenceTourParticipant served : alreadyServed) {
            LocalDate benefitedOn = benefitDates.get(served.getSessionId());
            if (benefitedOn != null && benefitedOn.isBefore(arrival)) {
                fees.put(served.getMemberId(), REDUCED_FEE);
            }
        }
        return fees;
    }

    /** Montant dû par un membre donné, montant plein par défaut. */
    public BigDecimal feeFor(Map<Long, BigDecimal> fees, Long memberId) {
        return fees.getOrDefault(memberId, FULL_FEE);
    }

    /** Séance chargée par identifiant (utilitaire de lecture). */
    public MeetingSessionEntity session(Long sessionId) {
        return sessionRepository.findById(sessionId).orElse(null);
    }
}
