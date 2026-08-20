package org.asvosonk.member.infrastructure.persistence.repository;

import org.asvosonk.member.infrastructure.persistence.entity.MembershipFeePayment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.List;

@Repository
public interface MembershipFeePaymentRepository extends JpaRepository<MembershipFeePayment, Long> {

    /** Versements de frais encaissés pendant une séance, membre et type compris. */
    @Query("""
            SELECT p FROM MembershipFeePayment p
              JOIN FETCH p.fee f
              JOIN FETCH f.member
             WHERE p.session.id = :sessionId
             ORDER BY p.createdAt ASC
            """)
    List<MembershipFeePayment> findBySessionId(Long sessionId);

    /** Total des frais encaissés pendant une séance. */
    @Query("SELECT COALESCE(SUM(p.amount), 0) FROM MembershipFeePayment p WHERE p.session.id = :sessionId")
    BigDecimal totalBySessionId(Long sessionId);
}
