package org.asvosonk.member;

import org.asvosonk.member.application.usecase.CreateMemberUseCase;
import org.asvosonk.member.application.usecase.RecordFeePaymentUseCase;
import org.asvosonk.member.domain.model.Member;
import org.asvosonk.member.domain.valueobject.FeeType;
import org.asvosonk.member.presentation.request.MemberRequest;
import org.asvosonk.session.domain.repository.RevolvingFundRepository;
import org.asvosonk.support.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * F-03: the revolving fund must start EMPTY and only be fed by the real payment
 * of the {@code revolving_fund} membership fee — no fictitious 5 000 capital.
 */
@SpringBootTest
@Transactional
class RevolvingFundFundingIT extends AbstractIntegrationTest {

    @Autowired CreateMemberUseCase      createMember;
    @Autowired RecordFeePaymentUseCase  recordFeePayment;
    @Autowired RevolvingFundRepository  fundRepository;

    private Long newMemberId() {
        MemberRequest req = new MemberRequest();
        req.setFullName("Test Membre " + System.nanoTime());
        req.setJoinDate(LocalDate.now());
        req.setResident(true);
        Member m = createMember.execute(req);
        return m.getId();
    }

    @Test
    void newMemberStartsWithZeroFund() {
        Long id = newMemberId();
        BigDecimal balance = fundRepository.findByMemberId(id).orElseThrow().getBalance();
        assertThat(balance).isEqualByComparingTo("0");
    }

    @Test
    void payingRevolvingFundFeeCreditsTheFund() {
        Long id = newMemberId();
        recordFeePayment.execute(id, FeeType.revolving_fund, new BigDecimal("5000"));
        assertThat(fundRepository.findByMemberId(id).orElseThrow().getBalance())
            .isEqualByComparingTo("5000");
    }

    @Test
    void partialPaymentsAccumulateWithoutDoubleCounting() {
        Long id = newMemberId();
        recordFeePayment.execute(id, FeeType.revolving_fund, new BigDecimal("2000"));
        assertThat(fundRepository.findByMemberId(id).orElseThrow().getBalance())
            .isEqualByComparingTo("2000");

        // Pay the remaining 3000 (fee due is 5000, capped) -> fund should reach 5000, not 7000.
        recordFeePayment.execute(id, FeeType.revolving_fund, new BigDecimal("3000"));
        assertThat(fundRepository.findByMemberId(id).orElseThrow().getBalance())
            .isEqualByComparingTo("5000");

        // Any further payment is capped at the fee due -> no extra credit.
        recordFeePayment.execute(id, FeeType.revolving_fund, new BigDecimal("1000"));
        assertThat(fundRepository.findByMemberId(id).orElseThrow().getBalance())
            .isEqualByComparingTo("5000");
    }

    @Test
    void payingAnotherFeeTypeDoesNotCreditTheFund() {
        Long id = newMemberId();
        recordFeePayment.execute(id, FeeType.registration, new BigDecimal("2500"));
        assertThat(fundRepository.findByMemberId(id).orElseThrow().getBalance())
            .isEqualByComparingTo("0");
    }
}
