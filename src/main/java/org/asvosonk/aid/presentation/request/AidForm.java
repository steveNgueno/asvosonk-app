package org.asvosonk.aid.presentation.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;
import org.springframework.format.annotation.DateTimeFormat;

import java.math.BigDecimal;
import java.time.LocalDate;

@Getter @Setter
public class AidForm {

    @NotNull(message = "Le membre concerné est obligatoire.")
    private Long beneficiaryId;

    private String type; // AidType en minuscules, validé au mapping

    @NotNull(message = "La date de l'aide est obligatoire.")
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate aidDate;

    @NotNull(message = "La somme remise au membre est obligatoire.")
    @DecimalMin(value = "1", message = "La somme remise doit être supérieure à zéro.")
    private BigDecimal totalAmount;

    @DecimalMin(value = "0", message = "La part par membre doit être positive ou nulle.")
    private BigDecimal sharePerMember;

    private String description;
}
