package org.asvosonk.aid.presentation.request;

import org.asvosonk.common.presentation.validation.FcfaAmount;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
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
    @PastOrPresent(message = "La date de l'aide ne peut pas être dans le futur.")
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate aidDate;

    @NotNull(message = "La somme remise au membre est obligatoire.")
    @DecimalMin(value = "1", message = "La somme remise doit être supérieure à zéro.")
    @FcfaAmount
    private BigDecimal totalAmount;

    @DecimalMin(value = "0", message = "La part par membre doit être positive ou nulle.")
    @FcfaAmount
    private BigDecimal sharePerMember;

    private String description;
}
