package org.asvosonk.tontine;

import jakarta.persistence.EntityManager;
import org.asvosonk.common.domain.exception.BusinessRuleException;
import org.asvosonk.security.domain.model.AppUser;
import org.asvosonk.security.domain.repository.AppUserRepository;
import org.asvosonk.session.application.service.SessionStepService;
import org.asvosonk.session.domain.valueobject.SessionStep;
import org.asvosonk.support.AbstractIntegrationTest;
import org.asvosonk.tontine.application.usecase.RecordTontineContributionUseCase;
import org.asvosonk.tontine.application.usecase.RecordTontineContributionUseCase.PendingFailure;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Échecs de Grande Tontine.
 *
 * <ul>
 *   <li>Leurs conséquences (sanction, recul dans l'ordre) ne s'appliquent qu'à la
 *       clôture de la rubrique : une erreur corrigée avant n'en a aucune.</li>
 *   <li>Un échec passé se recouvre — on verse au bénéficiaire de cette séance-là —
 *       avant toute cotisation au bénéficiaire du jour : montant imposé si le membre
 *       lui devait de l'argent, sinon un multiple de 5 000 FCFA non nul.</li>
 * </ul>
 */
@SpringBootTest
@Transactional
class TontineFailureRecoveryIT extends AbstractIntegrationTest {

    private static final BigDecimal FIVE = new BigDecimal("5000");

    @Autowired RecordTontineContributionUseCase useCase;
    @Autowired SessionStepService               sessionStepService;
    @Autowired AppUserRepository                appUserRepository;
    @Autowired EntityManager                    em;

    private AppUser admin;
    private Long tourId;
    private Long a;
    private Long b;
    private Long c;

    @BeforeEach
    void seed() {
        admin = appUserRepository.findByLogin("admin").orElseThrow();
        a = insertMember("Echec A");
        b = insertMember("Echec B");
        c = insertMember("Echec C");
        tourId = ((Number) em.createNativeQuery(
                "INSERT INTO tontine_tour (start_date) VALUES (DATE '2026-06-01') RETURNING id")
            .getSingleResult()).longValue();
        insertParticipant(a, 1);
        insertParticipant(b, 2);
        insertParticipant(c, 3);
        em.flush();
    }

    // ── M2 : conséquences reportées à la clôture ─────────────

    @Test
    void uneErreurCorrigeeAvantLaClotureNEntraineNiSanctionNiRecul() {
        Long session = insertSession("2026-07-13", "TONTINE_OPEN", "open");

        useCase.replaceInSession(tourId, session, b, a, FIVE);
        useCase.replaceInSession(tourId, session, c, a, BigDecimal.ZERO);   // erreur de saisie
        em.flush();
        assertThat(tontineSanctionsOf(c)).isZero();
        assertThat(drawOrderOf(c)).isEqualTo(3);

        useCase.replaceInSession(tourId, session, c, a, FIVE);             // corrigée
        sessionStepService.transitionToNext(session, admin, SessionStep.TONTINE_OPEN);
        em.flush();

        assertThat(tontineSanctionsOf(c)).isZero();
        assertThat(drawOrderOf(c)).isEqualTo(3);
    }

    @Test
    void unEchecMaintenuEstSanctionneALaClotureSeulement() {
        Long session = insertSession("2026-07-13", "TONTINE_OPEN", "open");

        useCase.replaceInSession(tourId, session, b, a, FIVE);
        useCase.replaceInSession(tourId, session, c, a, BigDecimal.ZERO);
        em.flush();
        assertThat(tontineSanctionsOf(c)).as("aucune sanction pendant la saisie").isZero();

        sessionStepService.transitionToNext(session, admin, SessionStep.TONTINE_OPEN);
        em.flush();

        assertThat(tontineSanctionsOf(c)).isEqualTo(1);
        assertThat(sanctionAmountOf(c)).isEqualByComparingTo("2000");
        assertThat(drawOrderOf(c)).as("pas encore bénéficiaire : dernière place").isEqualTo(4);
    }

    // ── Recouvrement : montant libre ─────────────────────────

    @Test
    void unEchecPasseDoitEtreRecouvertAvantLaCotisationDuJour() {
        // Séance 1 (close) : bénéficiaire A, C a fait un échec.
        Long s1 = insertSession("2026-07-06", "REPORT_GENERATED", "closed");
        Long failure = insertContribution(s1, c, a, "0", "default_status");
        markBenefited(a);
        Long s2 = insertSession("2026-07-13", "TONTINE_OPEN", "open");
        em.flush();

        assertThat(useCase.hasUnrecoveredFailures(tourId)).isTrue();
        List<PendingFailure> pending = useCase.pendingFailures(tourId, s2, c);
        assertThat(pending).hasSize(1);
        assertThat(pending.get(0).beneficiaryId()).isEqualTo(a);
        assertThat(pending.get(0).imposedAmount()).as("montant libre").isNull();

        assertThatThrownBy(() -> useCase.replaceInSession(tourId, s2, c, b, FIVE))
            .isInstanceOf(BusinessRuleException.class)
            .hasMessageContaining("Recouvrez d'abord");
        assertThatThrownBy(() -> useCase.recordSheetLine(tourId, s2, c, b, FIVE,
                Map.of(failure, new BigDecimal("7000"))))
            .isInstanceOf(BusinessRuleException.class)
            .hasMessageContaining("multiple");

        // Un nouvel échec reste possible sans recouvrer.
        useCase.recordSheetLine(tourId, s2, c, b, BigDecimal.ZERO, Map.of());

        // Recouvrement de 10 000 puis cotisation du jour : la ligne passe d'un bloc.
        useCase.recordSheetLine(tourId, s2, c, b, FIVE, Map.of(failure, new BigDecimal("10000")));
        em.flush();

        assertThat(useCase.pendingFailures(tourId, s2, c).get(0).recordedAmount())
            .isEqualByComparingTo("10000");
        assertThat(owedDebt(a, c)).as("le recouvrement est une cotisation tardive à A")
            .isEqualByComparingTo("10000");
        assertThat(owedDebt(b, c)).isEqualByComparingTo("5000");
        assertThat(useCase.recoveriesDuring(s2)).hasSize(1);
        assertThat(useCase.hasUnrecoveredFailures(tourId)).isFalse();
    }

    // ── Recouvrement : montant imposé ────────────────────────

    @Test
    void unMembreQuiDevaitLaSommeRecouvreExactementSaDette() {
        // A a bénéficié (B lui avait cotisé 5 000 : A doit 5 000 à B), puis A a
        // fait un échec le jour où B était bénéficiaire.
        Long s1 = insertSession("2026-07-06", "REPORT_GENERATED", "closed");
        Long s2 = insertSession("2026-07-13", "REPORT_GENERATED", "closed");
        insertDebt(a, b, s1);
        Long failure = insertContribution(s2, a, b, "0", "default_status");
        markBenefited(a);
        markBenefited(b);
        Long s3 = insertSession("2026-07-20", "TONTINE_OPEN", "open");
        em.flush();

        assertThat(useCase.pendingFailures(tourId, s3, a).get(0).imposedAmount())
            .isEqualByComparingTo("5000");

        assertThatThrownBy(() -> useCase.replaceRecoveryInSession(tourId, s3, a, failure, new BigDecimal("10000")))
            .isInstanceOf(BusinessRuleException.class)
            .hasMessageContaining("exactement");

        useCase.replaceRecoveryInSession(tourId, s3, a, failure, FIVE);
        em.flush();
        assertThat(owedDebt(a, b)).as("la dette envers B est soldée").isNull();

        // Corrigé avant la clôture (0 = retrait) : la dette redevient due.
        useCase.replaceRecoveryInSession(tourId, s3, a, failure, BigDecimal.ZERO);
        em.flush();
        assertThat(owedDebt(a, b)).isEqualByComparingTo("5000");
    }

    // ── seed helpers ─────────────────────────────────────────

    private Long insertMember(String name) {
        return ((Number) em.createNativeQuery(
                "INSERT INTO member (full_name, join_date) VALUES (:n, CURRENT_DATE) RETURNING id")
            .setParameter("n", name)
            .getSingleResult()).longValue();
    }

    private void insertParticipant(Long member, int drawOrder) {
        em.createNativeQuery(
                "INSERT INTO tontine_participant (tour_id, member_id, draw_order) VALUES (:t, :m, :o)")
            .setParameter("t", tourId).setParameter("m", member).setParameter("o", drawOrder)
            .executeUpdate();
    }

    private Long insertSession(String date, String step, String status) {
        return ((Number) em.createNativeQuery("""
                INSERT INTO meeting_session (session_date, status, current_step)
                VALUES (CAST(:d AS date), CAST(:st AS session_status), :step) RETURNING id
                """)
            .setParameter("d", date).setParameter("st", status).setParameter("step", step)
            .getSingleResult()).longValue();
    }

    private Long insertContribution(Long session, Long contributor, Long beneficiary, String amount, String status) {
        return ((Number) em.createNativeQuery("""
                INSERT INTO tontine_contribution (tour_id, session_id, contributor_id, beneficiary_id, amount, status)
                VALUES (:t, :s, :c, :b, CAST(:a AS numeric), CAST(:st AS payment_status)) RETURNING id
                """)
            .setParameter("t", tourId).setParameter("s", session).setParameter("c", contributor)
            .setParameter("b", beneficiary).setParameter("a", amount).setParameter("st", status)
            .getSingleResult()).longValue();
    }

    private void insertDebt(Long debtor, Long creditor, Long originSession) {
        em.createNativeQuery("""
                INSERT INTO tontine_debt (tour_id, debtor_id, creditor_id, amount, origin_session_id, status)
                VALUES (:t, :d, :c, 5000, :s, 'owed')
                """)
            .setParameter("t", tourId).setParameter("d", debtor).setParameter("c", creditor)
            .setParameter("s", originSession)
            .executeUpdate();
    }

    private void markBenefited(Long member) {
        em.createNativeQuery(
                "UPDATE tontine_participant SET has_benefited = true WHERE tour_id = :t AND member_id = :m")
            .setParameter("t", tourId).setParameter("m", member)
            .executeUpdate();
    }

    private long tontineSanctionsOf(Long member) {
        return ((Number) em.createNativeQuery(
                "SELECT COUNT(*) FROM sanction WHERE member_id = :m AND origin = 'tontine_default'")
            .setParameter("m", member).getSingleResult()).longValue();
    }

    private BigDecimal sanctionAmountOf(Long member) {
        return (BigDecimal) em.createNativeQuery(
                "SELECT amount FROM sanction WHERE member_id = :m AND origin = 'tontine_default'")
            .setParameter("m", member).getSingleResult();
    }

    private int drawOrderOf(Long member) {
        return ((Number) em.createNativeQuery(
                "SELECT draw_order FROM tontine_participant WHERE tour_id = :t AND member_id = :m")
            .setParameter("t", tourId).setParameter("m", member).getSingleResult()).intValue();
    }

    private BigDecimal owedDebt(Long debtor, Long creditor) {
        List<?> rows = em.createNativeQuery("""
                SELECT amount FROM tontine_debt
                 WHERE tour_id = :t AND debtor_id = :d AND creditor_id = :c AND status = 'owed'
                """)
            .setParameter("t", tourId).setParameter("d", debtor).setParameter("c", creditor)
            .getResultList();
        return rows.isEmpty() ? null : (BigDecimal) rows.get(0);
    }
}
