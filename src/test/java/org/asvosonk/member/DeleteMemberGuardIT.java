package org.asvosonk.member;

import jakarta.persistence.EntityManager;
import org.asvosonk.common.domain.exception.BusinessRuleException;
import org.asvosonk.member.application.usecase.DeleteMemberUseCase;
import org.asvosonk.member.domain.repository.MemberRepository;
import org.asvosonk.support.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * F-11 — A member carrying financial history must never be hard-deleted
 * (which would cascade-erase fees, savings, loans, sanctions and tontine debts,
 * including debts owed to OTHER members). A member with no real activity can
 * still be removed.
 */
@SpringBootTest
@Transactional
class DeleteMemberGuardIT extends AbstractIntegrationTest {

    @Autowired DeleteMemberUseCase deleteMember;
    @Autowired MemberRepository memberRepository;
    @Autowired EntityManager em;

    @Test
    void memberWithNoActivityCanBeDeleted() {
        Long id = insertMember("Empty Member");
        // Mimic registration scaffolding: empty fee slot + zero-balance fund.
        em.createNativeQuery("INSERT INTO membership_fee (member_id, fee_type, amount_due) "
              + "VALUES (:id, CAST('registration' AS fee_type), 10000)")
            .setParameter("id", id).executeUpdate();
        em.createNativeQuery("INSERT INTO revolving_fund (member_id, balance) VALUES (:id, 0)")
            .setParameter("id", id).executeUpdate();
        em.flush();

        assertThatCode(() -> deleteMember.execute(id)).doesNotThrowAnyException();
        em.flush();
        assertThat(memberRepository.findById(id)).isEmpty();
    }

    @Test
    void memberWithPaidFeeIsRefused() {
        Long id = insertMember("Paid Member");
        em.createNativeQuery("INSERT INTO membership_fee (member_id, fee_type, amount_due, amount_paid) "
              + "VALUES (:id, CAST('registration' AS fee_type), 10000, 10000)")
            .setParameter("id", id).executeUpdate();
        em.flush();

        assertThatThrownBy(() -> deleteMember.execute(id))
            .isInstanceOf(BusinessRuleException.class)
            .hasMessageContaining("historique financier");
        em.clear();
        assertThat(memberRepository.findById(id)).isPresent();
    }

    @Test
    void memberWithSanctionIsRefused() {
        Long id = insertMember("Sanctioned Member");
        em.createNativeQuery("INSERT INTO sanction (member_id, sanction_date, amount, reason) "
              + "VALUES (:id, CURRENT_DATE, 2000, 'retard')")
            .setParameter("id", id).executeUpdate();
        em.flush();

        assertThatThrownBy(() -> deleteMember.execute(id))
            .isInstanceOf(BusinessRuleException.class);
        em.clear();
        assertThat(memberRepository.findById(id)).isPresent();
    }

    @Test
    void databaseRestrictsDirectDeleteOfSanctionedMember() {
        // Backstop: even bypassing the use case, the FK RESTRICT (V16) must
        // stop the DB from erasing a member with ledger rows.
        Long id = insertMember("SQL Delete Member");
        em.createNativeQuery("INSERT INTO sanction (member_id, sanction_date, amount, reason) "
              + "VALUES (:id, CURRENT_DATE, 2000, 'retard')")
            .setParameter("id", id).executeUpdate();
        em.flush();

        assertThatThrownBy(() -> {
            em.createNativeQuery("DELETE FROM member WHERE id = :id")
                .setParameter("id", id).executeUpdate();
            em.flush();
        }).hasMessageContaining("violates foreign key constraint");
    }

    private Long insertMember(String name) {
        return ((Number) em.createNativeQuery(
                "INSERT INTO member (full_name, join_date) VALUES (:n, CURRENT_DATE) RETURNING id")
            .setParameter("n", name)
            .getSingleResult()).longValue();
    }
}
