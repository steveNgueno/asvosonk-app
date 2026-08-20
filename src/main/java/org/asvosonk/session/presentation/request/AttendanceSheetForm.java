package org.asvosonk.session.presentation.request;

import jakarta.validation.Valid;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

/**
 * The whole attendance sheet of a session, as submitted by the presence screen.
 *
 * <p>Each row carries the member, whether they attended and the amount they
 * paid. The screen used to post one field at a time through two separate forms
 * per row, which meant the "présent" checkbox was never actually persisted (the
 * amount form did not carry it, so every save reset it to false). Submitting the
 * sheet as a single object keeps the two values of a row together.
 */
@Getter @Setter
@NoArgsConstructor
public class AttendanceSheetForm {

    @Valid
    private List<AttendanceEntryForm> entries = new ArrayList<>();
}
