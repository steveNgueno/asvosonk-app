package org.asvosonk.session.presentation.request;

import jakarta.validation.constraints.*;
import lombok.*;

import java.time.LocalDate;

@Getter @Setter
@NoArgsConstructor
public class SessionForm {

    @NotNull(message = "La date de séance est obligatoire")
    private LocalDate sessionDate;

    private String agenda;
}
