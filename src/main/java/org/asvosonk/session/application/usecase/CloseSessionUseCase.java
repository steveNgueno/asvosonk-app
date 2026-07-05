package org.asvosonk.session.application.usecase;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.asvosonk.cashbox.application.service.CashboxService;
import org.asvosonk.cashbox.domain.valueobject.CashboxType;
import org.asvosonk.cashbox.domain.valueobject.MovementOrigin;
import org.asvosonk.member.domain.model.Member;
import org.asvosonk.member.domain.repository.MemberRepository;
import org.asvosonk.member.infrastructure.persistence.entity.MemberEntity;
import org.asvosonk.sanction.domain.valueobject.SanctionOrigin;
import org.asvosonk.sanction.domain.valueobject.SanctionStatus;
import org.asvosonk.sanction.infrastructure.persistence.entity.SanctionEntity;
import org.asvosonk.security.domain.model.AppUser;
import org.asvosonk.session.application.service.RevolvingFundService;
import org.asvosonk.session.domain.valueobject.AttendanceStatus;
import org.asvosonk.session.domain.valueobject.FundMovementType;
import org.asvosonk.session.domain.valueobject.SessionStatus;
import org.asvosonk.session.infrastructure.persistence.entity.MeetingSessionEntity;
import org.asvosonk.session.infrastructure.persistence.entity.RevolvingFundEntity;
import org.asvosonk.session.infrastructure.persistence.entity.RevolvingFundMovementEntity;
import org.asvosonk.session.infrastructure.persistence.entity.SessionAttendanceEntity;
import org.asvosonk.session.infrastructure.persistence.entity.SessionReportEntity;
import org.asvosonk.session.infrastructure.persistence.repository.SessionReportRepository;
import org.asvosonk.session.presentation.response.SessionCloseResult;
import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Use case: close a session, process all attendances, and generate the report.
 * This is the most complex use case, coordinating cashbox, revolving fund,
 * and sanction operations in a single atomic transaction.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CloseSessionUseCase {

    private static final BigDecimal PRESENCE_FEE            = new BigDecimal("2000");
    private static final BigDecimal BEVERAGE_COST_PER_PERSON = new BigDecimal("500");

    private final CashboxService            cashboxService;
    private final RevolvingFundService      revolvingFundService;
    private final MemberRepository          memberRepository;
    private final SessionReportRepository   sessionReportRepository;
    private final EntityManager             entityManager;

    @Transactional
    public SessionCloseResult execute(Long sessionId, AppUser user) {
        MeetingSessionEntity session = entityManager.find(MeetingSessionEntity.class, sessionId);
        if (session == null) throw new IllegalArgumentException("Séance introuvable : " + sessionId);
        if (session.isClosed()) throw new IllegalStateException("Séance déjà clôturée");
        if (session.getBeneficiary() == null)
            throw new IllegalStateException("Le bénéficiaire du jour n'a pas été désigné");

        List<SessionAttendanceEntity> attendances = entityManager.createQuery(
                "SELECT a FROM SessionAttendanceEntity a JOIN a.member m WHERE a.session.id = :sessionId ORDER BY m.fullName ASC",
                SessionAttendanceEntity.class)
            .setParameter("sessionId", sessionId)
            .getResultList();

        BigDecimal totalTontine      = BigDecimal.ZERO;
        BigDecimal totalBeveragePool = BigDecimal.ZERO;
        BigDecimal totalDevelopment  = BigDecimal.ZERO;
        int presentCount = 0, fundCoveredCount = 0, defaultCount = 0;
        List<RevolvingFundService.AttendanceResult> results = new ArrayList<>();

        for (SessionAttendanceEntity att : attendances) {
            Member member = memberRepository.findById(att.getMember().getId()).orElse(null);
            if (member == null) continue;

            RevolvingFundService.AttendanceResult r =
                revolvingFundService.process(member, att.getAmountPaid(), session, att, user);
            results.add(r);
            entityManager.merge(att);

            totalTontine      = totalTontine.add(r.getContributionToTontine());
            totalBeveragePool = totalBeveragePool.add(r.getContributionToBeverage());
            totalDevelopment  = totalDevelopment.add(r.getContributionToDevelopment());
            if (att.isPresent())                presentCount++;
            if (r.isCoveredByFund())             fundCoveredCount++;
            if (r.isDefault())                   defaultCount++;
        }

        BigDecimal actualBeverageCost = BEVERAGE_COST_PER_PERSON.multiply(BigDecimal.valueOf(presentCount));
        BigDecimal beverageReliquat = totalBeveragePool.subtract(actualBeverageCost).max(BigDecimal.ZERO);

        if (beverageReliquat.compareTo(BigDecimal.ZERO) > 0) {
            cashboxService.credit(CashboxType.beverage, beverageReliquat,
                "Reliquat boisson séance " + session.getSessionDate(),
                MovementOrigin.presence, session, null, null, user);
        }

        MemberEntity beneficiaryEntity = session.getBeneficiary();
        Member beneficiary = beneficiaryEntity != null
            ? memberRepository.findById(beneficiaryEntity.getId()).orElse(null)
            : null;

        BigDecimal totalSanctionDeductions = BigDecimal.ZERO;
        if (beneficiary != null) {
            TypedQuery<SanctionEntity> sQuery = entityManager.createQuery(
                "SELECT s FROM SanctionEntity s WHERE s.member.id = :memberId AND s.status = :status ORDER BY s.sanctionDate DESC",
                SanctionEntity.class);
            sQuery.setParameter("memberId", beneficiary.getId());
            sQuery.setParameter("status", SanctionStatus.unpaid);
            List<SanctionEntity> unpaidSanctions = sQuery.getResultList();

            totalSanctionDeductions = unpaidSanctions.stream()
                .map(SanctionEntity::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

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
        }

        BigDecimal netTontine = totalTontine.subtract(totalSanctionDeductions).max(BigDecimal.ZERO);

        // ── Persist session report for future reference ─────────────────
        SessionReportEntity report = new SessionReportEntity();
        report.setSessionId(sessionId);
        report.setGrossTontine(totalTontine);
        report.setSanctionDeductions(totalSanctionDeductions);
        report.setNetTontine(netTontine);
        report.setTotalDevelopment(totalDevelopment);
        report.setTotalBeveragePool(totalBeveragePool);
        report.setActualBeverageCost(actualBeverageCost);
        report.setBeverageReliquat(beverageReliquat);
        report.setTotalCotisants(attendances.size());
        report.setPresentCount(presentCount);
        report.setFundCoveredCount(fundCoveredCount);
        report.setDefaultCount(defaultCount);
        sessionReportRepository.save(report);

        session.setStatus(SessionStatus.closed);
        session.setClosedAt(LocalDateTime.now());
        entityManager.merge(session);

        log.info("Session {} closed — tontine net={} FCFA, beverage reliquat={} FCFA, defaults={}",
                 session.getSessionDate(), netTontine, beverageReliquat, defaultCount);

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
