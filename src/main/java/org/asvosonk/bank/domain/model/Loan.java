package org.asvosonk.bank.domain.model;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import org.asvosonk.bank.domain.valueobject.LoanStatus;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Getter
@EqualsAndHashCode(of = "id")
public class Loan {

    private static final BigDecimal DEFAULT_INTEREST_RATE = new BigDecimal("10.00");
    private static final int DEFAULT_DURATION_MONTHS = 2;

    private final Long id;
    private final Long memberId;
    private final LocalDate loanDate;
    private final BigDecimal amount;
    private final BigDecimal interestRate;
    private final int durationMonths;
    private final LocalDate dueDate;
    private final BigDecimal totalDue;
    private LoanStatus status;
    /** Séance d'octroi, ou null pour un emprunt accordé hors séance. */
    private final Long sessionId;
    private final LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public Loan(Long id, Long memberId, LocalDate loanDate, BigDecimal amount,
                BigDecimal interestRate, int durationMonths, LocalDate dueDate,
                BigDecimal totalDue, LoanStatus status, Long sessionId,
                LocalDateTime createdAt, LocalDateTime updatedAt) {
        this.id = id;
        this.memberId = memberId;
        this.loanDate = loanDate;
        this.amount = amount;
        this.interestRate = interestRate;
        this.durationMonths = durationMonths;
        this.dueDate = dueDate;
        this.totalDue = totalDue;
        this.status = status;
        this.sessionId = sessionId;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    // ── Factory ─────────────────────────────────────────────

    public static Loan createNew(Long memberId, BigDecimal amount, Long sessionId) {
        LocalDate now = LocalDate.now();
        BigDecimal interestRate = DEFAULT_INTEREST_RATE;
        int durationMonths = DEFAULT_DURATION_MONTHS;
        // F-51 — round the interest to 2 decimals (FCFA has no sub-unit, but the
        // rest of the codebase represents money at scale 2). Without an explicit
        // scale, divide() keeps the dividend's scale (amount.scale() + interestRate.scale()),
        // e.g. 4 — so totalDue silently drifted to a 4-decimal amount never settled to zero.
        BigDecimal interest = amount.multiply(interestRate)
            .divide(new BigDecimal("100"), 2, RoundingMode.HALF_UP);
        BigDecimal totalDue = amount.add(interest);
        LocalDate dueDate = now.plusMonths(durationMonths);
        return new Loan(null, memberId, now, amount, interestRate, durationMonths,
            dueDate, totalDue, LoanStatus.active, sessionId,
            LocalDateTime.now(), LocalDateTime.now());
    }

    // ── Business methods ────────────────────────────────────

    public boolean isActive() { return status == LoanStatus.active; }

    public boolean isRepaid() { return status == LoanStatus.repaid; }

    /** A loan still owes money while it is not fully repaid (active or overdue). */
    public boolean isOutstanding() { return status != LoanStatus.repaid; }

    public boolean isOverdue() {
        return status == LoanStatus.overdue
            || (status == LoanStatus.active && LocalDate.now().isAfter(dueDate));
    }

    public BigDecimal getRemainingBalance(BigDecimal totalRepaid) {
        return totalDue.subtract(totalRepaid).max(BigDecimal.ZERO);
    }

    public void markAsOverdue() {
        this.status = LoanStatus.overdue;
        this.updatedAt = LocalDateTime.now();
    }

    public void markAsRepaid() {
        this.status = LoanStatus.repaid;
        this.updatedAt = LocalDateTime.now();
    }

    public String getStatusLabel() {
        return switch (status) {
            case active  -> "Actif";
            case repaid  -> "Remboursé";
            case overdue -> "En retard";
        };
    }
}
