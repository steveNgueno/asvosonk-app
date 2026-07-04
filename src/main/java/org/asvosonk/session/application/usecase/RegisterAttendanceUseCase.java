package org.asvosonk.session.application.usecase;

import lombok.RequiredArgsConstructor;
import org.asvosonk.member.domain.repository.MemberRepository;
import org.asvosonk.member.infrastructure.persistence.entity.MemberEntity;
import org.asvosonk.session.infrastructure.persistence.entity.MeetingSessionEntity;
import org.asvosonk.session.infrastructure.persistence.entity.SessionAttendanceEntity;
import org.asvosonk.session.presentation.request.AttendanceEntryForm;
import jakarta.persistence.EntityManager;
import jakarta.persistence.NoResultException;
import jakarta.persistence.TypedQuery;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

/**
 * Use case: register or update a member's attendance for a session.
 */
@Service
@RequiredArgsConstructor
public class RegisterAttendanceUseCase {

    private final MemberRepository memberRepository;
    private final EntityManager entityManager;

    @Transactional
    public void execute(Long sessionId, AttendanceEntryForm form) {
        MeetingSessionEntity session = entityManager.find(MeetingSessionEntity.class, sessionId);
        if (session == null) {
            throw new IllegalArgumentException("Séance introuvable : " + sessionId);
        }
        if (session.isClosed()) {
            throw new IllegalStateException("Séance déjà clôturée");
        }

        TypedQuery<SessionAttendanceEntity> query = entityManager.createQuery(
                "SELECT a FROM SessionAttendanceEntity a WHERE a.session.id = :sessionId AND a.member.id = :memberId",
                SessionAttendanceEntity.class);
        query.setParameter("sessionId", sessionId);
        query.setParameter("memberId", form.getMemberId());

        SessionAttendanceEntity att;
        try {
            att = query.getSingleResult();
        } catch (NoResultException e) {
            att = new SessionAttendanceEntity();
            att.setSession(session);
            att.setMember(entityManager.getReference(MemberEntity.class,
                memberRepository.findById(form.getMemberId()).orElseThrow().getId()));
        }

        att.setPresent(form.isPresent());
        att.setAmountPaid(form.getAmountPaid() != null ? form.getAmountPaid() : BigDecimal.ZERO);
        entityManager.merge(att);
    }
}
