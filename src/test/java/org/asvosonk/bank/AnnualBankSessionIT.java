package org.asvosonk.bank;

import jakarta.persistence.EntityManager;
import org.asvosonk.bank.application.usecase.CreateLoanUseCase;
import org.asvosonk.bank.application.usecase.RecordLoanRepaymentUseCase;
import org.asvosonk.bank.application.usecase.RecordSavingUseCase;
import org.asvosonk.bank.domain.model.Loan;
import org.asvosonk.bank.domain.model.LoanRepayment;
import org.asvosonk.bank.domain.model.Saving;
import org.asvosonk.bank.domain.repository.LoanRepaymentRepository;
import org.asvosonk.bank.domain.repository.LoanRepository;
import org.asvosonk.bank.domain.repository.SavingRepository;
import org.asvosonk.cashbox.domain.valueobject.MovementDirection;
import org.asvosonk.cashbox.domain.valueobject.MovementOrigin;
import org.asvosonk.security.domain.model.AppUser;
import org.asvosonk.security.domain.repository.AppUserRepository;
import org.asvosonk.session.application.service.SessionStepService;
import org.asvosonk.session.domain.valueobject.SessionStatus;
import org.asvosonk.session.domain.valueobject.SessionStep;
import org.asvosonk.session.infrastructure.persistence.entity.MeetingSessionEntity;
import org.asvosonk.session.infrastructure.persistence.entity.SessionReportEntity;
import org.asvosonk.session.infrastructure.persistence.repository.SessionReportRepository;
import org.asvosonk.support.AbstractIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Banque Annuelle saisie en séance : les opérations doivent être rattachées à
 * la séance — sur la ligne métier comme sur le mouvement de caisse — et la
 * clôture de l'étape doit figer les trois chiffres de la rubrique dans le
 * rapport.
 */
@SpringBootTest
@Transactional
class AnnualBankSessionIT extends AbstractIntegrationTest {

    @Autowired RecordSavingUseCase        recordSavingUseCase;
    @Autowired CreateLoanUseCase          createLoanUseCase;
    @Autowired RecordLoanRepaymentUseCase recordLoanRepaymentUseCase;
    @Autowired SessionStepService         sessionStepService;
    @Autowired SessionReportRepository    sessionReportRepository;
    @Autowired SavingRepository           savingRepository;
    @Autowired LoanRepository             loanRepository;
    @Autowired LoanRepaymentRepository    loanRepaymentRepository;
    @Autowired AppUserRepository          appUserRepository;
    @Autowired EntityManager              em;

    private AppUser admin;
    private Long memberId;
    private MeetingSessionEntity session;

    @BeforeEach
    void seed() {
        admin = appUserRepository.findByLogin("admin").orElseThrow();

        memberId = ((Number) em.createNativeQuery(
                "INSERT INTO member (full_name, join_date) VALUES ('Banker', CURRENT_DATE) RETURNING id")
            .getSingleResult()).longValue();

        session = new MeetingSessionEntity();
        session.setSessionDate(LocalDate.now());
        session.setStatus(SessionStatus.open);
        session.setCurrentStepEnum(SessionStep.BANQUE_ANNUELLE_OPEN);
        em.persist(session);
        em.flush();
    }

    private long countMovements(MovementDirection direction) {
        return (Long) em.createQuery(
                "SELECT COUNT(m) FROM CashboxMovementEntity m " +
                "WHERE m.session.id = :sessionId " +
                "AND m.origin = :origin " +
                "AND m.direction = :direction")
            .setParameter("sessionId", session.getId())
            .setParameter("origin", MovementOrigin.annual_bank)
            .setParameter("direction", direction)
            .getSingleResult();
    }

    @Test
    void savingDuringSessionIsLinkedToTheSession() {
        Saving saving = recordSavingUseCase.execute(
            memberId, new BigDecimal("5000"), LocalDate.now(), session, admin);
        em.flush();

        assertThat(saving.getSessionId()).isEqualTo(session.getId());
        assertThat(countMovements(MovementDirection.in)).isEqualTo(1);
        assertThat(savingRepository.getTotalSavingsBySessionId(session.getId()))
            .isEqualByComparingTo("5000");
    }

