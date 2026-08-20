package org.asvosonk.session.presentation.request;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * Feuille de cotisation de la grande tontine : une ligne par participant, toutes
 * enregistrées d'un seul envoi.
 *
 * <p>Saisir cotisant par cotisant obligeait à autant d'allers-retours que de
 * membres. La feuille reprend le principe de la présence : on remplit le tableau,
 * on enregistre une fois. Une ligne laissée vide n'est pas enregistrée — elle
 * pourra l'être plus tard, tant que la tontine de la séance n'est pas clôturée ;
 * un <strong>0</strong> explicite, lui, enregistre un échec de cotisation.</p>
 */
@Getter
@Setter
public class TontineSheetForm {

    private Long tourId;
    private Long beneficiaryId;
    private List<Entry> entries = new ArrayList<>();

    @Getter
    @Setter
    public static class Entry {
        private Long contributorId;
        /** Montant versé ; {@code null} = ligne non saisie, ignorée. */
        private BigDecimal amount;
    }
}
