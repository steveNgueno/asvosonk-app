package org.asvosonk.common.presentation.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import jakarta.validation.ReportAsSingleViolation;
import jakarta.validation.constraints.Digits;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Montant saisi en FCFA : un nombre entier (le franc CFA n'a pas de centimes en
 * usage) d'au plus {@value FcfaAmounts#MAX_DIGITS} chiffres.
 *
 * <p>Sans cette règle, 1 500,50 était accepté puis affiché arrondi, si bien que
 * les rapports ne correspondaient plus aux montants en base ; et un montant trop
 * grand dépassait la capacité des colonnes ({@code NUMERIC(10,2)}) et finissait
 * en erreur 500. {@code null} est accepté : l'obligation se déclare à part
 * ({@code @NotNull}).</p>
 */
@Documented
@Constraint(validatedBy = {})
@Digits(integer = FcfaAmounts.MAX_DIGITS, fraction = 0)
@ReportAsSingleViolation
@Target({ElementType.FIELD, ElementType.PARAMETER, ElementType.TYPE_USE})
@Retention(RetentionPolicy.RUNTIME)
public @interface FcfaAmount {

    String message() default FcfaAmounts.MESSAGE;

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
