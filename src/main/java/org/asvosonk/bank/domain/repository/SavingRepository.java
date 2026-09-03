package org.asvosonk.bank.domain.repository;

import org.asvosonk.bank.domain.model.Saving;

import java.math.BigDecimal;
import java.util.List;

public interface SavingRepository {

    Saving save(Saving saving);

    List<Saving> findByMemberIdOrderByOperationDateDesc(Long memberId);

    BigDecimal getTotalSavingsByMemberId(Long memberId);

    /** Épargnes saisies pendant une séance, dans leur ordre de saisie. */
    List<Saving> findBySessionId(Long sessionId);

    /** Total des épargnes collectées pendant une séance ; zéro si aucune. */
    BigDecimal getTotalSavingsBySessionId(Long sessionId);
}
