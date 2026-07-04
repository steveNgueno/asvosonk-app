package org.asvosonk.bank.application.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.asvosonk.bank.application.usecase.CheckOverdueLoansUseCase;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Scheduled task that checks for and marks overdue loans.
 * Runs daily at 8:00 AM.
 * The actual overdue detection logic is delegated to CheckOverdueLoansUseCase
 * (which also runs independently at 02:00 AM).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class LoanOverdueScheduler {

    private final CheckOverdueLoansUseCase checkOverdueLoansUseCase;

    /**
     * Runs every morning at 8:00 to detect and mark overdue loans.
     */
    @Scheduled(cron = "0 0 8 * * ?")
    @Transactional
    public void markOverdueLoans() {
        log.info("Running scheduled overdue loan check (08:00)...");
        checkOverdueLoansUseCase.checkOverdueLoans();
    }
}
