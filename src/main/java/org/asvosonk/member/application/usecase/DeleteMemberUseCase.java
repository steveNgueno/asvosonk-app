package org.asvosonk.member.application.usecase;

import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.asvosonk.common.domain.exception.BusinessRuleException;
import org.asvosonk.common.domain.exception.ResourceNotFoundException;
import org.asvosonk.member.domain.model.Member;
import org.asvosonk.member.domain.repository.MemberRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class DeleteMemberUseCase {

    private final MemberRepository memberRepository;
    private final EntityManager entityManager;

    /**
     * F-11 — Never erase a member who carries financial history.
     *
     * <p>A hard delete used to cascade through every financial FK (fees,
     * savings, loans, sanctions, tontine contributions and debts), wiping the
     * accounting trail — including debts owed to <em>other</em> members. This
     * guard refuses such a delete and points the caller to a status change
     * ({@code resigned} / {@code deceased}), which preserves history. The
     * database also enforces this via {@code ON DELETE RESTRICT} (V16), so even
     * a direct SQL delete cannot erase the ledger.
     */
    @Transactional
    public void execute(Long id) {
        Member member = memberRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Membre", id));

        if (hasFinancialHistory(id)) {
            throw new BusinessRuleException(
                "Ce membre possède un historique financier (cotisations, épargne, "
              + "prêts, sanctions ou tontine) et ne peut pas être supprimé. "
              + "Changez plutôt son statut en « démissionné » ou « décédé » pour "
              + "préserver la piste comptable.");
        }

        memberRepository.delete(member);
    }

    /**
     * True money only. Every member is registered with empty {@code membership_fee}
     * slots (amount_paid = 0) and a zero-balance {@code revolving_fund}; those must
     * NOT count as history, otherwise no member could ever be removed. We therefore
     * count fees only when something was actually paid, the fund only when its
     * balance is positive, and any row at all in the transactional ledgers.
     */
    private boolean hasFinancialHistory(Long memberId) {
        Number count = (Number) entityManager.createNativeQuery("""
            SELECT
                (SELECT COUNT(*) FROM membership_fee       WHERE member_id = :id AND amount_paid > 0)
              + (SELECT COUNT(*) FROM revolving_fund       WHERE member_id = :id AND balance > 0)
              + (SELECT COUNT(*) FROM saving               WHERE member_id = :id)
              + (SELECT COUNT(*) FROM loan                 WHERE member_id = :id)
              + (SELECT COUNT(*) FROM sanction             WHERE member_id = :id)
              + (SELECT COUNT(*) FROM tontine_participant  WHERE member_id = :id)
              + (SELECT COUNT(*) FROM tontine_contribution WHERE contributor_id = :id OR beneficiary_id = :id)
              + (SELECT COUNT(*) FROM tontine_debt         WHERE debtor_id = :id OR creditor_id = :id)
            """)
            .setParameter("id", memberId)
            .getSingleResult();
        return count.longValue() > 0;
    }
}
