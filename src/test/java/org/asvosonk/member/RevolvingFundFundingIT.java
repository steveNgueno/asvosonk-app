package org.asvosonk.member;

import org.asvosonk.common.domain.exception.BusinessRuleException;
import org.asvosonk.member.application.usecase.CreateMemberUseCase;
import org.asvosonk.member.application.usecase.RecordFeePaymentUseCase;
import org.asvosonk.member.domain.model.Member;
import org.asvosonk.member.domain.valueobject.FeeType;
import org.asvosonk.member.presentation.request.MemberRequest;
import org.asvosonk.security.domain.model.AppUser;
import org.asvosonk.security.domain.repository.AppUserRepository;
import org.asvosonk.session.application.service.SessionService;
import org.asvosonk.session.domain.repository.RevolvingFundRepository;
import org.asvosonk.session.presentation.request.SessionForm;
import org.asvosonk.support.AbstractIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Alimentation du fond de roulement par le frais d'adhésion du même nom.
 *
 * <p>Le fond démarre vide : il ne contient jamais que ce qui a réellement été
 * versé. Les frais, eux, ne s'encaissent qu'en séance — chaque versement est
 * rattaché à la réunion du jour et compte dans ses entrées.</p>
 */
@SpringBootTest
@Transactional
class RevolvingFundFundingIT extends AbstractIntegrationTest {

    @Autowired CreateMemberUseCase      createMember;
    @Autowired RecordFeePaymentUseCase  recordFeePayment;
    @Autowired RevolvingFundRepository  fundRepository;
    @Autowired SessionService           sessionService;
    @Autowired AppUserRepository        appUserRepository;

    private AppUser secretary;

    @BeforeEach
    void openSession() {
        secretary = appUserRepository.findByLogin("admin").orElseThrow();
        SessionForm form = new SessionForm();
        form.setSessionDate(LocalDate.of(2026, 3, 2));
        sessionService.create(form, secretary);
    }

    private Long newMemberId() {
        MemberRequest req = new MemberRequest();
        req.setFullName("Test Membre " + System.nanoTime());
        req.setJoinDate(LocalDate.now());
        req.setResident(true);
        Member m = createMember.execute(req);
        return m.getId();
    }

    private void pay(Long memberId, FeeType type, String amount) {
        recordFeePayment.execute(memberId, type, new BigDecimal(amount), secretary);
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
        pay(id, FeeType.revolving_fund, "5000");
        assertThat(fundRepository.findByMemberId(id).orElseThrow().getBalance())
            .isEqualByComparingTo("5000");
    }

    @Test
    void partialPaymentsAccumulateWithoutDoubleCounting() {
        Long id = newMemberId();
        pay(id, FeeType.revolving_fund, "2000");
        assertThat(fundRepository.findByMemberId(id).orElseThrow().getBalance())
            .isEqualByComparingTo("2000");

        // Pay the remaining 3000 (fee due is 5000) -> fund reaches exactly 5000.
        pay(id, FeeType.revolving_fund, "3000");
        assertThat(fundRepository.findByMemberId(id).orElseThrow().getBalance())
            .isEqualByComparingTo("5000");

        // F-20 — once the fee is fully paid, a further payment is REJECTED
        // (not silently capped), same principle as loan over-repayment (F-19).
        assertThatThrownBy(() -> pay(id, FeeType.revolving_fund, "1000"))
            .isInstanceOf(BusinessRuleException.class);
        assertThat(fundRepository.findByMemberId(id).orElseThrow().getBalance())
            .isEqualByComparingTo("5000");
    }

    @Test
    void payingAnotherFeeTypeDoesNotCreditTheFund() {
        Long id = newMemberId();
        pay(id, FeeType.registration, "2500");
        assertThat(fundRepository.findByMemberId(id).orElseThrow().getBalance())
            .isEqualByComparingTo("0");
    }

    /** Les frais d'adhésion ne se paient qu'en séance. */
    @Test
    void unFraisNePeutPasEtreEncaisseHorsSeance() {
        Long id = newMemberId();
        closeCurrentSession();

        assertThatThrownBy(() -> pay(id, FeeType.registration, "2500"))
            .isInstanceOf(BusinessRuleException.class)
            .hasMessageContaining("Aucune séance");
    }

    private void closeCurrentSession() {
        em().createNativeQuery("UPDATE meeting_session SET status = 'closed'").executeUpdate();
        em().flush();
        em().clear();
    }

    @Autowired jakarta.persistence.EntityManager entityManager;

    private jakarta.persistence.EntityManager em() {
        return entityManager;
    }
}