    @Test
    void savingRecordedOutsideASessionStaysUnlinked() {
        Saving saving = recordSavingUseCase.execute(
            memberId, new BigDecimal("5000"), LocalDate.now(), admin);
        em.flush();

        assertThat(saving.getSessionId()).isNull();
        assertThat(savingRepository.getTotalSavingsBySessionId(session.getId()))
            .isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    void loanDuringSessionIsLinkedToTheSession() {
        recordSavingUseCase.execute(
            memberId, new BigDecimal("10000"), LocalDate.now(), session, admin);
        Loan loan = createLoanUseCase.execute(memberId, new BigDecimal("5000"), session, admin);
        em.flush();

        assertThat(loan.getSessionId()).isEqualTo(session.getId());
        assertThat(countMovements(MovementDirection.out)).isEqualTo(1);
        assertThat(loanRepository.getTotalLoanedBySessionId(session.getId()))
            .isEqualByComparingTo("5000");
    }

    @Test
    void repaymentDuringSessionIsLinkedToTheSession() {
        recordSavingUseCase.execute(
            memberId, new BigDecimal("10000"), LocalDate.now(), session, admin);
        Loan loan = createLoanUseCase.execute(memberId, new BigDecimal("5000"), session, admin);
        LoanRepayment repayment = recordLoanRepaymentUseCase.execute(
            loan.getId(), new BigDecimal("2000"), session, admin);
        em.flush();

        assertThat(repayment.getSessionId()).isEqualTo(session.getId());
        // Une sortie (l'emprunt) et deux entrées (l'épargne, le remboursement).
        assertThat(countMovements(MovementDirection.out)).isEqualTo(1);
        assertThat(countMovements(MovementDirection.in)).isEqualTo(2);
        assertThat(loanRepaymentRepository.getTotalRepaidBySessionId(session.getId()))
            .isEqualByComparingTo("2000");
    }

    /**
     * Le point clé : épargnes et remboursements sont deux entrées de la même
     * caisse, distinguées par la ligne métier et non par le libellé du
     * mouvement — un libellé changé ne doit plus pouvoir fausser le rapport.
     */
    @Test
    void closingTheStepFreezesTheThreeFiguresInTheReport() {
        recordSavingUseCase.execute(
            memberId, new BigDecimal("10000"), LocalDate.now(), session, admin);
        Loan loan = createLoanUseCase.execute(memberId, new BigDecimal("6000"), session, admin);
        recordLoanRepaymentUseCase.execute(loan.getId(), new BigDecimal("2000"), session, admin);
        em.flush();

        sessionStepService.transitionToNext(
            session.getId(), admin, SessionStep.BANQUE_ANNUELLE_OPEN);
        em.flush();
        em.clear();

        SessionReportEntity report =
            sessionReportRepository.findBySessionId(session.getId()).orElseThrow();
        assertThat(report.getBanqueAnnuelleSavings()).isEqualByComparingTo("10000");
        assertThat(report.getBanqueAnnuelleRepayments()).isEqualByComparingTo("2000");
        assertThat(report.getBanqueAnnuelleLoans()).isEqualByComparingTo("6000");

        MeetingSessionEntity reloaded = em.find(MeetingSessionEntity.class, session.getId());
        assertThat(reloaded.getCurrentStepEnum()).isEqualTo(SessionStep.BANQUE_ANNUELLE_CLOSED);
        assertThat(reloaded.getBanqueAnnuelleClosedAt()).isNotNull();
    }

    @Test
    void operationsOfAnotherSessionAreNotCountedInTheReport() {
        // Séance précédente, déjà close : une seule séance peut être ouverte
        // à la fois (contrainte ux_session_single_open).
        MeetingSessionEntity other = new MeetingSessionEntity();
        other.setSessionDate(LocalDate.now().minusWeeks(1));
        other.setStatus(SessionStatus.closed);
        other.setCurrentStepEnum(SessionStep.BANQUE_ANNUELLE_OPEN);
        em.persist(other);
        em.flush();

        recordSavingUseCase.execute(
            memberId, new BigDecimal("7000"), LocalDate.now(), other, admin);
        recordSavingUseCase.execute(
            memberId, new BigDecimal("3000"), LocalDate.now(), session, admin);
        em.flush();

        assertThat(savingRepository.getTotalSavingsBySessionId(session.getId()))
            .isEqualByComparingTo("3000");
        assertThat(savingRepository.getTotalSavingsBySessionId(other.getId()))
            .isEqualByComparingTo("7000");
    }

    /**
     * Marquer l'emprunt « remboursé » repasse par le mapper : le rattachement à
     * la séance d'octroi doit survivre à cette réécriture.
     */
    @Test
    void fullyRepayingALoanKeepsItsSessionLink() {
        recordSavingUseCase.execute(
            memberId, new BigDecimal("10000"), LocalDate.now(), session, admin);
        Loan loan = createLoanUseCase.execute(memberId, new BigDecimal("1000"), session, admin);
        recordLoanRepaymentUseCase.execute(loan.getId(), loan.getTotalDue(), session, admin);
        em.flush();
        em.clear();

        Loan reloaded = loanRepository.findById(loan.getId()).orElseThrow();
        assertThat(reloaded.isRepaid()).isTrue();
        assertThat(reloaded.getSessionId()).isEqualTo(session.getId());
    }
}
