package org.asvosonk.report.application.service;

/**
 * Le générateur de rapports (exécutable Python) est absent de ce serveur.
 *
 * <p>Situation d'installation, pas une panne : le message est destiné à
 * l'utilisateur, le chemin attendu n'est consigné que dans les journaux.</p>
 */
public class ReportModuleUnavailableException extends RuntimeException {

    public ReportModuleUnavailableException() {
        super("Le module de génération des rapports n'est pas installé sur ce serveur. "
            + "Contactez l'administrateur.");
    }
}
