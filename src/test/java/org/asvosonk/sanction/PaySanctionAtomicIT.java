package org.asvosonk.sanction;

import org.asvosonk.cashbox.domain.repository.CashboxRepository;
import org.asvosonk.cashbox.domain.valueobject.CashboxType;
import org.asvosonk.common.domain.exception.BusinessRuleException;
import org.asvosonk.sanction.application.usecase.CreateSanctionUseCase;
import org.asvosonk.sanction.application.usecase.PaySanctionUseCase;
import org.asvosonk.sanction.domain.model.Sanction;
import org.asvosonk.sanction.domain.repository.SanctionRepository;
import org.asvosonk.sanction.domain.valueobject.SanctionOrigin;
import org.asvosonk.sanction.domain.valueobject.SanctionStatus;
import org.asvosonk.support.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * F-05: paying a sanction must change its status AND credit the sanction
 * cashbox within the SAME transaction. The seed provides member id 1
 * ("Administrateur") and a sanction cashbox at balance 0.
 */
@SpringBootTest
@Transactional
class PaySanctionAtomicIT extends AbstractIntegrationTest {

    private static final Long SEED_MEMBER_ID = 1L;

    @Autowired CreateSanctionUseCase createSanction;
    @Autowired PaySanctionUseCase    paySanction;
    @Autowired SanctionRepository    sanctionRepository;
    @Autowired CashboxRepository     cashboxRepository;

    @Test
    void payingCreditsCashboxAndMarksPaid_atomically() {
        BigDecimal before = cashboxRepository.findByType(CashboxType.sanction)
            .orElseThrow().getBalance();

        Sanction s = createSanction.execute(SEED_MEMBER_ID, LocalDate.now(),
            new BigDecimal("3000"), "retard", SanctionOrigin.manual, null);

        Sanction paid = paySanction.execute(s.getId(), null);

        assertThat(paid.getStatus()).isEqualTo(SanctionStatus.paid);
        BigDecimal after = cashboxRepository.findByType(CashboxType.sanction)
            .orElseThrow().getBalance();
        assertThat(after.subtract(before)).isEqualByComparingTo("3000");
    }

    @Test
    void payingAnAlreadyPaidSanction_isRejected_andCashboxUnchanged() {
        Sanction s = createSanction.execute(SEED_MEMBER_ID, LocalDate.now(),
            new BigDecimal("1000"), "absence", SanctionOrigin.manual, null);
        paySanction.execute(s.getId(), null);

        BigDecimal balanceAfterFirst = cashboxRepository.findByType(CashboxType.sanction)
            .orElseThrow().getBalance();

        assertThatThrownBy(() -> paySanction.execute(s.getId(), null))
            .isInstanceOf(BusinessRuleException.class);

        assertThat(cashboxRepository.findByType(CashboxType.sanction).orElseThrow().getBalance())
            .isEqualByComparingTo(balanceAfterFirst);
    }
}
