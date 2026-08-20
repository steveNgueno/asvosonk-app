package org.asvosonk.presence;

import jakarta.persistence.EntityManager;
import org.asvosonk.member.application.usecase.CreateMemberUseCase;
import org.asvosonk.member.domain.model.Member;
import org.asvosonk.member.presentation.request.MemberRequest;
import org.asvosonk.presence.application.usecase.CreatePresenceTourUseCase;
import org.asvosonk.presence.application.usecase.GetPresenceTourSummaryUseCase;
import org.asvosonk.presence.domain.model.PresenceTour;
import org.asvosonk.presence.domain.model.PresenceTourParticipant;
import org.asvosonk.security.domain.model.AppUser;
import org.asvosonk.security.domain.repository.AppUserRepository;
import org.asvosonk.session.application.service.SessionService;
import org.asvosonk.session.application.service.SessionStepService;
import org.asvosonk.session.application.usecase.ComputePresenceFeeUseCase;
import org.asvosonk.session.domain.valueobject.SessionStep;
import org.asvosonk.session.infrastructure.persistence.entity.MeetingSessionEntity;
import org.asvosonk.session.infrastructure.persistence.entity.SessionReportEntity;
import org.asvosonk.session.infrastructure.persistence.repository.SessionReportRepository;
import org.asvosonk.session.presentation.request.AttendanceEntryForm;
import org.asvosonk.session.presentation.request.SessionForm;
import org.asvosonk.support.AbstractIntegrationTest;
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
 * Arrivée d'un membre en cours de tour de présence.
 *
 * <p>Le nouvel adhérent entre dans le tour en cours, en dernière position, et ne
 * participe pas aux tirages. Lorsque son tour vient, les membres qui avaient
 * <strong>déjà bénéficié avant son arrivée</strong> ne lui doivent que 1 000 FCFA
 * (500 boisson + 500 développement) : il n'avait pas cotisé pour eux, il ne
 * reçoit donc pas leur part de tontine.</p>
 */
@SpringBootTest
@Transactional
class LateMemberPresenceFeeIT extends AbstractIntegrationTest {

    @Autowired SessionService                sessionService;
    @Autowired SessionStepService            sessionStepService;
    @Autowired SessionReportRepository       reportRepository;
    @Autowired CreatePresenceTourUseCase     createPresenceTour;
    @Autowired GetPresenceTourSummaryUseCase summary;
    @Autowired ComputePresenceFeeUseCase     computeFee;
    @Autowired CreateMemberUseCase           createMember;
    @Autowired AppUserRepository             appUserRepository;
    @Autowired EntityManager                 em;

    private AppUser secretary;
    private Map<String, Long> members;
    private PresenceTour tour;

    @BeforeEach
    void seed() {
        secretary = appUserRepository.findByLogin("admin").orElseThrow();
        em.createNativeQuery("UPDATE member SET status = 'resigned' WHERE full_name = 'Administrateur'")
            .executeUpdate();

        members = new HashMap<>();
        for (String name : List.of("Ancien1", "Ancien2", "Ancien3")) {
            Long id = ((Number) em.createNativeQuery(
                    "INSERT INTO member (full_name, join_date) VALUES (:n, DATE '2026-01-01') RETURNING id")
                .setParameter("n", name).getSingleResult()).longValue();
            members.put(name, id);
            em.createNativeQuery("INSERT INTO revolving_fund (member_id, balance) VALUES (:m, 5000)")
                .setParameter("m", id).executeUpdate();
        }
        tour = createPresenceTour.execute(LocalDate.of(2026, 6, 1));
        em.flush();
    }

    @Test
    void unNouvelAdherentRejointLeTourEnDernierEtHorsTirage() {
        Member nouveau = createNewMember("Nouveau", LocalDate.of(2026, 6, 20));
        em.flush();
        em.clear();

        List<PresenceTourParticipant> participants = summary.findParticipantsByTourId(tour.getId());
        assertThat(participants).hasSize(4);

        PresenceTourParticipant late = participants.stream()
            .filter(p -> p.getMemberId().equals(nouveau.getId()))
            .findFirst().orElseThrow();
        assertThat(late.isJoinedMidTour()).isTrue();
        // La date retenue est celle de l'entrée dans le tour, pas la date
        // d'adhésion de la fiche : aucune séance n'a encore eu lieu, l'arrivée
        // est donc datée du démarrage du tour.
        assertThat(late.getJoinedAt()).isEqualTo(tour.getStartDate());

        // Hors tirage tant que des membres fondateurs attendent leur tour.
        assertThat(summary.findEligibleBeneficiaries(tour.getId()))
            .extracting(PresenceTourParticipant::getMemberId)
            .doesNotContain(nouveau.getId())
            .containsExactlyInAnyOrder(
                members.get("Ancien1"), members.get("Ancien2"), members.get("Ancien3"));
    }

