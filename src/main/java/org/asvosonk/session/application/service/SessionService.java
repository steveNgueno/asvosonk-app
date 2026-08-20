package org.asvosonk.session.application.service;

import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.asvosonk.member.domain.model.Member;
import org.asvosonk.member.domain.repository.MemberRepository;
import org.asvosonk.member.infrastructure.persistence.entity.MemberEntity;

import org.asvosonk.common.domain.exception.BusinessRuleException;
import org.asvosonk.presence.domain.model.PresenceTour;
import org.asvosonk.presence.domain.repository.PresenceTourRepository;
import org.asvosonk.security.domain.model.AppUser;
import org.asvosonk.security.infrastructure.persistence.entity.AppUserEntity;
import org.asvosonk.session.application.usecase.ComputePresenceFeeUseCase;
import org.asvosonk.session.application.usecase.GetRevolvingFundStatusUseCase;
import org.asvosonk.session.presentation.request.AttendanceEntryForm;
import org.asvosonk.session.presentation.request.SessionForm;
import org.asvosonk.session.domain.valueobject.SessionStatus;
import org.asvosonk.session.domain.valueobject.SessionStep;
import org.asvosonk.session.infrastructure.persistence.entity.MeetingSessionEntity;
import org.asvosonk.session.infrastructure.persistence.entity.SessionAttendanceEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class SessionService {

    private final MemberRepository                memberRepository;
    private final EntityManager                   entityManager;
    private final PresenceTourRepository          presenceTourRepository;
    private final ComputePresenceFeeUseCase       computePresenceFeeUseCase;
    private final GetRevolvingFundStatusUseCase   getRevolvingFundStatusUseCase;

    private MemberEntity toEntity(Member m) {
        return m != null ? entityManager.getReference(MemberEntity.class, m.getId()) : null;
    }

    // ── Queries ──────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<MeetingSessionEntity> findAll() {
        return entityManager.createQuery(
                "SELECT s FROM MeetingSessionEntity s LEFT JOIN FETCH s.presenceBeneficiary ORDER BY s.sessionDate DESC",
                MeetingSessionEntity.class)
            .getResultList();
    }

    @Transactional(readOnly = true)
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

    /**
     * Échecs de cotisation recouverts au cours de cette séance.
     *
     * <p>Chaque recouvrement rend 1 000 FCFA au bénéficiaire de la séance ratée :
     * l'argent lui est remis en espèces, ou conservé par le trésorier s'il est
     * absent. Le rapport de séance en dresse la liste.</p>
     */
    @Transactional(readOnly = true)
    public List<RecoveredFailure> findRecoveriesDuring(Long sessionId) {
        return entityManager.createQuery("""
                SELECT a FROM SessionAttendanceEntity a
                  JOIN FETCH a.member
                  JOIN FETCH a.session s
                  LEFT JOIN FETCH s.presenceBeneficiary
                 WHERE a.recoveredSession.id = :sessionId
                 ORDER BY s.sessionDate ASC
                """, SessionAttendanceEntity.class)
            .setParameter("sessionId", sessionId)
            .getResultList()
            .stream()
            .map(a -> new RecoveredFailure(
                a.getMember().getId(),
                a.getSession().getId(),
                a.getSession().getSessionDate(),
                a.getSession().getPresenceBeneficiary() != null
                    ? a.getSession().getPresenceBeneficiary().getId() : null,
                a.tontineShare()))
            .filter(r -> r.amount().signum() > 0)
            .toList();
    }

    /** Rattrapage dû au bénéficiaire d'une séance dont l'échec vient d'être recouvert. */
    public record RecoveredFailure(Long memberId,
                                   Long failedSessionId,
                                   java.time.LocalDate failedSessionDate,
                                   Long beneficiaryId,
                                   BigDecimal amount) { }

    @Transactional(readOnly = true)
    public List<SessionAttendanceEntity> findAttendances(Long sessionId) {
        return entityManager.createQuery(
                "SELECT a FROM SessionAttendanceEntity a JOIN FETCH a.member m WHERE a.session.id = :sessionId ORDER BY m.fullName ASC",
                SessionAttendanceEntity.class)
            .setParameter("sessionId", sessionId)
            .getResultList();
    }

    /**
     * Complète la feuille de présence avec les membres actifs qui n'y figurent
     * pas encore, et retourne la liste à jour.
     *
     * <p>Les lignes sont créées à l'ouverture de la séance ; un membre inscrit
     * pendant la réunion — cas courant, l'adhésion se fait en séance — n'y serait
     * donc pas. La présence est obligatoire pour tous les membres actifs : la
     * feuille se réaligne à chaque affichage, tant qu'elle est modifiable.</p>
     */
    @Transactional
    public List<SessionAttendanceEntity> findAttendancesSynchronized(Long sessionId) {
        MeetingSessionEntity session = findById(sessionId);
        if (session.isClosed() || session.isStepAtLeast(SessionStep.PRESENCE_CLOSED)) {
            return findAttendances(sessionId);
        }

        Set<Long> present = entityManager.createQuery(
                "SELECT a.member.id FROM SessionAttendanceEntity a WHERE a.session.id = :sessionId",
                Long.class)
            .setParameter("sessionId", sessionId)
            .getResultList()
            .stream().collect(Collectors.toSet());

        int added = 0;
        for (Member m : memberRepository.findAllActive()) {
            if (present.contains(m.getId())) {
                continue;
            }
            SessionAttendanceEntity att = new SessionAttendanceEntity();
            att.setSession(session);
            att.setMember(toEntity(m));
            att.setPresent(false);
            att.setAmountPaid(BigDecimal.ZERO);
            entityManager.persist(att);
            added++;
        }
        if (added > 0) {
            entityManager.flush();
            log.info("Feuille de présence de la séance {} complétée : {} membre(s) ajouté(s)",
                sessionId, added);
        }
        return findAttendances(sessionId);
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

        // Une seule séance à la fois : la réunion suivante ne s'ouvre qu'une fois
        // la précédente menée jusqu'à son rapport. Sans quoi deux séances se
        // disputeraient les mêmes encaissements.
        List<MeetingSessionEntity> unfinished = entityManager.createQuery(
                "SELECT s FROM MeetingSessionEntity s WHERE s.status <> :closed ORDER BY s.sessionDate",
                MeetingSessionEntity.class)
            .setParameter("closed", SessionStatus.closed)
            .getResultList();
        if (!unfinished.isEmpty()) {
            MeetingSessionEntity pending = unfinished.get(0);
            throw new IllegalArgumentException(
                "La séance du " + pending.getSessionDate() + " n'est pas terminée : "
              + "générez son rapport avant d'en ouvrir une nouvelle.");
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

    /**
     * Enregistre la feuille entière en une fois.
     *
     * <p>Tout ou rien : si un montant dépasse ce que le membre doit, rien n'est
     * enregistré et la feuille reste telle qu'elle était — plutôt qu'à moitié
     * saisie, avec les lignes d'avant le refus déjà écrites.</p>
     *
     * @return le nombre de lignes enregistrées
     */
    @Transactional
    public int saveAttendanceSheet(Long sessionId, List<AttendanceEntryForm> entries) {
        int saved = 0;
        for (AttendanceEntryForm entry : entries) {
            if (entry == null || entry.getMemberId() == null) {
                continue;
            }
            saveAttendanceEntry(sessionId, entry);
            saved++;
        }
        return saved;
    }

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

        BigDecimal paid = form.getAmountPaid() != null ? form.getAmountPaid() : BigDecimal.ZERO;
        rejectOverpayment(sessionId, form.getMemberId(), paid);

        att.setPresent(form.isPresent());
        att.setAmountPaid(paid);
        entityManager.merge(att);
    }

    /**
     * Refuse un versement supérieur à ce que le membre doit réellement.
     *
     * <p>On ne donne pas plus que son dû : le plafond est la cotisation du jour
     * — 2 000 FCFA, ou 1 000 pour un membre qui avait déjà bénéficié avant
     * l'arrivée du bénéficiaire du jour — augmentée des dettes encore en cours,
     * avances du fond et échecs à recouvrir. C'est exactement le montant
     * « pour tout solder » affiché sur la feuille.</p>
     */
    private void rejectOverpayment(Long sessionId, Long memberId, BigDecimal paid) {
        if (paid == null || paid.signum() <= 0) {
            return;
        }
        BigDecimal ceiling = presenceCeiling(sessionId, memberId);
        if (paid.compareTo(ceiling) > 0) {
            throw new BusinessRuleException(
                "Versement refusé pour " + memberName(memberId) + " : "
              + paid.toBigInteger() + " FCFA dépassent ce qui est dû ("
              + ceiling.toBigInteger() + " FCFA, dettes comprises). "
              + "On ne peut pas donner plus que son dû.");
        }
    }

    /** Cotisation du jour + dettes encore en cours, pour un membre et une séance. */
    @Transactional(readOnly = true)
    public BigDecimal presenceCeiling(Long sessionId, Long memberId) {
        MeetingSessionEntity session = findById(sessionId);
        BigDecimal due = ComputePresenceFeeUseCase.FULL_FEE;
        if (session.getPresenceBeneficiary() != null) {
            PresenceTour tour = presenceTourRepository.findCurrentOpenTour().orElse(null);
            if (tour != null) {
                due = computePresenceFeeUseCase.feeFor(
                    computePresenceFeeUseCase.feesByMember(
                        tour.getId(), session.getPresenceBeneficiary().getId()),
                    memberId);
            }
        }

        BigDecimal debts = BigDecimal.ZERO;
        for (var debt : getRevolvingFundStatusUseCase.outstandingDebtsByMember()
                .getOrDefault(memberId, List.of())) {
            debts = debts.add(debt.amount());
        }
        return due.add(debts);
    }

    private String memberName(Long memberId) {
        return memberRepository.findById(memberId).map(Member::getFullName).orElse("ce membre");
    }

}
