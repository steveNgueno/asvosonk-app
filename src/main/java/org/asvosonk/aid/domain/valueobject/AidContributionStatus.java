package org.asvosonk.aid.domain.valueobject;

/**
 * Statut de la part d'un membre sur une aide :
 * owed : la part reste à recouvrir (totalement ou partiellement) ;
 * paid : la part est entièrement recouvrée.
 */
public enum AidContributionStatus {
    owed,
    paid;

    public String label() {
        return this == owed ? "À recouvrer" : "Recouvrée";
    }
}
