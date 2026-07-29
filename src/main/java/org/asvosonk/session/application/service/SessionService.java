package org.asvosonk.session.application.service;

import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.asvosonk.session.presentation.response.SessionCloseResult;
import org.asvosonk.member.domain.model.Member;
import org.asvosonk.member.domain.repository.MemberRepository;
import org.asvosonk.member.infrastructure.persistence.entity.MemberEntity;

import org.asvosonk.security.domain.model.AppUser;
import org.asvosonk.security.infrastructure.persistence.entity.AppUserEntity;
import org.asvosonk.session.presentation.request.AttendanceEntryForm;
import org.asvosonk.session.presentation.request.SessionForm;
import org.asvosonk.session.domain.valueobject.SessionStatus;
import org.asvosonk.session.infrastructure.persistence.entity.MeetingSessionEntity;
import org.asvosonk.session.infrastructure.persistence.entity.SessionAttendanceEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class SessionService {

    private final MemberRepository      memberRepository;
    private final EntityManager          entityManager;

    private MemberEntity toEntity(Member m) {
        return m != null ? entityManager.getReference(MemberEntity.class, m.getId()) : null;
    }

    // ── Queries ──────────────────────────────────────────────

    public List<MeetingSessionEntity> findAll() {
        return entityManager.createQuery(
                "SELECT s FROM MeetingSessionEntity s LEFT JOIN FETCH s.presenceBeneficiary ORDER BY s.sessionDate DESC",
                MeetingSessionEntity.class)
            .getResultList();
    }

    public MeetingSessionEntity findById(Long id) {
        try {
            return entityManager.createQuery(
                    "SELECT s FROM MeetingSessionEntity s LEFT JOIN FETCH s.presenceBeneficiary WHERE s.id = :id",
                    MeetingSessionEntity.class)
                .setParameter("id", id)
                .getSingleResult();
        } catch (jakarta.persistence.NoResultException e) {
            throw new IllegalArgumentException("Séance introuvable : " + id);
        }
    }

    /**
     * Loads a session under a PESSIMISTIC_WRITE lock (F-10). Concurrent step
     * transitions on the same session are then serialized at the DB row level:
     * the second request blocks until the first commits, and only then re-reads
     * the (now advanced) current step — so a double submit can never replay the
     * same financial transition twice. Must run inside a transaction.
     */
    public MeetingSessionEntity findByIdForUpdate(Long id) {
        MeetingSessionEntity session = entityManager.find(
            MeetingSessionEntity.class, id, jakarta.persistence.LockModeType.PESSIMISTIC_WRITE);
        if (session == null) {
            throw new IllegalArgumentException("Séance introuvable : " + id);
        }
        return session;
    }

    public List<SessionAttendanceEntity> findAttendances(Long sessionId) {
        return entityManager.createQuery(
                "SELECT a FROM SessionAttendanceEntity a JOIN FETCH a.member m WHERE a.session.id = :sessionId ORDER BY m.fullName ASC",
                SessionAttendanceEntity.class)
            .setParameter("sessionId", sessionId)
            .getResultList();
    }

    // ── Create session ───────────────────────────────────────

    @Transactional
    public MeetingSessionEntity create(SessionForm form, AppUser createdBy) {
        TypedQuery<Long> existsQuery = entityManager.createQuery(
                "SELECT COUNT(s) FROM MeetingSessionEntity s WHERE s.sessionDate = :date", Long.class);
        existsQuery.setParameter("date", form.getSessionDate());
        if (existsQuery.getSingleResult() > 0) {
            throw new IllegalArgumentException(
                "Une séance existe déjà pour le " + form.getSessionDate());
        }

        MeetingSessionEntity session = new MeetingSessionEntity();
        session.setSessionDate(form.getSessionDate());
        session.setAgenda(form.getAgenda());
        session.setStatus(SessionStatus.open);
        session.setCreatedBy(entityManager.getReference(AppUserEntity.class, createdBy.getId()));
        entityManager.persist(session);

        // Pre-populate attendance rows for all active members
        List<Member> activeMembers = memberRepository.findAllActive();
        for (Member m : activeMembers) {
            SessionAttendanceEntity att = new SessionAttendanceEntity();
            att.setSession(session);
            att.setMember(toEntity(m));
            att.setPresent(false);
            att.setAmountPaid(BigDecimal.ZERO);
            entityManager.persist(att);
        }

        log.info("Session created: {} — {} members pre-populated", session.getSessionDate(), activeMembers.size());
        return session;
    }

    // ── Set beneficiary ──────────────────────────────────────

    @Transactional
    public void setBeneficiary(Long sessionId, Long memberId) {
        MeetingSessionEntity session = findById(sessionId);
        if (session.isClosed()) throw new IllegalStateException("Séance déjà clôturée");
        Member beneficiary = memberRepository.findById(memberId)
            .orElseThrow(() -> new IllegalArgumentException("Membre introuvable"));
        session.setPresenceBeneficiary(entityManager.getReference(MemberEntity.class, beneficiary.getId()));
        entityManager.merge(session);
    }

    // ── Save attendance entry (before close) ─────────────────

    @Transactional
    public void saveAttendanceEntry(Long sessionId, AttendanceEntryForm form) {
        MeetingSessionEntity session = findById(sessionId);
        if (session.isClosed()) throw new IllegalStateException("Séance déjà clôturée");

        TypedQuery<SessionAttendanceEntity> query = entityManager.createQuery(
                "SELECT a FROM SessionAttendanceEntity a WHERE a.session.id = :sessionId AND a.member.id = :memberId",
                SessionAttendanceEntity.class);
        query.setParameter("sessionId", sessionId);
        query.setParameter("memberId", form.getMemberId());

        SessionAttendanceEntity att;
        try {
            att = query.getSingleResult();
        } catch (jakarta.persistence.NoResultException e) {
            att = new SessionAttendanceEntity();
            att.setSession(session);
            att.setMember(entityManager.getReference(MemberEntity.class,
                memberRepository.findById(form.getMemberId()).orElseThrow().getId()));
        }

        att.setPresent(form.isPresent());
        att.setAmountPaid(form.getAmountPaid() != null ? form.getAmountPaid() : BigDecimal.ZERO);
        entityManager.merge(att);
    }

    /**
     * @deprecated The session workflow is now managed by SessionStepService.
     * This legacy method duplicates logic from SessionStepService.closePresence().
     * Do NOT call this method for new development — use SessionStepService instead.
     * Will be removed in a future release.
     */
    @Deprecated(since = "1.0.0", forRemoval = true)
    public SessionCloseResult closeSession(Long sessionId, AppUser user) {
        throw new UnsupportedOperationException(
            "SessionService.closeSession() is deprecated. Use SessionStepService.transitionToNext() instead.");
    }
}
