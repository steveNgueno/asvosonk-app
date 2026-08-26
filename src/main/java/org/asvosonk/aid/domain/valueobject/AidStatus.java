package org.asvosonk.aid.domain.valueobject;

/**
 * in_progress : l'aide est d'actualité — au moins une part reste à recouvrir.
 * completed   : tous les membres ont recouvert leur part ; l'aide n'est
 *               plus d'actualité et n'est plus proposée aux recouvrements.
 */
public enum AidStatus {
    in_progress,
    completed;

    public boolean isCurrent() { return this == in_progress; }

    public String label() {
        return this == in_progress ? "En cours" : "Recouvrée";
    }
}