    @Test
    void lesMembresServisAvantSonArriveeNeLuiDoiventQue1000() {
        // Séance 1 : Ancien1 bénéficie, avant l'arrivée du nouveau.
        runSession(LocalDate.of(2026, 6, 8), members.get("Ancien1"), List.of());

        // Arrivée du nouvel adhérent.
        Member nouveau = createNewMember("Nouveau", LocalDate.of(2026, 6, 20));
        em.flush();

        // Séance 2 : Ancien2 bénéficie ; le nouveau cotise normalement 2 000.
        runSession(LocalDate.of(2026, 6, 22), members.get("Ancien2"), List.of(nouveau.getId()));

        // Séance 3 : Ancien3 bénéficie.
        runSession(LocalDate.of(2026, 6, 29), members.get("Ancien3"), List.of(nouveau.getId()));

        // Le nouveau est désormais le seul à ne pas avoir bénéficié.
        assertThat(summary.findEligibleBeneficiaries(tour.getId()))
            .extracting(PresenceTourParticipant::getMemberId)
            .containsExactly(nouveau.getId());

        // Montants dus quand c'est lui qui bénéficie : Ancien1 a été servi AVANT
        // son arrivée → 1 000 ; Ancien2 et Ancien3 après → 2 000.
        Map<Long, BigDecimal> fees = computeFee.feesByMember(tour.getId(), nouveau.getId());
        assertThat(computeFee.feeFor(fees, members.get("Ancien1"))).isEqualByComparingTo("1000");
        assertThat(computeFee.feeFor(fees, members.get("Ancien2"))).isEqualByComparingTo("2000");
        assertThat(computeFee.feeFor(fees, members.get("Ancien3"))).isEqualByComparingTo("2000");
        assertThat(computeFee.feeFor(fees, nouveau.getId())).isEqualByComparingTo("2000");

        // Séance 4 : le nouveau bénéficie. Ancien1 ne verse que 1 000.
        SessionReportEntity report = runSession(LocalDate.of(2026, 7, 6), nouveau.getId(),
            List.of(nouveau.getId()), Map.of(members.get("Ancien1"), "1000"));

        // Tontine : Ancien2 + Ancien3 + le nouveau = 3 × 1 000. Ancien1 : 0.
        assertThat(report.getPresenceGrossTontine()).isEqualByComparingTo("3000");
        // Boisson et développement restent dus par tout le monde : 4 × 500.
        assertThat(report.getPresenceDevelopmentTotal()).isEqualByComparingTo("2000");
        assertThat(report.getPresenceDefaultCount()).isZero();
        assertThat(report.getPresenceFundCoveredCount()).isZero();
    }

