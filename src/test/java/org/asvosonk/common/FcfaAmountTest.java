package org.asvosonk.common;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.asvosonk.common.domain.exception.BusinessRuleException;
import org.asvosonk.common.presentation.validation.FcfaAmounts;
import org.asvosonk.member.presentation.request.FeePaymentForm;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Montants en FCFA : entiers, 99 999 999 au plus. 1 500,50 était accepté puis
 * affiché arrondi (rapports faux), et un montant trop grand finissait en 500.
 */
class FcfaAmountTest {

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    void seulsLesEntiersDeHuitChiffresAuPlusSontAdmis() {
        assertThat(FcfaAmounts.isValid(new BigDecimal("5000"))).isTrue();
        assertThat(FcfaAmounts.isValid(new BigDecimal("5000.00"))).isTrue();
        assertThat(FcfaAmounts.isValid(new BigDecimal("99999999"))).isTrue();
        assertThat(FcfaAmounts.isValid(BigDecimal.ZERO)).isTrue();
        assertThat(FcfaAmounts.isValid(null)).isTrue();

        assertThat(FcfaAmounts.isValid(new BigDecimal("1500.50"))).isFalse();
        assertThat(FcfaAmounts.isValid(new BigDecimal("100000000"))).isFalse();
        assertThatThrownBy(() -> FcfaAmounts.requireValid(new BigDecimal("0.5")))
            .isInstanceOf(BusinessRuleException.class);
    }

    @Test
    void lesFormulairesRefusentLesCentimes() {
        FeePaymentForm form = new FeePaymentForm();
        form.setAmount(new BigDecimal("1500.50"));
        assertThat(validator.validateProperty(form, "amount")).isNotEmpty();

        form.setAmount(new BigDecimal("1000000000"));
        assertThat(validator.validateProperty(form, "amount")).isNotEmpty();

        form.setAmount(new BigDecimal("2500"));
        assertThat(validator.validateProperty(form, "amount")).isEmpty();
    }
}
