package org.asvosonk.session.presentation.response;

import lombok.*;
import org.asvosonk.member.domain.model.Member;
import org.asvosonk.session.infrastructure.persistence.entity.MeetingSessionEntity;
import org.asvosonk.session.application.service.RevolvingFundService;

import java.math.BigDecimal;
import java.util.List;

/**
 * Carries all computed data after a session is closed.
 * Used to render the session report page and to drive the PDF generator.
 */
@Getter @Setter
@Builder
public class SessionCloseResult {

    private MeetingSessionEntity session;
    private Member         beneficiary;

    // Attendance stats
    private int totalCotisants;
    private int presentCount;
    private int fundCoveredCount;
    private int defaultCount;

    // Tontine
    private BigDecimal grossTontine;       // sum of 1 000 FCFA × cotisants
    private BigDecimal sanctionDeductions; // unpaid sanctions deducted from beneficiary
    private BigDecimal netTontine;         // what the beneficiary actually receives

    // Cashbox movements this session
    private BigDecimal totalDevelopment;
    private BigDecimal totalBeveragePool;
    private BigDecimal actualBeverageCost;
    private BigDecimal beverageReliquat;

    // Per-member results (for the detailed table in the report)
    private List<RevolvingFundService.AttendanceResult> attendanceResults;

    // ── Computed helpers ─────────────────────────────────────

    /** Physical cash to hand to the treasurer (tontine + development; beverage stays in cashbox) */
    public BigDecimal getNetAmountToTreasurer() {
        return netTontine.add(totalDevelopment);
    }

    public int getUpToDateCount() {
        return totalCotisants - fundCoveredCount - defaultCount;
    }
}
