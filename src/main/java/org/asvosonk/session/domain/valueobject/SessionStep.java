package org.asvosonk.session.domain.valueobject;

/**
 * Defines the sequential workflow steps of a meeting session.
 * Each step must be completed before advancing to the next.
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
     * Returns the next step in the workflow, or null if already at REPORT_GENERATED.
     */
    public SessionStep next() {
        SessionStep[] steps = values();
        int idx = this.ordinal();
        return idx < steps.length - 1 ? steps[idx + 1] : null;
    }

    /**
     * Returns the previous step in the workflow, or null if already at CREATED.
     */
    public SessionStep previous() {
        int idx = this.ordinal();
        return idx > 0 ? values()[idx - 1] : null;
    }

    /**
     * Whether this step is a financial "open" step that requires input.
     */
    public boolean isOpenStep() {
        return this == PRESENCE_OPEN || this == TONTINE_OPEN
            || this == BANQUE_PROJET_OPEN || this == BANQUE_ANNUELLE_OPEN;
    }

    /**
     * Whether this step is after PRESENCE_OPEN (i.e. financial processing started).
     */
    public boolean isAfterPresence() {
        return this.ordinal() >= PRESENCE_CLOSED.ordinal();
    }

    /**
     * Returns the French label for this step.
     */
    public String label() {
        return switch (this) {
            case CREATED            -> "Créée";
            case PRESENCE_OPEN      -> "Présence";
            case PRESENCE_CLOSED    -> "Présence ✓";
            case TONTINE_OPEN       -> "Grande Tontine";
            case TONTINE_CLOSED     -> "Grande Tontine ✓";
            case BANQUE_PROJET_OPEN -> "Banque Projet";
            case BANQUE_PROJET_CLOSED -> "Banque Projet ✓";
            case BANQUE_ANNUELLE_OPEN -> "Banque Annuelle";
            case BANQUE_ANNUELLE_CLOSED -> "Banque Annuelle ✓";
            case REPORT_GENERATED   -> "Rapport";
        };
    }

    /**
     * Short label for stepper display.
     */
    public String shortLabel() {
        return switch (this) {
            case CREATED            -> "Ordre du jour";
            case PRESENCE_OPEN      -> "Présence";
            case PRESENCE_CLOSED    -> "Présence ✓";
            case TONTINE_OPEN       -> "Grande Tontine";
            case TONTINE_CLOSED     -> "Grande Tontine ✓";
            case BANQUE_PROJET_OPEN -> "Banque Projet";
            case BANQUE_PROJET_CLOSED -> "Banque Projet ✓";
            case BANQUE_ANNUELLE_OPEN -> "Banque Annuelle";
            case BANQUE_ANNUELLE_CLOSED -> "Banque Annuelle ✓";
            case REPORT_GENERATED   -> "Rapport";
        };
    }

    /**
     * Icon name for stepper display (Bootstrap Icons).
     */
    public String icon() {
        return switch (this) {
            case CREATED            -> "bi-calendar-check";
            case PRESENCE_OPEN      -> "bi-clipboard-check";
            case PRESENCE_CLOSED    -> "bi-clipboard-check";
            case TONTINE_OPEN       -> "bi-cash-stack";
            case TONTINE_CLOSED     -> "bi-cash-stack";
            case BANQUE_PROJET_OPEN -> "bi-building";
            case BANQUE_PROJET_CLOSED -> "bi-building";
            case BANQUE_ANNUELLE_OPEN -> "bi-bank";
            case BANQUE_ANNUELLE_CLOSED -> "bi-bank";
            case REPORT_GENERATED   -> "bi-file-earmark-bar-graph";
        };
    }

    /**
     * Returns the six display steps for the stepper (collapsing open/closed pairs).
     */
    public static DisplayStep[] displaySteps() {
        return DisplayStep.values();
    }

    /**
     * Simplified display steps for the stepper UI.
     */
    public enum DisplayStep {
        AGENDA("Ordre du jour", "bi-calendar-check"),
        PRESENCE("Présence", "bi-clipboard-check"),
        TONTINE("Grande Tontine", "bi-cash-stack"),
        BANQUE_PROJET("Banque Projet", "bi-building"),
        BANQUE_ANNUELLE("Banque Annuelle", "bi-bank"),
        REPORT("Rapport", "bi-file-earmark-bar-graph");

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
