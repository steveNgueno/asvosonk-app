package org.asvosonk.cashbox;

import org.asvosonk.cashbox.application.service.CashboxService;
import org.asvosonk.cashbox.domain.repository.CashboxRepository;
import org.asvosonk.cashbox.domain.valueobject.CashboxType;
import org.asvosonk.cashbox.domain.valueobject.MovementDirection;
import org.asvosonk.cashbox.domain.valueobject.MovementOrigin;
import org.asvosonk.common.domain.exception.BusinessRuleException;
import org.asvosonk.support.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * Covers the Wave 2A cashbox guards on {@link CashboxService#record}:
 * <ul>
 *   <li><b>F-38</b> — a non-positive amount is an explicit {@link BusinessRuleException},
 *       never a silent no-op.</li>
 *   <li><b>F-04</b> — an {@code out} movement can never drive a balance below zero.</li>
 *   <li><b>F-35</b> — the returned movement carries the real post-update balance.</li>
 * </ul>
 *
 * <p>The service methods use {@code Propagation.MANDATORY}, so each call must run
 * inside the test's own transaction (provided by {@code @Transactional}).
 */
@SpringBootTest
@Transactional
class CashboxServiceGuardsIT extends AbstractIntegrationTest {

    @Autowired CashboxService   cashboxService;
    @Autowired CashboxRepository cashboxRepository;

    // ── F-38 : montant <= 0 → exception explicite ────────────────────

    @Test
    void zeroAmount_isRejected() {
        assertThatThrownBy(() -> cashboxService.record(
                CashboxType.development, MovementDirection.in, BigDecimal.ZERO,
                "test", MovementOrigin.manual, null, null, null, null))
            .isInstanceOf(BusinessRuleException.class);
    }

    @Test
    void negativeAmount_isRejected() {
        assertThatThrownBy(() -> cashboxService.record(
                CashboxType.development, MovementDirection.in, new BigDecimal("-100"),
                "test", MovementOrigin.manual, null, null, null, null))
            .isInstanceOf(BusinessRuleException.class);
    }

    // ── F-04 : sortie qui rendrait le solde négatif → refus ──────────

    @Test
    void withdrawalBelowZero_isRejected() {
        // Seed leaves every cashbox at 0; any 'out' would go negative.
        assertThatThrownBy(() -> cashboxService.record(
                CashboxType.beverage, MovementDirection.out, new BigDecimal("500"),
                "retrait", MovementOrigin.manual, null, null, null, null))
            .isInstanceOf(BusinessRuleException.class);
    }

    // ── F-35 : le solde retourné est le solde réel après update ──────

    @Test
    void creditThenDebit_returnsRealBalance() {
        var in = cashboxService.record(
                CashboxType.sanction, MovementDirection.in, new BigDecimal("1000"),
                "entrée", MovementOrigin.manual, null, null, null, null);
        assertThat(in).isNotNull();
        assertThat(in.getCashbox().getBalance()).isEqualByComparingTo("1000");

        var out = cashboxService.record(
                CashboxType.sanction, MovementDirection.out, new BigDecimal("400"),
                "sortie", MovementOrigin.manual, null, null, null, null);
        assertThat(out.getCashbox().getBalance()).isEqualByComparingTo("600");

        // And the persisted balance agrees.
        assertThat(cashboxRepository.findByType(CashboxType.sanction).orElseThrow().getBalance())
            .isEqualByComparingTo("600");
    }

    @Test
    void exactWithdrawalToZero_isAllowed() {
        cashboxService.record(CashboxType.bank, MovementDirection.in, new BigDecimal("2000"),
                "dotation", MovementOrigin.manual, null, null, null, null);
        assertThatCode(() -> cashboxService.record(
                CashboxType.bank, MovementDirection.out, new BigDecimal("2000"),
                "retrait total", MovementOrigin.manual, null, null, null, null))
            .doesNotThrowAnyException();
    }
}
