package org.asvosonk.bank.application.usecase;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.asvosonk.bank.domain.model.Loan;
import org.asvosonk.bank.domain.repository.LoanRepository;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class CheckOverdueLoansUseCase {

    private final LoanRepository loanRepository;

    /**
     * Scheduled task that runs daily to mark overdue loans.
     * Runs at 02:00 AM each day.
     */
    @Scheduled(cron = "0 0 2 * * *")
    @Transactional
    public void checkOverdueLoans() {
        List<Loan> overdueLoans = loanRepository.findOverdueLoans();
        for (Loan loan : overdueLoans) {
            loan.markAsOverdue();
            loanRepository.save(loan);
            log.info("Loan #{} marked as overdue (due: {}, total: {} FCFA)",
                loan.getId(), loan.getDueDate(), loan.getTotalDue());
        }
        if (!overdueLoans.isEmpty()) {
            log.info("Checked overdue loans: {} marked as overdue", overdueLoans.size());
        }
    }
}
