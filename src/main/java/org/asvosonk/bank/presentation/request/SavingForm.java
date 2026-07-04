package org.asvosonk.bank.presentation.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;
import org.springframework.format.annotation.DateTimeFormat;

import java.math.BigDecimal;
import java.time.LocalDate;

@Getter @Setter
public class SavingForm {

    @NotNull(message = "Le montant est obligatoire.")
    @DecimalMin(value = "1", message = "Le montant minimum est de 1 FCFA.")
    private BigDecimal amount;

    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate operationDate;
}
