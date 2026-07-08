package org.asvosonk.presence.presentation.request;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDate;
import java.util.List;

@Getter @Setter
public class CreatePresenceTourForm {

    @NotNull(message = "La date de début est obligatoire.")
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate startDate;

    @NotEmpty(message = "Sélectionnez au moins 2 participants.")
    private List<Long> participantIds;

    @NotEmpty(message = "L'ordre de tirage est obligatoire.")
    private List<Integer> drawOrders;
}
