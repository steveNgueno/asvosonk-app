package org.asvosonk.sanction.domain.valueobject;

public enum SanctionOrigin {
    presence_fund,      // cotisé par le fond (200 FCFA)
    presence_default,   // échec présence (500 FCFA)
    tontine_default,    // échec grande tontine (2000/5000 FCFA)
    manual              // disciplinaire (censeur, montant libre)
}
