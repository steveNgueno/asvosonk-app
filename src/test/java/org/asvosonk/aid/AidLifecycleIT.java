package org.asvosonk.aid;

import jakarta.persistence.EntityManager;
import org.asvosonk.aid.application.usecase.CreateAidUseCase;
import org.asvosonk.aid.application.usecase.DeductAidsUseCase;
import org.asvosonk.aid.application.usecase.RecordAidRecoveryUseCase;
import org.asvosonk.aid.domain.model.Aid;
import org.asvosonk.aid.domain.model.AidContribution;
import org.asvosonk.aid.domain.repository.AidContributionRepository;
import org.asvosonk.aid.domain.repository.AidRepository;
import org.asvosonk.aid.domain.valueobject.AidContributionStatus;
import org.asvosonk.aid.domain.valueobject.AidPaymentMode;
import org.asvosonk.aid.domain.valueobject.AidStatus;
import org.asvosonk.aid.domain.valueobject.AidType;
import org.asvosonk.common.domain.exception.BusinessRuleException;
import org.asvosonk.member.application.usecase.CreateMemberUseCase;
import org.asvosonk.member.domain.model.Member;
import org.asvosonk.member.domain.repository.MemberRepository;
import org.asvosonk.security.domain.repository.AppUserRepository;
import org.asvosonk.session.application.service.SessionService;
import org.asvosonk.session.infrastructure.persistence.entity.MeetingSessionEntity;
import org.asvosonk.session.presentation.request.SessionForm;
import org.asvosonk.session.infrastructure.persistence.repository.SpringDataMeetingSessionRepository;
import org.asvosonk.support.AbstractIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Cycle de vie d'une aide : enregistrement en séance avec instantané des
 * membres actifs, recouvrement direct, complétion quand tout le monde a
 * recouvert, et retenue obligatoire sur tontine.
 *
 * <p>Le seed fournit le membre 1 (« Administrateur »), seul membre actif.
 * Des membres supplémentaires sont créés dans le setup pour que la division
 * d'aide ait un sens.</p>
 */
@SpringBootTest
@Transactional
class AidLifecycleIT extends AbstractIntegrationTest {

    private static final Long SEED_MEMBER_ID = 1L;

    @Autowired CreateAidUseCase          createAid;
    @Autowired RecordAidRecoveryUseCase  recordRecovery;
    @Autowired DeductAidsUseCase         deductAids;
    @Autowired AidRepository             aidRepository;
    @Autowired AidContributionRepository aidContributionRepository;
    @Autowired MemberRepository          memberRepository;
    @Autowired CreateMemberUseCase       createMember;
    @Autowired SessionService            sessionService;
    @Autowired AppUserRepository         appUserRepository;
    @Autowired EntityManager             em;
    @Autowired SpringDataMeetingSessionRepository sessionSpringData;

    private Long memberB;
    private Long memberC;

    @BeforeEach
    void setUp() {
        SessionForm form = new SessionForm();
        form.setSessionDate(LocalDate.of(2026, 5, 4));
        sessionService.create(form, appUserRepository.findByLogin("admin").orElseThrow());
        em.flush();

        Member b = createMember.execute(newMember("Membre B"));
        Member c = createMember.execute(newMember("Membre C"));
        em.flush();
        memberB = b.getId();
        memberC = c.getId();
    }

    private org.asvosonk.member.presentation.request.MemberRequest newMember(String name) {
        var request = new org.asvosonk.member.presentation.request.MemberRequest();
        request.setFullName(name);
        request.setJoinDate(LocalDate.of(2026, 1, 10));
        request.setResident(true);
        return request;
    }

    /** Trois membres actifs (seed + B + C) : part de 2 000 chacun pour 6 000 remis. */
    private Aid aidOfThreeMembers() {
        return createAid.execute(SEED_MEMBER_ID, AidType.deces_parent, LocalDate.now(),
            new BigDecimal("6000"), new BigDecimal("2000"), "décès du père", null);
    }

    // ── Enregistrement ───────────────────────────────────────

    @Test
    void creatingAnAidOutsideAnySession_isRejected() {
        em.createNativeQuery("UPDATE meeting_session SET status = 'closed'").executeUpdate();
        em.flush();
        em.clear();

        assertThatThrownBy(() -> createAid.execute(SEED_MEMBER_ID, AidType.deces_parent,
                LocalDate.now(), new BigDecimal("200000"), new BigDecimal("5000"), null, null))
            .isInstanceOf(BusinessRuleException.class)
            .hasMessageContaining("Aucune séance");
    }

