package org.asvosonk.presence.presentation.request;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDate;

/**
 * Ouverture d'un tour de présence.
 *
 * <p>Aucun participant à saisir : la cotisation de présence étant obligatoire
 * pour tous, le tour regroupe automatiquement tous les membres actifs. Il n'y a
 * pas non plus d'ordre de passage — le bénéficiaire est tiré au sort à chaque
 * séance parmi ceux qui n'ont pas encore bénéficié.</p>
 */
@Getter @Setter
public class CreatePresenceTourForm {

    @NotNull(message = "La date de début est obligatoire.")
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate startDate = LocalDate.now();
}
