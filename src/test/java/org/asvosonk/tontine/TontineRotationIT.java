package org.asvosonk.tontine;

import jakarta.persistence.EntityManager;
import org.asvosonk.support.AbstractIntegrationTest;
import org.asvosonk.tontine.application.usecase.RecordTontineContributionUseCase;
import org.asvosonk.tontine.domain.model.TontineDebt;
import org.asvosonk.tontine.domain.repository.TontineDebtRepository;
import org.asvosonk.tontine.domain.valueobject.DebtStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * F-07 — Grande tontine debt settlement.
 *
 * <p>Debt orientation invariant: a debt is stored as
 * {@code debtor = beneficiary (received)}, {@code creditor = contributor (gave)}.
 * A later contribution where the current contributor is that debtor must settle
 * the debt (mark it {@code repaid}) instead of piling up a mirror debt.
 */
@SpringBootTest
@Transactional
class TontineRotationIT extends AbstractIntegrationTest {

    @Autowired RecordTontineContributionUseCase useCase;
    @Autowired TontineDebtRepository debtRepository;
    @Autowired EntityManager em;

    private static final BigDecimal AMOUNT = new BigDecimal("5000");

    private Long tourId;
    private Long memberA;
    private Long memberB;
    private Long session1;
    private Long session2;

    @BeforeEach
    void seed() {
        memberA = insertMember("Rotation A");
        memberB = insertMember("Rotation B");

        tourId = ((Number) em.createNativeQuery(
                "INSERT INTO tontine_tour (start_date) VALUES (CURRENT_DATE) RETURNING id")
            .getSingleResult()).longValue();

        insertParticipant(tourId, memberA, 1);
        insertParticipant(tourId, memberB, 2);

        session1 = insertSession("2026-01-05");
        session2 = insertSession("2026-02-05");
        em.flush();
    }

    @Test
    void reverseContributionSettlesTheDebtInsteadOfDuplicating() {
        // Session 1: beneficiary A, B contributes → debt (debtor=A, creditor=B).
        useCase.execute(tourId, session1, memberB, memberA, AMOUNT);
        em.flush();

        List<TontineDebt> afterS1 = debtRepository.findByTourIdAndStatus(tourId, DebtStatus.owed);
        assertThat(afterS1).hasSize(1);
        assertThat(afterS1.get(0).getDebtorId()).isEqualTo(memberA);
        assertThat(afterS1.get(0).getCreditorId()).isEqualTo(memberB);

        // Session 2: beneficiary B, A contributes → A (the debtor) pays B (creditor).
        // This must SETTLE the S1 debt, not create a second one.
        useCase.execute(tourId, session2, memberA, memberB, AMOUNT);
        em.flush();
        em.clear();

        List<TontineDebt> owed = debtRepository.findByTourIdAndStatus(tourId, DebtStatus.owed);
        assertThat(owed).as("the debt must be settled, no owed debt remaining").isEmpty();

        List<TontineDebt> all = debtRepository.findByTourIdOrderByCreatedAtDesc(tourId);
        assertThat(all).as("no mirror debt should have been created").hasSize(1);
        assertThat(all.get(0).getStatus()).isEqualTo(DebtStatus.repaid);
        assertThat(all.get(0).getRepaymentSessionId()).isEqualTo(session2);
    }

    @Test
    void leBeneficiaireCotisePourLuiMemeSansCreerDeDette() {
        // Le bénéficiaire du jour cotise lui aussi : son versement grossit la
        // somme qu'il perçoit, mais personne ne la lui doit en retour.
        var contribution = useCase.execute(tourId, session1, memberA, memberA, AMOUNT);

        em.flush();
        em.clear();

        assertThat(contribution.getAmount()).isEqualByComparingTo(AMOUNT);
        assertThat(contribution.isPaid()).isTrue();
        assertThat(debtRepository.findByTourIdAndStatus(tourId, DebtStatus.owed))
            .as("une cotisation à soi-même ne crée aucune dette")
            .isEmpty();
    }

    // ── seed helpers ─────────────────────────────────────────

    private Long insertMember(String name) {
        return ((Number) em.createNativeQuery(
                "INSERT INTO member (full_name, join_date) VALUES (:n, CURRENT_DATE) RETURNING id")
            .setParameter("n", name)
            .getSingleResult()).longValue();
    }

    private void insertParticipant(Long tour, Long member, int drawOrder) {
        em.createNativeQuery(
                "INSERT INTO tontine_participant (tour_id, member_id, draw_order) "
              + "VALUES (:t, :m, :o)")
            .setParameter("t", tour)
            .setParameter("m", member)
            .setParameter("o", drawOrder)
            .executeUpdate();
    }

    /**
     * Séance historique : elle sert de point d'accroche aux cotisations. Elle est
     * insérée close — une seule séance non clôturée peut exister à la fois.
     */
    private Long insertSession(String date) {
        return ((Number) em.createNativeQuery(
                "INSERT INTO meeting_session (session_date, status) "
              + "VALUES (CAST(:d AS date), CAST('closed' AS session_status)) RETURNING id")
            .setParameter("d", date)
            .getSingleResult()).longValue();
    }
}