    @Test
    void creatingAnAid_createsAShareForEachActiveMember() {
        Aid aid = aidOfThreeMembers();

        List<AidContribution> shares = aidContributionRepository.findByAidId(aid.getId());

        assertThat(shares).hasSize(3); // Administrateur + B + C, bénéficiaire inclus
        assertThat(shares).allSatisfy(s -> {
            assertThat(s.getAmountDue()).isEqualByComparingTo("2000");
            assertThat(s.getStatus()).isEqualTo(AidContributionStatus.owed);
        });
        assertThat(shares).extracting(AidContribution::getMemberId)
            .contains(SEED_MEMBER_ID, memberB, memberC);
    }

    @Test
    void aMemberJoiningAfterTheAid_isNotConcerned() {
        Aid aid = aidOfThreeMembers();

        createMember.execute(newMember("Arrivant après coup"));
        em.flush();

        assertThat(aidContributionRepository.findByAidId(aid.getId())).hasSize(3);
    }

    @Test
    void creatingAnAidWithNegativeShare_isRejected() {
        assertThatThrownBy(() -> createAid.execute(SEED_MEMBER_ID, AidType.deces_parent,
                LocalDate.now(), new BigDecimal("6000"), new BigDecimal("-1"), null, null))
            .isInstanceOf(BusinessRuleException.class)
            .hasMessageContaining("part par membre");
    }

    // ── Recouvrement direct ─────────────────────────────────

    @Test
    void directRecovery_outsideAnySession_isRejected() {
        Aid aid = aidOfThreeMembers();
        Long shareId = shareOf(aid, memberC);

        em.createNativeQuery("UPDATE meeting_session SET status = 'closed'").executeUpdate();
        em.flush();
        em.clear();

        assertThatThrownBy(() -> recordRecovery.execute(shareId, null, null))
            .isInstanceOf(BusinessRuleException.class)
            .hasMessageContaining("Aucune séance");
    }

    @Test
    void directRecovery_marksTheSharePaid_andCompletesAidWhenAllRecovered() {
        Aid aid = aidOfThreeMembers();

        recordRecovery.execute(shareOf(aid, SEED_MEMBER_ID), null, null);
        recordRecovery.execute(shareOf(aid, memberB), null, null);

        // Deux parts sur trois : l'aide est encore d'actualité.
        assertThat(aidRepository.findById(aid.getId()).orElseThrow().getStatus())
            .isEqualTo(AidStatus.in_progress);

        recordRecovery.execute(shareOf(aid, memberC), null, null);

        Aid recovered = aidRepository.findById(aid.getId()).orElseThrow();
        assertThat(recovered.getStatus()).isEqualTo(AidStatus.completed);
        assertThat(recovered.isCurrent()).isFalse();

        AidContribution paid = aidContributionRepository
            .findByAidIdAndMemberId(aid.getId(), memberC).orElseThrow();
        assertThat(paid.getStatus()).isEqualTo(AidContributionStatus.paid);
        assertThat(paid.getPaymentMode()).isEqualTo(AidPaymentMode.direct);
        assertThat(paid.getSessionId()).isNotNull();
    }

    @Test
    void partialDirectRecovery_isAccepted_shareStaysOwedForTheBalance() {
        Aid aid = aidOfThreeMembers();
        Long shareId = shareOf(aid, memberB);

        AidContribution afterPartial = recordRecovery.execute(
            shareId, new BigDecimal("1200"), null);

        assertThat(afterPartial.getAmountPaid()).isEqualByComparingTo("1200");
        assertThat(afterPartial.getStatus()).isEqualTo(AidContributionStatus.owed);
        assertThat(afterPartial.getRemaining()).isEqualByComparingTo("800");

        // Le solde complète la part.
        AidContribution settled = recordRecovery.execute(shareId, null, null);
        assertThat(settled.getStatus()).isEqualTo(AidContributionStatus.paid);
        assertThat(settled.getAmountPaid()).isEqualByComparingTo("2000");
    }

    @Test
    void overpayingAShare_isRejected() {
        Aid aid = aidOfThreeMembers();
        Long shareId = shareOf(aid, memberB);

        assertThatThrownBy(() -> recordRecovery.execute(
                shareId, new BigDecimal("3000"), null))
            .isInstanceOf(BusinessRuleException.class)
            .hasMessageContaining("dépasse");
    }

