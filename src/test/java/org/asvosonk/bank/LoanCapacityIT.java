package org.asvosonk.bank;

import jakarta.persistence.EntityManager;
import org.asvosonk.bank.application.usecase.CreateLoanUseCase;
import org.asvosonk.bank.application.usecase.GetMemberBankSummaryUseCase;
import org.asvosonk.common.domain.exception.BusinessRuleException;
import org.asvosonk.security.domain.model.AppUser;
import org.asvosonk.security.domain.repository.AppUserRepository;
import org.asvosonk.support.AbstractIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Le plafond « 3 × l'épargne » porte sur l'endettement total en capital : deux
 * emprunts de 3 × l'épargne chacun portaient auparavant la dette à 6 × l'épargne.
 */
@SpringBootTest
@Transactional
class LoanCapacityIT extends AbstractIntegrationTest {

    @Autowired CreateLoanUseCase           createLoan;
    @Autowired GetMemberBankSummaryUseCase summary;
    @Autowired AppUserRepository           appUserRepository;
    @Autowired EntityManager               em;

    private AppUser admin;
    private Long memberId;

    @BeforeEach
    void seed() {
        admin = appUserRepository.findByLogin("admin").orElseThrow();
        memberId = ((Number) em.createNativeQuery(
                "INSERT INTO member (full_name, join_date) VALUES ('Emprunteur', CURRENT_DATE) RETURNING id")
            .getSingleResult()).longValue();
        em.createNativeQuery(
                "INSERT INTO saving (member_id, operation_date, amount) VALUES (:m, CURRENT_DATE, 20000)")
            .setParameter("m", memberId)
            .executeUpdate();
        em.createNativeQuery("UPDATE cashbox SET balance = 1000000 WHERE type = 'bank'").executeUpdate();
        em.flush();
    }

    @Test
    void lePlafondPorteSurLeCapitalDejaEmprunte() {
        createLoan.execute(memberId, new BigDecimal("50000"), admin);

        assertThatThrownBy(() -> createLoan.execute(memberId, new BigDecimal("30000"), admin))
            .isInstanceOf(BusinessRuleException.class)
            .hasMessageContaining("10000");

        // Capacité restante, intérêts exclus : 60 000 − 50 000.
        assertThat(createLoan.loanCapacity(memberId)).isEqualByComparingTo("10000");
        assertThat(summary.execute(memberId).getLoanCapacity()).isEqualByComparingTo("10000");

        createLoan.execute(memberId, new BigDecimal("10000"), admin);
        assertThat(createLoan.loanCapacity(memberId)).isEqualByComparingTo("0");
    }

    @Test
    void unRemboursementLibereDeLaCapacite() {
        var loan = createLoan.execute(memberId, new BigDecimal("60000"), admin);
        em.createNativeQuery(
                "INSERT INTO loan_repayment (loan_id, payment_date, amount) VALUES (:l, CURRENT_DATE, 15000)")
            .setParameter("l", loan.getId())
            .executeUpdate();
        em.flush();

        assertThat(createLoan.loanCapacity(memberId)).isEqualByComparingTo("15000");
    }
}
