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
    private final LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public Loan(Long id, Long memberId, LocalDate loanDate, BigDecimal amount,
                BigDecimal interestRate, int durationMonths, LocalDate dueDate,
                BigDecimal totalDue, LoanStatus status,
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
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    // ── Factory ─────────────────────────────────────────────

    public static Loan createNew(Long memberId, BigDecimal amount) {
        LocalDate now = LocalDate.now();
        BigDecimal interestRate = DEFAULT_INTEREST_RATE;
        int durationMonths = DEFAULT_DURATION_MONTHS;
        BigDecimal totalDue = amount.add(
            amount.multiply(interestRate).divide(new BigDecimal("100"), RoundingMode.HALF_UP));
        LocalDate dueDate = now.plusMonths(durationMonths);
        return new Loan(null, memberId, now, amount, interestRate, durationMonths,
            dueDate, totalDue, LoanStatus.active, LocalDateTime.now(), LocalDateTime.now());
    }

    // ── Business methods ────────────────────────────────────

    public boolean isActive() { return status == LoanStatus.active; }

    public boolean isOverdue() {
        return status == LoanStatus.active && LocalDate.now().isAfter(dueDate);
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
