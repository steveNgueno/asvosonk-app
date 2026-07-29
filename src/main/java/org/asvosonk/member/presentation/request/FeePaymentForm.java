package org.asvosonk.member.presentation.request;

import jakarta.validation.constraints.*;
import lombok.*;
import org.asvosonk.member.domain.valueobject.FeeType;

import java.math.BigDecimal;

@Getter @Setter
@NoArgsConstructor
public class FeePaymentForm {

    // Renseigné côté serveur à partir du {id} de l'URL (jamais soumis par le
    // formulaire) : ne pas le valider, sous peine de rejeter tout paiement.
    private Long memberId;

    @NotNull(message = "Le type de frais est obligatoire")
    private FeeType feeType;

    @NotNull(message = "Le montant est obligatoire")
    @DecimalMin(value = "1", message = "Le montant doit être supérieur à 0")
    private BigDecimal amount;
}
