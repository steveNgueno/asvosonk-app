package org.asvosonk.session.application.usecase;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.asvosonk.member.domain.model.Member;
import org.asvosonk.member.domain.repository.MemberRepository;
import org.asvosonk.security.domain.model.AppUser;
import org.asvosonk.security.infrastructure.persistence.entity.AppUserEntity;
import org.asvosonk.session.domain.valueobject.SessionStatus;
import org.asvosonk.session.infrastructure.persistence.entity.MeetingSessionEntity;
import org.asvosonk.session.infrastructure.persistence.entity.SessionAttendanceEntity;
import org.asvosonk.session.presentation.request.SessionForm;
import jakarta.persistence.EntityManager;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

/**
 * Use case: create a new meeting session with pre-populated attendance rows.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CreateSessionUseCase {

    private final MemberRepository memberRepository;
    private final EntityManager entityManager;

    @Transactional
    public MeetingSessionEntity execute(SessionForm form, AppUser createdBy) {
        Long count = entityManager.createQuery(
                "SELECT COUNT(s) FROM MeetingSessionEntity s WHERE s.sessionDate = :date", Long.class)
            .setParameter("date", form.getSessionDate())
            .getSingleResult();
        if (count > 0) {
            throw new IllegalArgumentException(
                "Une séance existe déjà pour le " + form.getSessionDate());
        }

        MeetingSessionEntity session = new MeetingSessionEntity();
        session.setSessionDate(form.getSessionDate());
        session.setAgenda(form.getAgenda());
        session.setStatus(SessionStatus.open);
        session.setCreatedBy(entityManager.getReference(AppUserEntity.class, createdBy.getId()));
        entityManager.persist(session);

        List<Member> activeMembers = memberRepository.findAllActive();
        for (Member m : activeMembers) {
            SessionAttendanceEntity att = new SessionAttendanceEntity();
            att.setSession(session);
            att.setMember(entityManager.getReference(
                org.asvosonk.member.infrastructure.persistence.entity.MemberEntity.class, m.getId()));
            att.setPresent(false);
            att.setAmountPaid(BigDecimal.ZERO);
            entityManager.persist(att);
        }

        log.info("Session created: {} — {} members pre-populated", session.getSessionDate(), activeMembers.size());
        return session;
    }
}
