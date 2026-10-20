package org.asvosonk.session.presentation.request;

import org.asvosonk.common.presentation.validation.FcfaAmount;

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
    @FcfaAmount
    private BigDecimal amountPaid = BigDecimal.ZERO;
}
