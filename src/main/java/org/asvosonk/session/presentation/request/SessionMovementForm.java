package org.asvosonk.session.presentation.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.asvosonk.cashbox.domain.valueobject.CashboxType;
import org.asvosonk.cashbox.domain.valueobject.MovementDirection;

import java.math.BigDecimal;

/**
 * Entrée ou sortie de caisse saisie pendant une séance.
 *
 * <p>Les entrées du jour ne se limitent pas aux cotisations : un don, un
 * remboursement ponctuel ou une dépense décidée en séance en font partie et
 * doivent apparaître dans le rapport. Le motif est obligatoire dans les deux
 * sens — c'est lui qui justifie le mouvement dans le cahier de caisse.
 */
@Getter @Setter
@NoArgsConstructor
public class SessionMovementForm {

    @NotNull(message = "La caisse est obligatoire.")
    private CashboxType cashbox;

    @NotNull(message = "Le sens du mouvement est obligatoire.")
    private MovementDirection direction;

    @NotNull(message = "Le montant est obligatoire.")
    @DecimalMin(value = "1", message = "Le montant doit être supérieur à 0.")
    private BigDecimal amount;

    @NotBlank(message = "Le motif est obligatoire.")
    private String reason;
}