    /**
     * Le cas réel : l'adhésion se fait <strong>pendant</strong> une réunion, et la
     * date d'adhésion saisie est souvent antérieure (rattrapage de dossier).
     *
     * <p>C'est l'entrée dans le tour qui compte, pas la date de la fiche : le
     * bénéficiaire de la séance précédente ne doit que 1 000 FCFA, celui de la
     * séance en cours — servi alors que le nouveau est déjà là — doit 2 000.</p>
     */
    @Test
    void unMembreInscritPendantUneSeanceEntreDansLeTourCeJourLa() {
        // Séance 1 : Ancien1 bénéficie.
        runSession(LocalDate.of(2026, 6, 8), members.get("Ancien1"), List.of());

        // Séance 2 ouverte ; le nouveau est inscrit en pleine réunion, avec une
        // date d'adhésion volontairement ancienne.
        SessionForm form = new SessionForm();
        form.setSessionDate(LocalDate.of(2026, 6, 15));
        MeetingSessionEntity session = sessionService.create(form, secretary);
        em.flush();
        sessionStepService.transitionToNext(session.getId(), secretary, SessionStep.CREATED);

        Member nouveau = createNewMember("Nouveau", LocalDate.of(2026, 1, 3));
        em.flush();
        em.clear();

        PresenceTourParticipant late = summary.findParticipantsByTourId(tour.getId()).stream()
            .filter(p -> p.getMemberId().equals(nouveau.getId()))
            .findFirst().orElseThrow();
        assertThat(late.getJoinedAt()).isEqualTo(LocalDate.of(2026, 6, 15));

        // La séance en cours se termine : Ancien2 bénéficie ce jour-là.
        sessionService.setBeneficiary(session.getId(), members.get("Ancien2"));
        for (Long memberId : allMembers(List.of(nouveau.getId()))) {
            AttendanceEntryForm entry = new AttendanceEntryForm();
            entry.setMemberId(memberId);
            entry.setPresent(true);
            entry.setAmountPaid(new BigDecimal("2000"));
            sessionService.saveAttendanceEntry(session.getId(), entry);
        }
        em.flush();
        sessionStepService.transitionToNext(session.getId(), secretary, SessionStep.PRESENCE_OPEN);
        finish(session.getId());

        runSession(LocalDate.of(2026, 6, 22), members.get("Ancien3"), List.of(nouveau.getId()));

        Map<Long, BigDecimal> fees = computeFee.feesByMember(tour.getId(), nouveau.getId());
        assertThat(computeFee.feeFor(fees, members.get("Ancien1"))).isEqualByComparingTo("1000");
        assertThat(computeFee.feeFor(fees, members.get("Ancien2"))).isEqualByComparingTo("2000");
        assertThat(computeFee.feeFor(fees, members.get("Ancien3"))).isEqualByComparingTo("2000");
    }

    // ── Utilitaires ──────────────────────────────────────────────────

    private Member createNewMember(String name, LocalDate joinDate) {
        MemberRequest request = new MemberRequest();
        request.setFullName(name);
        request.setJoinDate(joinDate);
        request.setResident(true);
        Member created = createMember.execute(request);
        // Frais de fond de roulement acquitté à l'adhésion.
        em.createNativeQuery("UPDATE revolving_fund SET balance = 5000 WHERE member_id = :m")
            .setParameter("m", created.getId()).executeUpdate();
        return created;
    }

    private SessionReportEntity runSession(LocalDate date, Long beneficiaryId, List<Long> extraMembers) {
        return runSession(date, beneficiaryId, extraMembers, Map.of());
    }

    private SessionReportEntity runSession(LocalDate date, Long beneficiaryId,
                                           List<Long> extraMembers, Map<Long, String> overrides) {
        SessionForm form = new SessionForm();
        form.setSessionDate(date);
        MeetingSessionEntity session = sessionService.create(form, secretary);
        em.flush();

        sessionStepService.transitionToNext(session.getId(), secretary, SessionStep.CREATED);
        sessionService.setBeneficiary(session.getId(), beneficiaryId);

        for (Long memberId : allMembers(extraMembers)) {
            AttendanceEntryForm entry = new AttendanceEntryForm();
            entry.setMemberId(memberId);
            entry.setPresent(true);
            entry.setAmountPaid(new BigDecimal(overrides.getOrDefault(memberId, "2000")));
            sessionService.saveAttendanceEntry(session.getId(), entry);
        }
        em.flush();

        sessionStepService.transitionToNext(session.getId(), secretary, SessionStep.PRESENCE_OPEN);
        em.flush();
        em.clear();

        SessionReportEntity report = reportRepository.findBySessionId(session.getId()).orElseThrow();
        finish(session.getId());
        return report;
    }

    /** Mène la séance jusqu'au rapport : une seule séance peut être en cours. */
    private void finish(Long sessionId) {
        for (SessionStep from : List.of(
                SessionStep.PRESENCE_CLOSED, SessionStep.TONTINE_OPEN, SessionStep.TONTINE_CLOSED,
                SessionStep.BANQUE_PROJET_OPEN, SessionStep.BANQUE_PROJET_CLOSED,
                SessionStep.BANQUE_ANNUELLE_OPEN, SessionStep.BANQUE_ANNUELLE_CLOSED)) {
            sessionStepService.transitionToNext(sessionId, secretary, from);
        }
        em.flush();
        em.clear();
    }

    private List<Long> allMembers(List<Long> extra) {
        List<Long> ids = new java.util.ArrayList<>(members.values());
        ids.addAll(extra);
        return ids;
    }
}
