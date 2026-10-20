package org.asvosonk.common.presentation.validation;

import org.asvosonk.common.domain.exception.BusinessRuleException;

import java.math.BigDecimal;

/**
 * Contrôle des montants en FCFA reçus hors formulaire validé (paramètres de
 * requête, lignes de feuille) : même règle que {@link FcfaAmount}.
 */
public final class FcfaAmounts {

    /** 99 999 999 FCFA au plus : tient dans toutes les colonnes de montant. */
    public static final int MAX_DIGITS = 8;

    public static final String MESSAGE =
        "Le montant doit être un nombre entier de FCFA, sans centimes, d'au plus 99 999 999.";

    private FcfaAmounts() {
    }

    public static boolean isValid(BigDecimal amount) {
        if (amount == null) {
            return true;
        }
        BigDecimal normalized = amount.stripTrailingZeros();
        return normalized.scale() <= 0
            && normalized.precision() - normalized.scale() <= MAX_DIGITS;
    }

    /** @throws BusinessRuleException si le montant n'est pas un entier admissible */
    public static void requireValid(BigDecimal amount) {
        if (!isValid(amount)) {
            throw new BusinessRuleException(MESSAGE);
        }
    }
}
