package org.asvosonk.session;

import jakarta.persistence.EntityManager;
import org.asvosonk.member.domain.repository.MemberRepository;
import org.asvosonk.presence.application.usecase.CreatePresenceTourUseCase;
import org.asvosonk.sanction.domain.repository.SanctionRepository;
import org.asvosonk.security.domain.model.AppUser;
import org.asvosonk.security.domain.repository.AppUserRepository;
import org.asvosonk.session.application.service.SessionService;
import org.asvosonk.session.application.service.SessionStepService;
import org.asvosonk.session.domain.valueobject.AttendanceStatus;
import org.asvosonk.session.domain.valueobject.SessionStep;
import org.asvosonk.session.infrastructure.persistence.entity.MeetingSessionEntity;
import org.asvosonk.session.infrastructure.persistence.entity.SessionReportEntity;
import org.asvosonk.session.infrastructure.persistence.repository.SessionReportRepository;
import org.asvosonk.session.presentation.request.AttendanceEntryForm;
import org.asvosonk.session.presentation.request.SessionForm;
import org.asvosonk.support.AbstractIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Mécanique du fond de roulement, telle que décrite par le bureau.
 *
 * <p>Le fond vaut 5 000 FCFA : il couvre deux séances entières (2 × 2 000) puis,
 * avec ses 1 000 derniers francs, complète un versement de 1 000 du membre — le
 * « compromis », après lequel il est épuisé. Une séance qu'aucun des deux ne
 * couvre est un échec, qui reste dû et pourra être recouvert plus tard.</p>
 */
@SpringBootTest
@Transactional
class RevolvingFundRulesIT extends AbstractIntegrationTest {

    @Autowired SessionService            sessionService;
    @Autowired SessionStepService        sessionStepService;
    @Autowired SessionReportRepository   reportRepository;
    @Autowired CreatePresenceTourUseCase createPresenceTour;
    @Autowired SanctionRepository        sanctionRepository;
    @Autowired AppUserRepository         appUserRepository;
    @Autowired MemberRepository          memberRepository;
    @Autowired EntityManager             em;

    private AppUser secretary;
    private Map<String, Long> members;

    @BeforeEach
    void seed() {
        secretary = appUserRepository.findByLogin("admin").orElseThrow();
        em.createNativeQuery("UPDATE member SET status = 'resigned' WHERE full_name = 'Administrateur'")
            .executeUpdate();

        members = new HashMap<>();
        for (String name : List.of("Alpha", "Beta", "Gamma")) {
            Long id = ((Number) em.createNativeQuery(
                    "INSERT INTO member (full_name, join_date) VALUES (:n, DATE '2026-01-01') RETURNING id")
                .setParameter("n", name).getSingleResult()).longValue();
            members.put(name, id);
            em.createNativeQuery("INSERT INTO revolving_fund (member_id, balance) VALUES (:m, 5000)")
                .setParameter("m", id).executeUpdate();
        }
        createPresenceTour.execute(LocalDate.of(2026, 1, 5));
        em.flush();
    }

    /**
     * Deux séances cotisées par le fond, puis le compromis à 1 000 FCFA :
     * 2 000 + 2 000 + 1 000 = 5 000, le fond est exactement épuisé et la séance
     * est cotisée en entier — la tontine du bénéficiaire ne perd rien.
     */
    @Test
    void leFondCouvreDeuxSeancesPuisCompleteUnVersementDe1000() {
        Long alpha = members.get("Alpha");

        runSession(LocalDate.of(2026, 2, 2), "Beta", Map.of(alpha, "0"));
        assertThat(fundBalance(alpha)).isEqualByComparingTo("3000");

        runSession(LocalDate.of(2026, 2, 9), "Gamma", Map.of(alpha, "0"));
        assertThat(fundBalance(alpha)).isEqualByComparingTo("1000");

        // Compromis : le membre apporte 1 000, le fond met ses 1 000 derniers.
        SessionReportEntity report = runSession(LocalDate.of(2026, 2, 16), "Beta", Map.of(alpha, "1000"));

        assertThat(fundBalance(alpha)).isEqualByComparingTo("0");
        assertThat(statusOf(alpha, LocalDate.of(2026, 2, 16)))
            .isEqualTo(AttendanceStatus.covered_by_fund);
        // La séance est cotisée en entier : Alpha compte pour 1 000 dans la tontine.
        assertThat(report.getPresenceGrossTontine()).isEqualByComparingTo("3000");
        assertThat(report.getPresenceDefaultCount()).isZero();
    }

