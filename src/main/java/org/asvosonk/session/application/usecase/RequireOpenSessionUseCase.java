package org.asvosonk.session.application.usecase;

import lombok.RequiredArgsConstructor;
import org.asvosonk.common.domain.exception.BusinessRuleException;
import org.asvosonk.session.domain.valueobject.SessionStep;
import org.asvosonk.session.infrastructure.persistence.entity.MeetingSessionEntity;
import org.asvosonk.session.infrastructure.persistence.repository.SpringDataMeetingSessionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/**
 * La séance en cours, pour tout ce qui doit s'y rattacher.
 *
 * <p>La réunion est le cadre de toute opération financière : un encaissement
 * n'existe pas « hors séance », il appartient au jour où l'argent a été remis.
 * Une seule séance est ouverte à la fois, et elle se ferme à la génération de
 * son rapport.</p>
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class RequireOpenSessionUseCase {

    private final SpringDataMeetingSessionRepository sessionRepository;

    /** La séance en cours, ou {@code Optional.empty()} s'il n'y en a pas. */
    public Optional<MeetingSessionEntity> find() {
        return sessionRepository.findAllByOrderBySessionDateDesc().stream()
            .filter(s -> !s.isClosed())
            .findFirst();
    }

    /**
     * La séance en cours, ou un refus explicite.
     *
     * @param operation ce que l'utilisateur essayait de faire, repris dans le message
     */
    public MeetingSessionEntity require(String operation) {
        MeetingSessionEntity session = find().orElseThrow(() -> new BusinessRuleException(
            "Aucune séance n'est ouverte : " + operation + " se fait en séance. "
          + "Créez la séance du jour d'abord."));

        if (session.isStepAtLeast(SessionStep.REPORT_GENERATED)) {
            throw new BusinessRuleException(
                "Le rapport de la séance du " + session.getSessionDate()
              + " est déjà généré : plus rien ne peut y être rattaché.");
        }
        return session;
    }
}
