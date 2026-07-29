package org.asvosonk.bank;

import jakarta.persistence.EntityManager;
import org.asvosonk.bank.application.usecase.GetMemberBankSummaryUseCase;
import org.asvosonk.support.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * F-47 — The loan-eligibility flag surfaced to the UI must mirror the real rule
 * enforced by CreateLoanUseCase, which requires the member to be active. A
 * suspended/resigned member with savings must NOT be advertised as eligible.
 */
@SpringBootTest
@Transactional
class LoanEligibilityIT extends AbstractIntegrationTest {

    @Autowired GetMemberBankSummaryUseCase summary;
    @Autowired EntityManager em;

    @Test
    void activeMemberWithSavingsIsEligible() {
        Long id = insertMember("Active Saver", "active");
        insertSaving(id, "10000");
        em.flush();

        assertThat(summary.execute(id).isEligibleForLoan()).isTrue();
    }

    @Test
    void suspendedMemberWithSavingsIsNotEligible() {
        Long id = insertMember("Suspended Saver", "suspended");
        insertSaving(id, "10000");
        em.flush();

        assertThat(summary.execute(id).isEligibleForLoan())
            .as("an inactive member the loan use case would reject must not look eligible")
            .isFalse();
    }

    private Long insertMember(String name, String status) {
        return ((Number) em.createNativeQuery(
                "INSERT INTO member (full_name, join_date, status) "
              + "VALUES (:n, CURRENT_DATE, CAST(:s AS member_status)) RETURNING id")
            .setParameter("n", name)
            .setParameter("s", status)
            .getSingleResult()).longValue();
    }

    private void insertSaving(Long memberId, String amount) {
        em.createNativeQuery(
                "INSERT INTO saving (member_id, operation_date, amount) "
              + "VALUES (:m, CURRENT_DATE, CAST(:a AS numeric))")
            .setParameter("m", memberId)
            .setParameter("a", amount)
            .executeUpdate();
    }
}
