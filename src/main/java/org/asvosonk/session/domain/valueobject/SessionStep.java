package org.asvosonk.session.domain.valueobject;

/**
 * Déroulé d'une séance, dans l'ordre de l'ordre du jour de l'association.
 *
 * <p>Chaque rubrique financière est une étape « ouverte » (saisie en cours) puis
 * « clôturée » (calculs figés) :
 * <ol>
 *   <li>ordre du jour ;</li>
 *   <li>présence (obligatoire pour tous les membres) ;</li>
 *   <li>grande tontine (membres souscripteurs du tour en cours) ;</li>
 *   <li>banque projet ;</li>
 *   <li>banque annuelle ;</li>
 *   <li>rapport de séance.</li>
 * </ol>
 *
 * <p>La Banque Annuelle se saisit en séance : épargnes, emprunts et
 * remboursements enregistrés à l'étape {@link #BANQUE_ANNUELLE_OPEN} sont
 * rattachés à la séance, et sa clôture fige leurs totaux dans le rapport. La
 * Banque Projet, elle, est encore traversée sans rien enregistrer.
 */
public enum SessionStep {
    CREATED,
    PRESENCE_OPEN,
    PRESENCE_CLOSED,
    TONTINE_OPEN,
    TONTINE_CLOSED,
    BANQUE_PROJET_OPEN,
    BANQUE_PROJET_CLOSED,
    BANQUE_ANNUELLE_OPEN,
    BANQUE_ANNUELLE_CLOSED,
    REPORT_GENERATED;

    /**
     * Étape suivante du déroulé, ou null si la séance est déjà au rapport.
     */
    public SessionStep next() {
        SessionStep[] steps = values();
        int idx = this.ordinal();
        return idx < steps.length - 1 ? steps[idx + 1] : null;
    }

    /**
     * Étape précédente, ou null si la séance vient d'être créée.
     */
    public SessionStep previous() {
        int idx = this.ordinal();
        return idx > 0 ? values()[idx - 1] : null;
    }

    /** Étape de saisie (par opposition à une étape déjà clôturée). */
    public boolean isOpenStep() {
        return this == PRESENCE_OPEN || this == TONTINE_OPEN
            || this == BANQUE_PROJET_OPEN || this == BANQUE_ANNUELLE_OPEN;
    }

    /** Vrai dès que la présence est clôturée (traitements financiers effectués). */
    public boolean isAfterPresence() {
        return this.ordinal() >= PRESENCE_CLOSED.ordinal();
    }

    /** Libellé complet de l'étape. */
    public String label() {
        return switch (this) {
            case CREATED                -> "Ordre du jour";
            case PRESENCE_OPEN          -> "Présence — saisie";
            case PRESENCE_CLOSED        -> "Présence clôturée";
            case TONTINE_OPEN           -> "Grande Tontine — saisie";
            case TONTINE_CLOSED         -> "Grande Tontine clôturée";
            case BANQUE_PROJET_OPEN     -> "Banque Projet — saisie";
            case BANQUE_PROJET_CLOSED   -> "Banque Projet clôturée";
            case BANQUE_ANNUELLE_OPEN   -> "Banque Annuelle — saisie";
            case BANQUE_ANNUELLE_CLOSED -> "Banque Annuelle clôturée";
            case REPORT_GENERATED       -> "Rapport généré";
        };
    }

    /** Libellé court, pour les listes. */
    public String shortLabel() {
        return switch (this) {
            case CREATED                -> "Ordre du jour";
            case PRESENCE_OPEN          -> "Présence";
            case PRESENCE_CLOSED        -> "Présence ✓";
            case TONTINE_OPEN           -> "Grande Tontine";
            case TONTINE_CLOSED         -> "Grande Tontine ✓";
            case BANQUE_PROJET_OPEN     -> "Banque Projet";
            case BANQUE_PROJET_CLOSED   -> "Banque Projet ✓";
            case BANQUE_ANNUELLE_OPEN   -> "Banque Annuelle";
            case BANQUE_ANNUELLE_CLOSED -> "Banque Annuelle ✓";
            case REPORT_GENERATED       -> "Rapport";
        };
    }

    /** Icône Bootstrap Icons associée. */
    public String icon() {
        return switch (this) {
            case CREATED                              -> "bi-calendar-check";
            case PRESENCE_OPEN, PRESENCE_CLOSED       -> "bi-clipboard-check";
            case TONTINE_OPEN, TONTINE_CLOSED         -> "bi-cash-stack";
            case BANQUE_PROJET_OPEN,
                 BANQUE_PROJET_CLOSED                 -> "bi-briefcase";
            case BANQUE_ANNUELLE_OPEN,
                 BANQUE_ANNUELLE_CLOSED               -> "bi-bank";
            case REPORT_GENERATED                     -> "bi-file-earmark-bar-graph";
        };
    }

    /** Étapes affichées dans la barre de progression (paires ouverte/clôturée fusionnées). */
    public static DisplayStep[] displaySteps() {
        return DisplayStep.values();
    }

    /** Index de l'étape courante dans la barre de progression. */
    public int displayIndex() {
        return switch (this) {
            case CREATED                                  -> 0;
            case PRESENCE_OPEN, PRESENCE_CLOSED           -> 1;
            case TONTINE_OPEN, TONTINE_CLOSED             -> 2;
            case BANQUE_PROJET_OPEN, BANQUE_PROJET_CLOSED -> 3;
            case BANQUE_ANNUELLE_OPEN,
                 BANQUE_ANNUELLE_CLOSED                   -> 4;
            case REPORT_GENERATED                         -> 5;
        };
    }

    /** Étapes simplifiées pour la barre de progression. */
    public enum DisplayStep {
        AGENDA("Ordre du jour",     "bi-calendar-check"),
        PRESENCE("Présence",        "bi-clipboard-check"),
        TONTINE("Grande Tontine",   "bi-cash-stack"),
        BANQUE_PROJET("Banque Projet",     "bi-briefcase"),
        BANQUE_ANNUELLE("Banque Annuelle", "bi-bank"),
        REPORT("Rapport",           "bi-file-earmark-bar-graph");

        private final String label;
        private final String icon;

        DisplayStep(String label, String icon) {
            this.label = label;
            this.icon = icon;
        }

        public String label() { return label; }
        public String icon() { return icon; }
    }
}
