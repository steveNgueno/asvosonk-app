package org.asvosonk.bank;

import jakarta.persistence.EntityManager;
import org.asvosonk.bank.application.usecase.RecordLoanRepaymentUseCase;
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
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * F-19 — A loan repayment must never exceed the remaining balance due. Over-
 * repayment would over-credit the bank cashbox and mark the loan repaid while
 * the books show more cash than was ever owed.
 */
@SpringBootTest
@Transactional
class LoanRepaymentCapIT extends AbstractIntegrationTest {

    @Autowired RecordLoanRepaymentUseCase repayUseCase;
    @Autowired AppUserRepository appUserRepository;
    @Autowired EntityManager em;

    private Long loanId;
    private AppUser admin;

    // amount 10000 @ 10% → total due 11000
    private static final BigDecimal TOTAL_DUE = new BigDecimal("11000.00");

    @BeforeEach
    void seed() {
        admin = appUserRepository.findByLogin("admin").orElseThrow();
        Long memberId = ((Number) em.createNativeQuery(
                "INSERT INTO member (full_name, join_date) VALUES ('Loaner', CURRENT_DATE) RETURNING id")
            .getSingleResult()).longValue();
        loanId = ((Number) em.createNativeQuery(
                "INSERT INTO loan (member_id, loan_date, amount, due_date, total_due) "
              + "VALUES (:m, CURRENT_DATE, 10000, CURRENT_DATE + 60, 11000) RETURNING id")
            .setParameter("m", memberId)
            .getSingleResult()).longValue();
        em.flush();
    }

    @Test
    void repaymentBeyondBalanceIsRejected() {
        assertThatThrownBy(() -> repayUseCase.execute(loanId, TOTAL_DUE.add(BigDecimal.ONE), admin))
            .isInstanceOf(BusinessRuleException.class)
            .hasMessageContaining("dépasse le solde restant");
    }

    @Test
    void exactBalanceIsAcceptedAndClosesLoan() {
        assertThatCode(() -> repayUseCase.execute(loanId, TOTAL_DUE, admin))
            .doesNotThrowAnyException();
        em.flush();
        em.clear();

        String status = (String) em.createNativeQuery(
                "SELECT CAST(status AS text) FROM loan WHERE id = :id")
            .setParameter("id", loanId)
            .getSingleResult();
        assertThat(status).isEqualTo("repaid");
    }

    @Test
    void partialThenOverpayOfRemainderIsRejected() {
        repayUseCase.execute(loanId, new BigDecimal("6000.00"), admin);
        em.flush();
        // 5000 remains; paying 6000 more must be refused
        assertThatThrownBy(() -> repayUseCase.execute(loanId, new BigDecimal("6000.00"), admin))
            .isInstanceOf(BusinessRuleException.class)
            .hasMessageContaining("dépasse le solde restant");
    }
}
