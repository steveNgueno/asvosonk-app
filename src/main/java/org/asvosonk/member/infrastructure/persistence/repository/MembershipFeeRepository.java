package org.asvosonk.member.infrastructure.persistence.repository;

import org.asvosonk.member.domain.valueobject.FeeType;
import org.asvosonk.member.infrastructure.persistence.entity.MembershipFee;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface MembershipFeeRepository extends JpaRepository<MembershipFee, Long> {

    List<MembershipFee> findByMemberIdOrderByFeeType(Long memberId);

    Optional<MembershipFee> findByMemberIdAndFeeType(Long memberId, FeeType feeType);

    /**
     * Ids of members with at least one fee not fully paid — computed in SQL
     * instead of loading every fee row and filtering in Java.
     */
    @org.springframework.data.jpa.repository.Query(
        "SELECT DISTINCT f.member.id FROM MembershipFee f WHERE f.amountPaid < f.amountDue")
    java.util.Set<Long> findMemberIdsWithOutstandingFees();
}
