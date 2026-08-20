package org.asvosonk.session;

import jakarta.persistence.EntityManager;
import org.asvosonk.cashbox.domain.repository.CashboxRepository;
import org.asvosonk.cashbox.domain.valueobject.CashboxType;
import org.asvosonk.member.domain.model.Member;
import org.asvosonk.member.domain.repository.MemberRepository;
import org.asvosonk.presence.application.usecase.CreatePresenceTourUseCase;
import org.asvosonk.presence.domain.model.PresenceTour;
import org.asvosonk.sanction.application.usecase.CreateSanctionUseCase;
import org.asvosonk.sanction.domain.repository.SanctionRepository;
import org.asvosonk.sanction.domain.valueobject.SanctionOrigin;
import org.asvosonk.sanction.domain.valueobject.SanctionStatus;
import org.asvosonk.security.domain.model.AppUser;
import org.asvosonk.security.domain.repository.AppUserRepository;
import org.asvosonk.session.application.service.SessionService;
import org.asvosonk.session.application.service.SessionStepService;
import org.asvosonk.session.domain.valueobject.SessionStep;
import org.asvosonk.session.infrastructure.persistence.entity.MeetingSessionEntity;
import org.asvosonk.session.infrastructure.persistence.entity.SessionReportEntity;
import org.asvosonk.session.infrastructure.persistence.repository.SessionReportRepository;
import org.asvosonk.session.presentation.request.AttendanceEntryForm;
import org.asvosonk.session.presentation.request.SessionForm;
import org.asvosonk.support.AbstractIntegrationTest;
import org.asvosonk.tontine.application.usecase.CreateTourUseCase;
import org.asvosonk.tontine.application.usecase.RecordTontineContributionUseCase;
import org.asvosonk.tontine.domain.model.TontineTour;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Scénario de référence : le déroulé d'une réunion, tel que décrit par le bureau.
 *
 * <p>Cinq membres — Jean, Paul, Pierre, Laurent, Emmanuel. Jean est tiré au sort
 * pour la tontine de présence. Quatre membres cotisent 2 000 FCFA ; Laurent ne
 * donne rien et son fond de roulement le cotise. Quatre membres sont physiquement
 * présents. Jean a une sanction impayée de 500 FCFA, retenue sur sa tontine.</p>
 *
 * <p>Résultats attendus :</p>
 * <ul>
 *   <li>tontine de présence : 5 000 bruts, 500 de retenue, <strong>4 500</strong> perçus ;</li>
 *   <li>développement : <strong>2 500</strong> (5 × 500, le membre cotisé par le fond compris) ;</li>
 *   <li>reliquat boisson : <strong>500</strong> (2 500 collectés − 4 présents × 500) ;</li>
 *   <li>sanctions encaissées : <strong>500</strong> ;</li>
 *   <li>retour en caisse : <strong>0</strong> ;</li>
 *   <li>total remis au trésorier : <strong>3 500</strong> — la tontine, remise en main
 *       propre, en est exclue.</li>
 * </ul>
 *
 * <p>Puis la grande tontine : Pierre bénéficie, Jean lui rend les 15 000 qu'il lui
 * avait cotisés, Emmanuel donne 20 000 librement et Pierre cotise 5 000 pour
 * lui-même — soit <strong>40 000</strong> perçus, sans retenue.</p>
 */
@SpringBootTest
@Transactional
class ReunionScenarioIT extends AbstractIntegrationTest {

    @Autowired SessionService                  sessionService;
    @Autowired SessionStepService              sessionStepService;
    @Autowired SessionReportRepository         reportRepository;
    @Autowired MemberRepository                memberRepository;
    @Autowired AppUserRepository               appUserRepository;
    @Autowired CreatePresenceTourUseCase       createPresenceTour;
    @Autowired CreateTourUseCase               createTontineTour;
    @Autowired RecordTontineContributionUseCase recordContribution;
    @Autowired CreateSanctionUseCase           createSanction;
    @Autowired SanctionRepository              sanctionRepository;
    @Autowired CashboxRepository               cashboxRepository;
    @Autowired EntityManager                   em;

    private AppUser secretary;
    private Map<String, Long> members;

    @BeforeEach
    void seed() {
        secretary = appUserRepository.findByLogin("admin").orElseThrow();

        // Le membre « Administrateur » du jeu de données initial est écarté :
        // le scénario raisonne sur cinq membres exactement.
        em.createNativeQuery("UPDATE member SET status = 'resigned' WHERE full_name = 'Administrateur'")
            .executeUpdate();

        members = new HashMap<>();
        for (String name : List.of("Jean", "Paul", "Pierre", "Laurent", "Emmanuel")) {
            Long id = ((Number) em.createNativeQuery(
                    "INSERT INTO member (full_name, join_date) VALUES (:n, DATE '2026-01-01') RETURNING id")
                .setParameter("n", name)
                .getSingleResult()).longValue();
            members.put(name, id);
            // Fond de roulement de 5 000 FCFA, acquitté à l'adhésion.
            em.createNativeQuery("INSERT INTO revolving_fund (member_id, balance) VALUES (:m, 5000)")
                .setParameter("m", id)
                .executeUpdate();
        }
        em.flush();
    }

