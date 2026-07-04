package org.asvosonk.bank.presentation.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

@Getter @Setter
public class LoanForm {

    @NotNull(message = "Le montant est obligatoire.")
    @DecimalMin(value = "1", message = "Le montant minimum est de 1 FCFA.")
    private BigDecimal amount;
}