    /**
     * Fond épuisé et rien apporté : c'est l'échec. La tontine du bénéficiaire
     * perd 1 000 FCFA, la boisson 500 et le développement 500, et le membre est
     * sanctionné de 500 FCFA.
     */
    @Test
    void sansFondNiEspecesLaSeanceEstUnEchec() {
        Long alpha = members.get("Alpha");
        emptyFund(alpha);

        SessionReportEntity report = runSession(LocalDate.of(2026, 3, 2), "Beta", Map.of(alpha, "0"));

        assertThat(report.getPresenceDefaultCount()).isEqualTo(1);
        assertThat(report.getPresenceGrossTontine()).isEqualByComparingTo("2000"); // 3 membres − 1 échec
        assertThat(report.getPresenceDevelopmentTotal()).isEqualByComparingTo("1000");
        assertThat(statusOf(alpha, LocalDate.of(2026, 3, 2)))
            .isEqualTo(AttendanceStatus.default_status);
        assertThat(sanctionAmounts(alpha)).contains(new BigDecimal("500.00"));
    }

    /**
     * Le scénario du bureau : deux séances cotisées par le fond, une troisième en
     * échec, puis 8 000 FCFA apportés la quatrième semaine. Cela rembourse les
     * deux avances, recouvre l'échec et paie la séance tenante — le fond revient
     * à 5 000 et le membre est totalement à jour.
     */
    @Test
    void huitMilleSoldentDeuxAvancesUnEchecEtLaSeanceTenante() {
        Long alpha = members.get("Alpha");

        runSession(LocalDate.of(2026, 4, 6), "Beta", Map.of(alpha, "0"));   // avance 1
        runSession(LocalDate.of(2026, 4, 13), "Gamma", Map.of(alpha, "0")); // avance 2
        runSession(LocalDate.of(2026, 4, 20), "Beta", Map.of(alpha, "0"));  // échec
        assertThat(fundBalance(alpha)).isEqualByComparingTo("1000");
        assertThat(statusOf(alpha, LocalDate.of(2026, 4, 20)))
            .isEqualTo(AttendanceStatus.default_status);

        SessionReportEntity report = runSession(LocalDate.of(2026, 4, 27), "Gamma", Map.of(alpha, "8000"));

        // Fond reconstitué : 1 000 + 2 000 + 2 000 = 5 000.
        assertThat(fundBalance(alpha)).isEqualByComparingTo("5000");
        // L'échec est recouvert, la séance tenante payée en espèces.
        assertThat(statusOf(alpha, LocalDate.of(2026, 4, 20))).isEqualTo(AttendanceStatus.recovered);
        assertThat(statusOf(alpha, LocalDate.of(2026, 4, 27))).isEqualTo(AttendanceStatus.up_to_date);
        // Retour en caisse : les 4 000 des deux avances remboursées.
        assertThat(report.getPresenceReturnToFund()).isEqualByComparingTo("4000");
        // Le rattrapage rend 1 000 au bénéficiaire de la séance ratée.
        assertThat(report.getPresenceRecoveryTotal()).isEqualByComparingTo("1000");

        // Le recouvrement alimente la boisson et le développement de la séance
        // tenante : 3 membres × 500, plus les 500 du rattrapage.
        assertThat(report.getPresenceDevelopmentTotal()).isEqualByComparingTo("2000");

        List<SessionService.RecoveredFailure> recoveries =
            sessionService.findRecoveriesDuring(sessionIdOf(LocalDate.of(2026, 4, 27)));
        assertThat(recoveries).hasSize(1);
        assertThat(recoveries.get(0).amount()).isEqualByComparingTo("1000");
        assertThat(recoveries.get(0).beneficiaryId()).isEqualTo(members.get("Beta"));
    }

