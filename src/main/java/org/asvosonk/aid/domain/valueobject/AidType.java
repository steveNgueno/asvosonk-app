package org.asvosonk.aid.domain.valueobject;

import java.math.BigDecimal;

/**
 * Formes d'aides prévues par les statuts (Article 4), avec le montant
 * de référence. Le montant est proposé à l'enregistrement mais reste
 * modifiable : c'est la réunion qui décide du chiffre définitif.
 */
public enum AidType {
    deces_membre(340_000),   // décès d'un membre
    deces_conjoint(340_000), // décès du conjoint légitime ou déclaré
    deces_parent(200_000),   // décès d'un parent (père, mère…)
    deces_enfant(200_000),   // décès d'un enfant
    naissance(100_000),      // naissance
    mariage(250_000),        // mariage à Yaoundé
    autre(0);                // autre aide votée en réunion

    private final int defaultAmount;

    AidType(int defaultAmount) {
        this.defaultAmount = defaultAmount;
    }

    /** Montant proposé par les statuts ; 0 pour une aide libre. */
    public BigDecimal defaultAmount() {
        return BigDecimal.valueOf(defaultAmount);
    }

    public String label() {
        return switch (this) {
            case deces_membre   -> "Décès d'un membre";
            case deces_conjoint -> "Décès du conjoint";
            case deces_parent   -> "Décès d'un parent";
            case deces_enfant   -> "Décès d'un enfant";
            case naissance      -> "Naissance";
            case mariage        -> "Mariage";
            case autre          -> "Autre aide";
        };
    }
}
