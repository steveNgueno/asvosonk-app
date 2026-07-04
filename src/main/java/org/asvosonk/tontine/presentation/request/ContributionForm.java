package org.asvosonk.tontine.presentation.request;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

@Getter @Setter
public class ContributionForm {

    @NotNull(message = "Le cotisant est obligatoire.")
    private Long contributorId;

    @NotNull(message = "Le bénéficiaire est obligatoire.")
    private Long beneficiaryId;

    @NotNull(message = "Le montant est obligatoire (0 pour échec).")
    private BigDecimal amount;

    private Long sessionId;
}