    /**
     * Quatre mille francs alors que deux séances ont été cotisées par le fond :
     * les deux avances sont remboursées, puis le fond — de nouveau garni —
     * reprend la séance tenante.
     */
    @Test
    void quatreMilleRemboursentLesDeuxAvancesEtLeFondReprendLaSeance() {
        Long alpha = members.get("Alpha");

        runSession(LocalDate.of(2026, 5, 4), "Beta", Map.of(alpha, "0"));
        runSession(LocalDate.of(2026, 5, 11), "Gamma", Map.of(alpha, "0"));

        runSession(LocalDate.of(2026, 5, 18), "Beta", Map.of(alpha, "4000"));

        // 1 000 + 4 000 = 5 000, puis 2 000 avancés pour la séance tenante.
        assertThat(fundBalance(alpha)).isEqualByComparingTo("3000");
        assertThat(statusOf(alpha, LocalDate.of(2026, 5, 18)))
            .isEqualTo(AttendanceStatus.covered_by_fund);
        assertThat(openAdvances(alpha)).isEqualTo(1);
    }

    /**
     * Le bénéficiaire de la tontine de présence ne peut pas repartir avec l'argent
     * en restant endetté : fond épuisé et rien apporté, sa mise à jour est prélevée
     * d'office sur sa propre tontine.
     *
     * <p>Beta et Gamma cotisent 2 000 chacun, soit 2 000 de tontine. Alpha, le
     * bénéficiaire, n'a rien. Ses 2 000 sont puisés dans cette tontine : sa
     * cotisation est acquittée — dont 1 000 qui reviennent dans la tontine — et il
     * perçoit les 1 000 restants. Les 4 000 réellement apportés se retrouvent
     * intégralement : 1 500 au développement, 1 500 de boisson, 1 000 pour lui.</p>
     */
    @Test
    void leBeneficiaireEstMisAJourDOfficeSurSaTontine() {
        Long alpha = members.get("Alpha");
        emptyFund(alpha);

        SessionReportEntity report = runSession(LocalDate.of(2026, 6, 1), "Alpha", Map.of(alpha, "0"));

        assertThat(report.getPresenceFundCatchUp()).isEqualByComparingTo("2000");
        assertThat(report.getPresenceGrossTontine()).isEqualByComparingTo("3000");
        assertThat(report.getPresenceNetTontine()).isEqualByComparingTo("1000");
        // Ni échec, ni avance du fond : il est bel et bien à jour.
        assertThat(report.getPresenceDefaultCount()).isZero();
        assertThat(report.getPresenceFundCoveredCount()).isZero();
        assertThat(statusOf(alpha, LocalDate.of(2026, 6, 1))).isEqualTo(AttendanceStatus.up_to_date);
        assertThat(fundBalance(alpha)).isEqualByComparingTo("0");
        assertThat(report.getPresenceDevelopmentTotal()).isEqualByComparingTo("1500");
    }

    /**
     * Versement insuffisant : seul le complément manquant est prélevé sur la
     * tontine, jamais davantage.
     */
    @Test
    void seulLeComplementManquantEstPreleveSurLaTontine() {
        Long alpha = members.get("Alpha");
        emptyFund(alpha);

        SessionReportEntity report = runSession(LocalDate.of(2026, 6, 8), "Alpha", Map.of(alpha, "500"));

        assertThat(report.getPresenceFundCatchUp()).isEqualByComparingTo("1500");
        assertThat(report.getPresenceGrossTontine()).isEqualByComparingTo("3000");
        assertThat(report.getPresenceNetTontine()).isEqualByComparingTo("1500");
        assertThat(statusOf(alpha, LocalDate.of(2026, 6, 8))).isEqualTo(AttendanceStatus.up_to_date);
    }

    /** Bénéficiaire déjà à jour : sa tontine lui revient sans prélèvement. */
    @Test
    void unBeneficiaireAJourNeSubitAucunPrelevement() {
        Long alpha = members.get("Alpha");

        SessionReportEntity report = runSession(LocalDate.of(2026, 6, 15), "Alpha", Map.of(alpha, "2000"));

        assertThat(report.getPresenceFundCatchUp()).isEqualByComparingTo("0");
        assertThat(report.getPresenceGrossTontine()).isEqualByComparingTo("3000");
        assertThat(report.getPresenceNetTontine()).isEqualByComparingTo("3000");
        assertThat(fundBalance(alpha)).isEqualByComparingTo("5000");
    }