    @Test
    void deroulementCompletDUneReunion() {
        // ── Tour de présence : tous les membres actifs ──────────────
        PresenceTour tour = createPresenceTour.execute(LocalDate.of(2026, 8, 3));
        assertThat(tour).isNotNull();

        // ── Sanction impayée de Jean : 500 FCFA ─────────────────────
        createSanction.execute(members.get("Jean"), LocalDate.of(2026, 7, 27),
            new BigDecimal("500"), "Retard", SanctionOrigin.manual, null);
        em.flush();

        // ── Séance du lundi ─────────────────────────────────────────
        SessionForm form = new SessionForm();
        form.setSessionDate(LocalDate.of(2026, 8, 10));
        form.setAgenda("Prière, rapport, nouvelles de famille, finances, divers");
        MeetingSessionEntity session = sessionService.create(form, secretary);
        em.flush();

        sessionStepService.transitionToNext(session.getId(), secretary, SessionStep.CREATED);

        // Tirage au sort : Jean bénéficie de la tontine de présence.
        sessionService.setBeneficiary(session.getId(), members.get("Jean"));

        // Saisie : 2 000 FCFA pour tous sauf Laurent ; Emmanuel est absent.
        record Entry(String name, boolean present, String paid) { }
        List<Entry> sheet = List.of(
            new Entry("Jean", true, "2000"),
            new Entry("Pierre", true, "2000"),
            new Entry("Paul", true, "2000"),
            new Entry("Laurent", true, "0"),
            new Entry("Emmanuel", false, "2000"));

        for (Entry e : sheet) {
            AttendanceEntryForm entry = new AttendanceEntryForm();
            entry.setMemberId(members.get(e.name()));
            entry.setPresent(e.present());
            entry.setAmountPaid(new BigDecimal(e.paid()));
            sessionService.saveAttendanceEntry(session.getId(), entry);
        }
        em.flush();

        sessionStepService.transitionToNext(session.getId(), secretary, SessionStep.PRESENCE_OPEN);
        em.flush();
        em.clear();

        SessionReportEntity report = reportRepository.findBySessionId(session.getId()).orElseThrow();

        // ── Tontine de présence ─────────────────────────────────────
        assertThat(report.getPresenceTotalCotisants()).isEqualTo(5);
        assertThat(report.getPresencePresentCount()).isEqualTo(4);
        assertThat(report.getPresenceFundCoveredCount()).isEqualTo(1);
        assertThat(report.getPresenceDefaultCount()).isZero();
        assertThat(report.getPresenceGrossTontine()).isEqualByComparingTo("5000");
        assertThat(report.getPresenceSanctionDeductions()).isEqualByComparingTo("500");
        assertThat(report.getPresenceNetTontine()).isEqualByComparingTo("4500");

        // ── Entrées du jour ─────────────────────────────────────────
        assertThat(report.getPresenceDevelopmentTotal()).isEqualByComparingTo("2500");
        assertThat(report.getPresenceBeverageReliquat()).isEqualByComparingTo("500");
        assertThat(report.getSanctionsCollected()).isEqualByComparingTo("500");
        assertThat(report.getPresenceReturnToFund()).isEqualByComparingTo("0");

        // ── Total remis au trésorier : 2 500 + 500 + 500 ────────────
        assertThat(report.getTotalToTreasurer()).isEqualByComparingTo("3500");

        // La sanction de Jean est soldée et son paiement daté.
        var jeanSanctions = sanctionRepository.findByMemberIdOrderBySanctionDateDesc(members.get("Jean"));
        assertThat(jeanSanctions).hasSize(1);
        assertThat(jeanSanctions.get(0).getStatus()).isEqualTo(SanctionStatus.paid);
        assertThat(jeanSanctions.get(0).getPaymentDate()).isNotNull();

        // Laurent est cotisé par le fond : 200 FCFA de sanction et 2 000 avancés.
        var laurentSanctions = sanctionRepository.findByMemberIdOrderBySanctionDateDesc(members.get("Laurent"));
        assertThat(laurentSanctions).hasSize(1);
        assertThat(laurentSanctions.get(0).getAmount()).isEqualByComparingTo("200");
        assertThat(fundBalance(members.get("Laurent"))).isEqualByComparingTo("3000");

        // Les caisses ont réellement encaissé.
        assertThat(cashboxBalance(CashboxType.development)).isEqualByComparingTo("2500");
        assertThat(cashboxBalance(CashboxType.beverage)).isEqualByComparingTo("500");
        assertThat(cashboxBalance(CashboxType.sanction)).isEqualByComparingTo("500");

        // ══ Grande tontine ══════════════════════════════════════════
        sessionStepService.transitionToNext(session.getId(), secretary, SessionStep.PRESENCE_CLOSED);

        TontineTour tontineTour = createTontineTour.execute(
            LocalDate.of(2026, 8, 3),
            List.of(members.get("Jean"), members.get("Pierre"), members.get("Emmanuel")),
            List.of(1, 2, 3));
        em.flush();

        // Séance précédente : Jean a bénéficié, Pierre lui avait cotisé 15 000.
        // La dette « Jean doit 15 000 à Pierre » est donc déjà née.
        em.createNativeQuery("""
                INSERT INTO tontine_debt (tour_id, debtor_id, creditor_id, amount, origin_session_id, status)
                VALUES (:tour, :jean, :pierre, 15000, :session, 'owed')
                """)
            .setParameter("tour", tontineTour.getId())
            .setParameter("jean", members.get("Jean"))
            .setParameter("pierre", members.get("Pierre"))
            .setParameter("session", session.getId())
            .executeUpdate();
        em.createNativeQuery(
                "UPDATE tontine_participant SET has_benefited = true WHERE tour_id = :t AND member_id = :m")
            .setParameter("t", tontineTour.getId())
            .setParameter("m", members.get("Jean"))
            .executeUpdate();
        em.flush();

        Long pierre = members.get("Pierre");
        // Jean rend exactement ce que Pierre lui avait cotisé.
        recordContribution.execute(tontineTour.getId(), session.getId(),
            members.get("Jean"), pierre, new BigDecimal("15000"));
        // Le bénéficiaire cotise lui aussi, librement.
        recordContribution.execute(tontineTour.getId(), session.getId(),
            pierre, pierre, new BigDecimal("5000"));
        // Emmanuel n'a pas encore bénéficié : montant libre.
        recordContribution.execute(tontineTour.getId(), session.getId(),
            members.get("Emmanuel"), pierre, new BigDecimal("20000"));
        em.flush();

        sessionStepService.transitionToNext(session.getId(), secretary, SessionStep.TONTINE_OPEN);
        em.flush();
        em.clear();

        report = reportRepository.findBySessionId(session.getId()).orElseThrow();
        assertThat(report.getTontineGrossCollected()).isEqualByComparingTo("40000");
        assertThat(report.getTontineSanctionDeductions()).isEqualByComparingTo("0");
        assertThat(report.getTontineNetPaid()).isEqualByComparingTo("40000");
        assertThat(report.getTontineBeneficiaryId()).isEqualTo(pierre);

        // La grande tontine ne transite par aucune caisse : le total remis au
        // trésorier reste celui de la présence.
        assertThat(report.getTotalToTreasurer()).isEqualByComparingTo("3500");

        // La cotisation du bénéficiaire pour lui-même ne crée aucune dette.
        Number selfDebts = (Number) em.createNativeQuery("""
                SELECT COUNT(*) FROM tontine_debt
                 WHERE tour_id = :t AND debtor_id = :m AND creditor_id = :m
                """)
            .setParameter("t", tontineTour.getId())
            .setParameter("m", pierre)
            .getSingleResult();
        assertThat(selfDebts.longValue()).isZero();

        // ══ Reste du déroulé jusqu'au rapport ═══════════════════════
        sessionStepService.transitionToNext(session.getId(), secretary, SessionStep.TONTINE_CLOSED);
        sessionStepService.transitionToNext(session.getId(), secretary, SessionStep.BANQUE_PROJET_OPEN);
        sessionStepService.transitionToNext(session.getId(), secretary, SessionStep.BANQUE_PROJET_CLOSED);
        sessionStepService.transitionToNext(session.getId(), secretary, SessionStep.BANQUE_ANNUELLE_OPEN);
        sessionStepService.transitionToNext(session.getId(), secretary, SessionStep.BANQUE_ANNUELLE_CLOSED);
        em.flush();
        em.clear();

        MeetingSessionEntity closed = sessionService.findById(session.getId());
        assertThat(closed.getCurrentStepEnum()).isEqualTo(SessionStep.REPORT_GENERATED);
        assertThat(reportRepository.findBySessionId(session.getId()).orElseThrow()
            .getTotalToTreasurer()).isEqualByComparingTo("3500");
    }

    private BigDecimal fundBalance(Long memberId) {
        return (BigDecimal) em.createNativeQuery(
                "SELECT balance FROM revolving_fund WHERE member_id = :m")
            .setParameter("m", memberId)
            .getSingleResult();
    }

    private BigDecimal cashboxBalance(CashboxType type) {
        return cashboxRepository.findByType(type).orElseThrow().getBalance();
    }
}
