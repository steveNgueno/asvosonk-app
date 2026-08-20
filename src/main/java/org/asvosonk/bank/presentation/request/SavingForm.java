package org.asvosonk.bank.presentation.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
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

    @PastOrPresent(message = "La date de l'opération ne peut pas être dans le futur.")
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate operationDate;
}
