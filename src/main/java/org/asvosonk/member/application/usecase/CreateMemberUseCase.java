package org.asvosonk.member.application.usecase;

import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.asvosonk.member.domain.model.Member;
import org.asvosonk.member.domain.repository.MemberRepository;
import org.asvosonk.member.domain.valueobject.FeeType;
import org.asvosonk.member.domain.valueobject.MemberStatus;
import org.asvosonk.member.infrastructure.persistence.entity.MemberEntity;
import org.asvosonk.member.infrastructure.persistence.entity.MembershipFee;
import org.asvosonk.member.infrastructure.persistence.repository.MembershipFeeRepository;
import org.asvosonk.member.presentation.request.MemberRequest;
import org.asvosonk.session.infrastructure.persistence.entity.RevolvingFundEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class CreateMemberUseCase {

    private static final BigDecimal REVOLVING_FUND_INITIAL = new BigDecimal("5000");

    private final MemberRepository        memberRepository;
    private final MembershipFeeRepository feeRepository;
    private final EntityManager           entityManager;

    @Transactional
    public Member execute(MemberRequest request) {
        LocalDateTime now = LocalDateTime.now();
        Member member = new Member(
            null,
            request.getFullName().trim(),
            request.getPhone() != null ? request.getPhone().trim() : null,
            request.getJoinDate(),
            request.isResident(),
            request.getStatus() != null ? request.getStatus() : MemberStatus.active,
            now,
            now
        );
        Member created = memberRepository.save(member);

        // Get a JPA entity reference for relationship mappings
        MemberEntity memberEntity = entityManager.getReference(MemberEntity.class, created.getId());

        // ── Initialize membership fees ───────────────────────────────────
        for (FeeType feeType : FeeType.values()) {
            MembershipFee fee = new MembershipFee();
            fee.setMember(memberEntity);
            fee.setFeeType(feeType);
            fee.setAmountDue(feeType.defaultAmount());
            fee.setAmountPaid(BigDecimal.ZERO);
            feeRepository.save(fee);
        }

        // ── Initialize revolving fund ────────────────────────────────────
        RevolvingFundEntity fund = new RevolvingFundEntity();
        fund.setMember(memberEntity);
        fund.setBalance(REVOLVING_FUND_INITIAL);
        entityManager.persist(fund);

        return created;
    }
}
