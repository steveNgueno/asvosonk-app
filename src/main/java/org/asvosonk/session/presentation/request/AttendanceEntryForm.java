package org.asvosonk.session.presentation.request;

import jakarta.validation.constraints.*;
import lombok.*;

import java.math.BigDecimal;

@Getter @Setter
@NoArgsConstructor
public class AttendanceEntryForm {

    @NotNull
    private Long memberId;

    private boolean present;

    @DecimalMin(value = "0", message = "Le montant ne peut pas être négatif")
    private BigDecimal amountPaid = BigDecimal.ZERO;
}
