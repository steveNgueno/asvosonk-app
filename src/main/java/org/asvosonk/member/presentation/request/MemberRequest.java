package org.asvosonk.member.presentation.request;

import jakarta.validation.constraints.*;
import lombok.*;
import org.asvosonk.member.domain.valueobject.MemberStatus;

import java.time.LocalDate;

@Getter @Setter
@NoArgsConstructor
public class MemberRequest {

    @NotBlank(message = "Le nom complet est obligatoire")
    @Size(max = 150, message = "Le nom ne doit pas dépasser 150 caractères")
    private String fullName;

    @Size(max = 30, message = "Le téléphone ne doit pas dépasser 30 caractères")
    private String phone;

    @NotNull(message = "La date d'adhésion est obligatoire")
    @PastOrPresent(message = "La date d'adhésion ne peut pas être dans le futur")
    private LocalDate joinDate;

    private boolean resident = true;

    private MemberStatus status = MemberStatus.active;
}