    // ── Utilitaires ──────────────────────────────────────────────────

    /** Déroule une séance complète : ouverture, saisie, clôture de la présence. */
    private SessionReportEntity runSession(LocalDate date, String beneficiary, Map<Long, String> paid) {
        SessionForm form = new SessionForm();
        form.setSessionDate(date);
        MeetingSessionEntity session = sessionService.create(form, secretary);
        em.flush();

        sessionStepService.transitionToNext(session.getId(), secretary, SessionStep.CREATED);
        sessionService.setBeneficiary(session.getId(), members.get(beneficiary));

        for (Long memberId : members.values()) {
            AttendanceEntryForm entry = new AttendanceEntryForm();
            entry.setMemberId(memberId);
            entry.setPresent(true);
            entry.setAmountPaid(new BigDecimal(paid.getOrDefault(memberId, "2000")));
            sessionService.saveAttendanceEntry(session.getId(), entry);
        }
        em.flush();

        sessionStepService.transitionToNext(session.getId(), secretary, SessionStep.PRESENCE_OPEN);
        em.flush();
        em.clear();

        SessionReportEntity report = reportRepository.findBySessionId(session.getId()).orElseThrow();
        finish(session.getId());
        return report;
    }

    /**
     * Mène la séance jusqu'à son rapport. Une seule séance peut être en cours :
     * la suivante ne s'ouvrira qu'une fois celle-ci terminée.
     */
    private void finish(Long sessionId) {
        for (SessionStep from : List.of(
                SessionStep.PRESENCE_CLOSED, SessionStep.TONTINE_OPEN, SessionStep.TONTINE_CLOSED,
                SessionStep.BANQUE_PROJET_OPEN, SessionStep.BANQUE_PROJET_CLOSED,
                SessionStep.BANQUE_ANNUELLE_OPEN, SessionStep.BANQUE_ANNUELLE_CLOSED)) {
            sessionStepService.transitionToNext(sessionId, secretary, from);
        }
        em.flush();
        em.clear();
    }

    private BigDecimal fundBalance(Long memberId) {
        return (BigDecimal) em.createNativeQuery(
                "SELECT balance FROM revolving_fund WHERE member_id = :m")
            .setParameter("m", memberId).getSingleResult();
    }

    private void emptyFund(Long memberId) {
        em.createNativeQuery("UPDATE revolving_fund SET balance = 0 WHERE member_id = :m")
            .setParameter("m", memberId).executeUpdate();
        em.flush();
    }

    private Long sessionIdOf(LocalDate date) {
        return ((Number) em.createNativeQuery(
                "SELECT id FROM meeting_session WHERE session_date = :d")
            .setParameter("d", date).getSingleResult()).longValue();
    }

    private AttendanceStatus statusOf(Long memberId, LocalDate date) {
        String status = (String) em.createNativeQuery("""
                SELECT a.attendance_status::text FROM session_attendance a
                  JOIN meeting_session s ON s.id = a.session_id
                 WHERE a.member_id = :m AND s.session_date = :d
                """)
            .setParameter("m", memberId).setParameter("d", date).getSingleResult();
        return AttendanceStatus.valueOf(status);
    }

    private int openAdvances(Long memberId) {
        return ((Number) em.createNativeQuery("""
                SELECT COUNT(*) FROM revolving_fund_movement mv
                  JOIN revolving_fund f ON f.id = mv.fund_id
                 WHERE f.member_id = :m AND mv.movement_type = 'advance' AND mv.is_recovered = false
                """)
            .setParameter("m", memberId).getSingleResult()).intValue();
    }

    @SuppressWarnings("unchecked")
    private List<BigDecimal> sanctionAmounts(Long memberId) {
        return em.createNativeQuery("SELECT amount FROM sanction WHERE member_id = :m")
            .setParameter("m", memberId).getResultList();
    }
}