    @Test
    void recoveringACompletedAidShare_isRejected() {
        Aid aid = aidOfThreeMembers();
        for (Long memberId : List.of(SEED_MEMBER_ID, memberB, memberC)) {
            recordRecovery.execute(shareOf(aid, memberId), null, null);
        }
        Long shareId = shareOf(aid, memberB);

        assertThatThrownBy(() -> recordRecovery.execute(shareId, null, null))
            .isInstanceOf(BusinessRuleException.class)
            .hasMessageContaining("déjà été entièrement recouverte");
    }

    @Test
    void totalPaidDuringSession_countsAllRecoveriesAsEntriesOfTheDay() {
        Aid aid = aidOfThreeMembers();

        recordRecovery.execute(shareOf(aid, SEED_MEMBER_ID), null, null);
        recordRecovery.execute(shareOf(aid, memberB), new BigDecimal("500"), null);
        recordRecovery.execute(shareOf(aid, memberB), null, null);

        BigDecimal total = aidContributionRepository
            .totalPaidBySessionId(currentOpenSession().getId());

        // 2 000 (Administrateur) + 2 000 (B : 500 puis solde 1 500).
        assertThat(total).isEqualByComparingTo("4000");
    }

    // ── Retenue sur tontine ─────────────────────────────────

    @Test
    void deductionOnTontine_coversSharesPartially_thenFully() {
        Aid aid = aidOfThreeMembers();
        MeetingSessionEntity session = currentOpenSession();

        // La tontine ne couvre qu'une partie de la part du membre C.
        BigDecimal firstCut = deductAids.deduct(memberC, new BigDecimal("700"),
            session, AidPaymentMode.retained_tontine, "test", null);

        assertThat(firstCut).isEqualByComparingTo("700");
        AidContribution partial = reloadShare(aid.getId(), memberC);
        assertThat(partial.getAmountPaid()).isEqualByComparingTo("700");
        assertThat(partial.getRemaining()).isEqualByComparingTo("1300");
        assertThat(partial.getPaymentMode()).isEqualTo(AidPaymentMode.retained_tontine);

        // Une seconde perception solde la part ; l'aide reste en cours tant que
        // les autres membres n'ont pas recouvert.
        BigDecimal secondCut = deductAids.deduct(memberC, new BigDecimal("5000"),
            session, AidPaymentMode.retained_tontine, "test", null);

        assertThat(secondCut).isEqualByComparingTo("1300");
        assertThat(reloadShare(aid.getId(), memberC).getStatus())
            .isEqualTo(AidContributionStatus.paid);
        assertThat(aidRepository.findById(aid.getId()).orElseThrow().getStatus())
            .isEqualTo(AidStatus.in_progress);
    }

    @Test
    void deductionCompletesTheAid_whenItWasTheLastOwedShare() {
        Aid aid = aidOfThreeMembers();
        MeetingSessionEntity session = currentOpenSession();

        recordRecovery.execute(shareOf(aid, SEED_MEMBER_ID), null, null);
        recordRecovery.execute(shareOf(aid, memberB), null, null);

        deductAids.deduct(memberC, new BigDecimal("5000"),
            session, AidPaymentMode.retained_presence, "test", null);

        assertThat(aidRepository.findById(aid.getId()).orElseThrow().getStatus())
            .isEqualTo(AidStatus.completed);
    }

    @Test
    void deductionWithoutAnythingOwed_returnsZero() {
        MeetingSessionEntity session = currentOpenSession();

        BigDecimal deducted = deductAids.deduct(memberC, new BigDecimal("10000"),
            session, AidPaymentMode.retained_tontine, "test", null);

        assertThat(deducted).isEqualByComparingTo("0");
    }

    // ── Helpers ──────────────────────────────────────────────

    private Long shareOf(Aid aid, Long memberId) {
        return aidContributionRepository.findByAidIdAndMemberId(aid.getId(), memberId)
            .orElseThrow().getId();
    }

    private AidContribution reloadShare(Long aidId, Long memberId) {
        em.flush();
        em.clear();
        return aidContributionRepository.findByAidIdAndMemberId(aidId, memberId).orElseThrow();
    }

    /** La séance ouverte du jour (celle créée dans le setup). */
    private MeetingSessionEntity currentOpenSession() {
        return sessionSpringData.findAllByOrderBySessionDateDesc().stream()
            .filter(s -> !s.isClosed())
            .findFirst()
            .orElseThrow();
    }
}
