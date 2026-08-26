package org.asvosonk.aid.domain.valueobject;

/**
 * Comment une part a été recouverte :
 * direct            : le membre a versé sa part lui-même en séance ;
 * retained_presence : retenue sur sa tontine de présence ;
 * retained_tontine  : retenue sur sa grande tontine.
 *
 * Comme pour les sanctions, l'aide est coupée obligatoirement sur le
 * bénéfice du membre lorsqu'il en perçoit un.
 */
public enum AidPaymentMode {
    direct,
    retained_presence,
    retained_tontine;

    public String label() {
        return switch (this) {
            case direct            -> "Versement direct";
            case retained_presence -> "Retenue tontine de présence";
            case retained_tontine  -> "Retenue grande tontine";
        };
    }
}
