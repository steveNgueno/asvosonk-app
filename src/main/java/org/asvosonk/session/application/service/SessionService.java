package org.asvosonk.session.application.service;

import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.asvosonk.cashbox.domain.valueobject.CashboxType;
import org.asvosonk.cashbox.domain.valueobject.MovementOrigin;
import org.asvosonk.cashbox.application.service.CashboxService;
import org.asvosonk.member.domain.model.Member;
import org.asvosonk.member.domain.repository.MemberRepository;
import org.asvosonk.member.infrastructure.persistence.entity.MemberEntity;
import org.asvosonk.sanction.domain.valueobject.SanctionStatus;
import org.asvosonk.sanction.infrastructure.persistence.entity.SanctionEntity;
import org.asvosonk.security.domain.model.AppUser;
import org.asvosonk.security.infrastructure.persistence.entity.AppUserEntity;
import org.asvosonk.session.presentation.request.AttendanceEntryForm;
import org.asvosonk.session.presentation.response.SessionCloseResult;
import org.asvosonk.session.presentation.request.SessionForm;
import org.asvosonk.session.domain.valueobject.AttendanceStatus;
import org.asvosonk.session.domain.valueobject.SessionStatus;
import org.asvosonk.session.infrastructure.persistence.entity.MeetingSessionEntity;
import org.asvosonk.session.infrastructure.persistence.entity.SessionAttendanceEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class SessionService {

    // Constants
    private static final BigDecimal PRESENCE_FEE      = new BigDecimal("2000");
    private static final BigDecimal TONTINE_SHARE     = new BigDecimal("1000");
    private static final BigDecimal BEVERAGE_SHARE    = new BigDecimal("500");
    private static final BigDecimal DEVELOPMENT_SHARE = new BigDecimal("500");
    private static final BigDecimal BEVERAGE_COST_PER_PERSON = new BigDecimal("500");

    private final MemberRepository      memberRepository;
    private final RevolvingFundService   revolvingFundService;
    private final CashboxService         cashboxService;
    private final EntityManager          entityManager;

    private MemberEntity toEntity(Member m) {
        return m != null ? entityManager.getReference(MemberEntity.class, m.getId()) : null;
    }

    private Member toDomain(MemberEntity e) {
        return e != null ? memberRepository.findById(e.getId()).orElse(null) : null;
    }

    // ── Queries ──────────────────────────────────────────────

    public List<MeetingSessionEntity> findAll() {
        return entityManager.createQuery(
                "SELECT s FROM MeetingSessionEntity s ORDER BY s.sessionDate DESC",
                MeetingSessionEntity.class)
            .getResultList();
    }

    public MeetingSessionEntity findById(Long id) {
        MeetingSessionEntity session = entityManager.find(MeetingSessionEntity.class, id);
        if (session == null) {
            throw new IllegalArgumentException("Séance introuvable : " + id);
        }
        return session;
    }

    public List<SessionAttendanceEntity> findAttendances(Long sessionId) {
        return entityManager.createQuery(
                "SELECT a FROM SessionAttendanceEntity a JOIN a.member m WHERE a.session.id = :sessionId ORDER BY m.fullName ASC",
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
        session.setBeneficiary(entityManager.getReference(MemberEntity.class, beneficiary.getId()));
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

    // ── CLOSE SESSION — core @Transactional operation ────────

    /**
     * Closes the session and triggers ALL automatic calculations in one atomic transaction:
     * 1. Process each member's attendance via RevolvingFundService (scenarios 1/2/3)
     * 2. Calculate tontine total for the beneficiary (minus unpaid sanctions if any)
     * 3. Calculate beverage reliquat
     * 4. Credit cashboxes (development, beverage)
     * 5. Mark session as closed
     *
     * If ANY step fails → full rollback, no partial state.
     */
    @Transactional
    public SessionCloseResult closeSession(Long sessionId, AppUser user) {
        MeetingSessionEntity session = findById(sessionId);

        if (session.isClosed()) throw new IllegalStateException("Séance déjà clôturée");
        if (session.getBeneficiary() == null)
            throw new IllegalStateException("Le bénéficiaire du jour n'a pas été désigné");

        List<SessionAttendanceEntity> attendances = findAttendances(sessionId);

        // Accumulators for session report
        BigDecimal totalTontine        = BigDecimal.ZERO;
        BigDecimal totalBeveragePool   = BigDecimal.ZERO;
        BigDecimal totalDevelopment    = BigDecimal.ZERO;
        int        presentCount        = 0;
        int        fundCoveredCount    = 0;
        int        defaultCount        = 0;
        List<RevolvingFundService.AttendanceResult> results = new ArrayList<>();

        // ── Process each member ───────────────────────────────
        for (SessionAttendanceEntity att : attendances) {
            RevolvingFundService.AttendanceResult r =
                revolvingFundService.process(
                    toDomain(att.getMember()),
                    att.getAmountPaid(),
                    session,
                    att,
                    user
                );
            results.add(r);
            entityManager.merge(att);

            totalTontine      = totalTontine.add(r.getContributionToTontine());
            totalBeveragePool = totalBeveragePool.add(r.getContributionToBeverage());
            totalDevelopment  = totalDevelopment.add(r.getContributionToDevelopment());

            if (att.isPresent())                                      presentCount++;
            if (r.isCoveredByFund())                                  fundCoveredCount++;
            if (r.isDefault())                                        defaultCount++;
        }

        // ── Beverage reliquat calculation ─────────────────────
        BigDecimal actualBeverageCost =
            BEVERAGE_COST_PER_PERSON.multiply(BigDecimal.valueOf(presentCount));
        BigDecimal beverageReliquat =
            totalBeveragePool.subtract(actualBeverageCost).max(BigDecimal.ZERO);

        if (beverageReliquat.compareTo(BigDecimal.ZERO) > 0) {
            cashboxService.credit(CashboxType.beverage, beverageReliquat,
                "Reliquat boisson séance " + session.getSessionDate(),
                MovementOrigin.presence, session, null, null, user);
        }

        // ── Sanctions check on beneficiary ────────────────────
        MemberEntity beneficiaryEntity = session.getBeneficiary();
        Member beneficiary = toDomain(beneficiaryEntity);

        TypedQuery<SanctionEntity> sanctionsQuery = entityManager.createQuery(
                "SELECT s FROM SanctionEntity s WHERE s.member.id = :memberId AND s.status = :status ORDER BY s.sanctionDate DESC",
                SanctionEntity.class);
        sanctionsQuery.setParameter("memberId", beneficiary.getId());
        sanctionsQuery.setParameter("status", SanctionStatus.unpaid);
        List<SanctionEntity> unpaidSanctions = sanctionsQuery.getResultList();

        BigDecimal totalSanctionDeductions = unpaidSanctions.stream()
            .map(SanctionEntity::getAmount)
            .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal netTontine = totalTontine.subtract(totalSanctionDeductions).max(BigDecimal.ZERO);

        // Mark deducted sanctions as paid and record in sanction cashbox
        if (totalSanctionDeductions.compareTo(BigDecimal.ZERO) > 0) {
            for (SanctionEntity s : unpaidSanctions) {
                s.setStatus(SanctionStatus.paid);
                s.setPaymentDate(LocalDate.now());
                entityManager.merge(s);
            }
            cashboxService.credit(CashboxType.sanction, totalSanctionDeductions,
                "Sanctions retenues sur tontine de présence — " + beneficiary.getFullName(),
                MovementOrigin.sanction, session, beneficiaryEntity, null, user);
        }

        // ── Close session ─────────────────────────────────────
        session.setStatus(SessionStatus.closed);
        session.setClosedAt(LocalDateTime.now());
        entityManager.merge(session);

        log.info("Session {} closed — tontine net={} FCFA, beverage reliquat={} FCFA, defaults={}",
                 session.getSessionDate(), netTontine, beverageReliquat, defaultCount);

        // ── Build report ──────────────────────────────────────
        return SessionCloseResult.builder()
            .session(session)
            .beneficiary(beneficiary)
            .totalCotisants(attendances.size())
            .presentCount(presentCount)
            .fundCoveredCount(fundCoveredCount)
            .defaultCount(defaultCount)
            .grossTontine(totalTontine)
            .sanctionDeductions(totalSanctionDeductions)
            .netTontine(netTontine)
            .totalDevelopment(totalDevelopment)
            .totalBeveragePool(totalBeveragePool)
            .actualBeverageCost(actualBeverageCost)
            .beverageReliquat(beverageReliquat)
            .attendanceResults(results)
            .build();
    }
}
