package org.asvosonk.cashbox.presentation.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Getter @Setter
@NoArgsConstructor
public class ManualMovementForm {

    @NotBlank(message = "Le motif est obligatoire")
    private String reason;

    @NotNull(message = "Le montant est obligatoire")
    @DecimalMin(value = "1", message = "Le montant doit être supérieur à 0")
    private BigDecimal amount;
}
