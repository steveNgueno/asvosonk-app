package org.asvosonk.tontine.application.usecase;

import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import lombok.RequiredArgsConstructor;
import org.asvosonk.common.domain.exception.BusinessRuleException;
import org.asvosonk.common.domain.exception.ResourceNotFoundException;
import org.asvosonk.common.presentation.validation.FcfaAmounts;
import org.asvosonk.sanction.application.usecase.CreateSanctionUseCase;
import org.asvosonk.sanction.domain.repository.SanctionRepository;
import org.asvosonk.sanction.domain.valueobject.SanctionOrigin;
import org.asvosonk.session.domain.valueobject.SessionStep;
import org.asvosonk.session.infrastructure.persistence.entity.MeetingSessionEntity;
import org.asvosonk.tontine.domain.model.TontineContribution;
import org.asvosonk.tontine.domain.model.TontineDebt;
import org.asvosonk.tontine.domain.model.TontineParticipant;
import org.asvosonk.tontine.domain.model.TontineTour;
import org.asvosonk.tontine.domain.repository.TontineContributionRepository;
import org.asvosonk.tontine.domain.repository.TontineDebtRepository;
import org.asvosonk.tontine.domain.repository.TontineParticipantRepository;
import org.asvosonk.tontine.domain.repository.TontineTourRepository;
import org.asvosonk.tontine.domain.valueobject.DebtStatus;
import org.asvosonk.tontine.domain.valueobject.PaymentStatus;
import org.asvosonk.tontine.infrastructure.persistence.entity.TontineRecoveryEntity;
import org.asvosonk.tontine.infrastructure.persistence.repository.TontineRecoveryRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class RecordTontineContributionUseCase {

    private static final BigDecimal MIN_CONTRIBUTION = new BigDecimal("5000");
    private static final BigDecimal DEFAULT_FINE_NOT_BENEFITED = new BigDecimal("2000");
    private static final BigDecimal DEFAULT_FINE_BENEFITED = new BigDecimal("5000");

    private final TontineTourRepository tourRepository;
    private final TontineParticipantRepository participantRepository;
    private final TontineContributionRepository contributionRepository;
    private final TontineDebtRepository debtRepository;
    private final TontineRecoveryRepository recoveryRepository;
    private final SanctionRepository sanctionRepository;
    private final CreateSanctionUseCase createSanctionUseCase;
    private final EntityManager entityManager;

    /**
     * Échec de cotisation encore à recouvrir.
     *
     * @param imposedAmount   somme exacte due si le membre avait une dette envers
     *                        ce bénéficiaire, {@code null} si le montant est libre
     *                        (multiple de 5 000 FCFA)
     * @param recordedAmount  recouvrement déjà saisi pendant la séance en cours,
     *                        {@code null} sinon
     */
    public record PendingFailure(Long failedContributionId,
                                 Long contributorId,
                                 Long failedSessionId,
                                 LocalDate failedSessionDate,
                                 Long beneficiaryId,
                                 BigDecimal imposedAmount,
                                 BigDecimal recordedAmount) {

        public boolean isRecordedInSession() {
            return recordedAmount != null;
        }
    }

    /** Recouvrement versé pendant une séance, pour son détail et son rapport. */
    public record RecoveryLine(Long contributorId,
                               Long beneficiaryId,
                               LocalDate failedSessionDate,
                               BigDecimal amount) { }

    // ── Saisie en séance ─────────────────────────────────────

    /**
     * Enregistre une cotisation saisie en séance (formulaire unitaire).
     *
     * <p>Contrairement à {@link #execute}, qui applique les seules règles de la
     * tontine, ce point d'entrée vérifie aussi le contexte de la séance : voir
     * {@link #requireSessionAcceptsContribution}.</p>
     */
    @Transactional
    public TontineContribution recordInSession(Long tourId, Long sessionId, Long contributorId,
                                               Long beneficiaryId, BigDecimal amount) {
        FcfaAmounts.requireValid(amount);
        requireSessionAcceptsContribution(tourId, sessionId, beneficiaryId);
        requireFailuresRecovered(tourId, sessionId, contributorId, amount);
        return execute(tourId, sessionId, contributorId, beneficiaryId, amount);
    }

    /** Ligne de la feuille de cotisation saisie en séance — voir {@link #recordInSession}. */
    @Transactional
    public TontineContribution replaceInSession(Long tourId, Long sessionId, Long contributorId,
                                                Long beneficiaryId, BigDecimal amount) {
        FcfaAmounts.requireValid(amount);
        requireSessionAcceptsContribution(tourId, sessionId, beneficiaryId);
        requireFailuresRecovered(tourId, sessionId, contributorId, amount);
        return replaceContribution(tourId, sessionId, contributorId, beneficiaryId, amount);
    }

    /**
     * Une ligne complète de la feuille, enregistrée d'un bloc : d'abord les
     * recouvrements des échecs passés, ensuite la cotisation du jour (qui exige
     * que ces échecs soient recouverts). Si une partie est refusée, rien n'est
     * enregistré pour ce membre.
     *
     * @param amount     cotisation du jour, {@code null} si la case est vide
     * @param recoveries montant saisi par échec à recouvrir (identifiant de
     *                   l'échec → montant ; 0 retire un recouvrement saisi)
     */
    @Transactional
    public void recordSheetLine(Long tourId, Long sessionId, Long contributorId, Long beneficiaryId,
                                BigDecimal amount, Map<Long, BigDecimal> recoveries) {
        for (Map.Entry<Long, BigDecimal> recovery : recoveries.entrySet()) {
            replaceRecoveryInSession(tourId, sessionId, contributorId, recovery.getKey(), recovery.getValue());
        }
        if (amount != null) {
            replaceInSession(tourId, sessionId, contributorId, beneficiaryId, amount);
        }
    }

    /**
     * Une cotisation ne s'enregistre que pendant la rubrique Grande Tontine de la
     * séance, et au profit du bénéficiaire prévu.
     *
     * <p>La clôture de la rubrique totalise les cotisations de la séance et fige
     * la somme remise au bénéficiaire dans le rapport : une cotisation acceptée
     * avant l'ouverture ou après la clôture faussait ce total. Le bénéficiaire,
     * lui, était lu dans un champ caché du formulaire sans être revérifié — un
     * envoi forgé pouvait verser la tontine à un autre participant.</p>
     *
     * <p>La séance est verrouillée comme lors d'une transition d'étape, pour
     * qu'une saisie ne puisse pas croiser la clôture.</p>
     */
    private void requireSessionAcceptsContribution(Long tourId, Long sessionId, Long beneficiaryId) {
        requireTontineOpen(sessionId);

        Long expected = expectedBeneficiary(tourId, sessionId);
        if (expected == null || !expected.equals(beneficiaryId)) {
            throw new BusinessRuleException(
                "Le bénéficiaire indiqué n'est pas celui de la séance : la tontine revient au "
                    + "prochain participant dans l'ordre de passage.");
        }
    }

    private MeetingSessionEntity requireTontineOpen(Long sessionId) {
        MeetingSessionEntity session = sessionId == null ? null
            : entityManager.find(MeetingSessionEntity.class, sessionId, LockModeType.PESSIMISTIC_WRITE);
        if (session == null) {
            throw new ResourceNotFoundException("Séance", sessionId);
        }
        if (!session.isStepExactly(SessionStep.TONTINE_OPEN)) {
            throw new BusinessRuleException(
                "La Grande Tontine de cette séance n'est pas ouverte à la saisie (étape courante : "
                    + session.getCurrentStepEnum().label() + ").");
        }
        return session;
    }

    /**
     * Bénéficiaire de la séance : celui des cotisations déjà saisies s'il y en a
     * (la feuille garde son bénéficiaire même si un échec réordonne le tour en
     * cours de saisie), sinon le premier participant non servi dans l'ordre de
     * passage — celui que propose l'écran de séance.
     */
    @Transactional(readOnly = true)
    public Long expectedBeneficiary(Long tourId, Long sessionId) {
        return contributionRepository.findByTourIdAndSessionId(tourId, sessionId).stream()
            .map(TontineContribution::getBeneficiaryId)
            .findFirst()
            .orElseGet(() -> participantRepository.findByTourIdOrderByDrawOrder(tourId).stream()
                .filter(p -> !p.isHasBenefited())
                .map(TontineParticipant::getMemberId)
                .findFirst()
                .orElse(null));
    }

    // ── Recouvrement des échecs ──────────────────────────────

    /**
     * Un membre qui a fait un échec doit d'abord le recouvrir — verser au
     * bénéficiaire de cette séance-là — avant de cotiser au bénéficiaire du jour.
     * Un nouvel échec (montant 0) reste, lui, toujours enregistrable.
     */
    private void requireFailuresRecovered(Long tourId, Long sessionId, Long contributorId, BigDecimal amount) {
        if (amount == null || amount.signum() == 0) {
            return;
        }
        pendingFailures(tourId, sessionId, contributorId).stream()
            .filter(f -> !f.isRecordedInSession())
            .findFirst()
            .ifPresent(f -> {
                throw new BusinessRuleException(
                    "Recouvrez d'abord l'échec de la séance du " + f.failedSessionDate()
                        + " avant de cotiser au bénéficiaire du jour.");
            });
    }

    /**
     * Échecs d'un membre restant à recouvrir dans ce tour, du plus ancien au plus
     * récent. Ceux qui ont été recouverts pendant la séance en cours y figurent
     * encore, avec le montant saisi, pour rester modifiables jusqu'à la clôture.
     * Une cotisation à 0 du bénéficiaire pour lui-même n'est pas un échec.
     */
    @Transactional(readOnly = true)
    public List<PendingFailure> pendingFailures(Long tourId, Long currentSessionId, Long contributorId) {
        return pendingFailuresByMember(tourId, currentSessionId).getOrDefault(contributorId, List.of());
    }

    @Transactional(readOnly = true)
    public Map<Long, List<PendingFailure>> pendingFailuresByMember(Long tourId, Long currentSessionId) {
        Map<Long, TontineRecoveryEntity> recoveryByFailure = recoveryRepository.findByTourId(tourId).stream()
            .collect(Collectors.toMap(TontineRecoveryEntity::getFailedContributionId, r -> r));

        return contributionRepository.findByTourIdOrderByCreatedAtDesc(tourId).stream()
            .filter(TontineContribution::isDefault)
            .filter(c -> !c.getContributorId().equals(c.getBeneficiaryId()))
            .filter(c -> !c.getSessionId().equals(currentSessionId))
            .filter(c -> {
                TontineRecoveryEntity recovery = recoveryByFailure.get(c.getId());
                return recovery == null || recovery.getSessionId().equals(currentSessionId);
            })
            .map(c -> {
                TontineRecoveryEntity recovery = recoveryByFailure.get(c.getId());
                BigDecimal imposed = recovery != null && recovery.getSessionId().equals(currentSessionId)
                    ? imposedRecoveryAmount(tourId, c, currentSessionId)
                    : debtRepository.findByTourIdAndDebtorIdAndCreditorIdAndStatus(
                            tourId, c.getContributorId(), c.getBeneficiaryId(), DebtStatus.owed)
                        .map(TontineDebt::getAmount).orElse(null);
                return new PendingFailure(c.getId(), c.getContributorId(), c.getSessionId(),
                    sessionDate(c.getSessionId()),
                    c.getBeneficiaryId(), imposed, recovery != null ? recovery.getAmount() : null);
            })
            .sorted(Comparator.comparing(PendingFailure::failedSessionDate))
            .collect(Collectors.groupingBy(PendingFailure::contributorId,
                LinkedHashMap::new, Collectors.toList()));
    }

    /** Vrai tant qu'un échec du tour n'a pas été recouvert. */
    @Transactional(readOnly = true)
    public boolean hasUnrecoveredFailures(Long tourId) {
        return !pendingFailuresByMember(tourId, null).isEmpty();
    }

    /**
     * Enregistre (ou remplace) le recouvrement d'un échec pendant la séance en
     * cours. Le montant suit les règles d'une cotisation : la somme exacte si le
     * membre devait de l'argent à ce bénéficiaire, sinon un multiple de 5 000 FCFA
     * non nul. 0 retire un recouvrement saisi par erreur.
     */
    @Transactional
    public void replaceRecoveryInSession(Long tourId, Long sessionId, Long contributorId,
                                         Long failedContributionId, BigDecimal amount) {
        FcfaAmounts.requireValid(amount);
        requireTontineOpen(sessionId);

        TontineContribution failure = contributionRepository.findById(failedContributionId)
            .filter(TontineContribution::isDefault)
            .filter(c -> c.getTourId().equals(tourId) && c.getContributorId().equals(contributorId))
            .orElseThrow(() -> new BusinessRuleException("Échec à recouvrir introuvable pour ce membre."));
        if (failure.getSessionId().equals(sessionId)) {
            throw new BusinessRuleException("Un échec ne se recouvre qu'à une séance suivante.");
        }

        Optional<TontineRecoveryEntity> existing = recoveryRepository.findByFailedContributionId(failedContributionId);
        if (existing.isPresent()) {
            TontineRecoveryEntity old = existing.get();
            if (!old.getSessionId().equals(sessionId)) {
                throw new BusinessRuleException("Cet échec a déjà été recouvert lors d'une séance précédente.");
            }
            reversePaymentEffects(tourId, sessionId, old.getContributorId(), old.getBeneficiaryId());
            recoveryRepository.delete(old);
            recoveryRepository.flush();
        }

        if (amount == null || amount.signum() == 0) {
            return;
        }
        applyPaymentEffects(tourId, sessionId, contributorId, failure.getBeneficiaryId(), amount);

        TontineRecoveryEntity recovery = new TontineRecoveryEntity();
        recovery.setTourId(tourId);
        recovery.setFailedContributionId(failedContributionId);
        recovery.setSessionId(sessionId);
        recovery.setContributorId(contributorId);
        recovery.setBeneficiaryId(failure.getBeneficiaryId());
        recovery.setAmount(amount);
        recoveryRepository.save(recovery);
    }

    /** Recouvrements versés pendant une séance. */
    @Transactional(readOnly = true)
    public List<RecoveryLine> recoveriesDuring(Long sessionId) {
        return recoveryRepository.findBySessionIdOrderByIdAsc(sessionId).stream()
            .map(r -> new RecoveryLine(r.getContributorId(), r.getBeneficiaryId(),
                contributionRepository.findById(r.getFailedContributionId())
                    .map(c -> sessionDate(c.getSessionId())).orElse(null),
                r.getAmount()))
            .toList();
    }

    // ── Conséquences des échecs, à la clôture de la rubrique ─

    /**
     * Applique les conséquences des échecs définitifs de la séance : sanction
     * (2 000 FCFA et dernière place pour un membre qui n'a pas encore bénéficié,
     * 5 000 FCFA sinon).
     *
     * <p>Elles ne sont plus appliquées à la saisie : une erreur corrigée avant la
     * clôture ne doit entraîner ni sanction ni changement de position. Appelée à
     * la clôture, avant que le bénéficiaire du jour ne soit marqué servi.
     * Idempotente : un échec déjà sanctionné ne l'est pas deux fois.</p>
     */
    @Transactional
    public void applyDefaultConsequences(Long tourId, Long sessionId, LocalDate sessionDate) {
        Map<Long, TontineParticipant> participants = participantRepository.findByTourIdOrderByDrawOrder(tourId)
            .stream()
            .collect(Collectors.toMap(TontineParticipant::getMemberId, p -> p, (a, b) -> a, LinkedHashMap::new));

        List<TontineContribution> failures = contributionRepository.findByTourIdAndSessionId(tourId, sessionId)
            .stream()
            .filter(TontineContribution::isDefault)
            .filter(c -> !c.getContributorId().equals(c.getBeneficiaryId()))
            .sorted(Comparator.comparingInt(c -> participants.containsKey(c.getContributorId())
                ? participants.get(c.getContributorId()).getDrawOrder() : Integer.MAX_VALUE))
            .toList();

        for (TontineContribution failure : failures) {
            TontineParticipant contributor = participants.get(failure.getContributorId());
            if (contributor == null
                    || sanctionRepository.findByOriginAndReferenceId(SanctionOrigin.tontine_default, failure.getId())
                        .isPresent()) {
                continue;
            }
            boolean notBenefited = !contributor.isHasBenefited();
            createSanctionUseCase.execute(
                contributor.getMemberId(),
                sessionDate,
                notBenefited ? DEFAULT_FINE_NOT_BENEFITED : DEFAULT_FINE_BENEFITED,
                "Défaut de cotisation Grande Tontine — Tour #" + tourId + " — "
                    + (notBenefited ? "Non bénéficié" : "Bénéficié"),
                SanctionOrigin.tontine_default,
                failure.getId());

            // Un membre pas encore servi passe en fin de classement.
            if (notBenefited) {
                int maxOrder = participantRepository.findByTourIdOrderByDrawOrder(tourId).stream()
                    .mapToInt(TontineParticipant::getDrawOrder)
                    .max()
                    .orElse(0);
                contributor.reassignDrawOrder(maxOrder + 1);
                participantRepository.save(contributor);
            }
        }
    }

    // ── Règles de la tontine ─────────────────────────────────

    /**
     * Record a grand tontine contribution.
     *
     * @param tourId        the active tour ID
     * @param sessionId     the session ID (meeting session)
     * @param contributorId the member contributing
     * @param beneficiaryId the member benefiting this session
     * @param amount        contribution amount (0 = default, otherwise multiple of 5000)
     * @return the recorded contribution
     */
    @Transactional
    public TontineContribution execute(Long tourId, Long sessionId, Long contributorId,
                                       Long beneficiaryId, BigDecimal amount) {
        // ── Preconditions ────────────────────────────────────
        // F-34 — locked so two concurrent submissions for this tour can't both
        // record conflicting lines for the same tour at once.
        TontineTour tour = tourRepository.findByIdForUpdate(tourId)
            .orElseThrow(() -> new ResourceNotFoundException("Tour de grande tontine", tourId));

        if (!tour.isOpen()) {
            throw new BusinessRuleException("Le tour de grande tontine est clôturé.");
        }

        // Le bénéficiaire du jour cotise lui aussi à sa propre tontine : son
        // versement grossit la somme qu'il perçoit, mais ne crée évidemment
        // aucune dette (personne ne la lui doit en retour) — voir plus bas.

        // Both must be participants
        participantRepository.findByTourIdAndMemberId(tourId, contributorId)
            .orElseThrow(() -> new BusinessRuleException(
                "Le cotisant n'est pas participant de ce tour."));
        participantRepository.findByTourIdAndMemberId(tourId, beneficiaryId)
            .orElseThrow(() -> new BusinessRuleException(
                "Le bénéficiaire n'est pas participant de ce tour."));

        // Amount validation
        if (amount.compareTo(BigDecimal.ZERO) < 0) {
            throw new BusinessRuleException("Le montant ne peut pas être négatif.");
        }

        // F-49 : idempotence anti double-clic. La table impose UNIQUE(tour, session,
        // contributor, beneficiary) ; sans détection préalable, un second envoi lèverait
        // une violation d'unicité SQL exposée brute à l'utilisateur. On la traduit en
        // message métier clair et on refuse le doublon.
        boolean alreadyRecorded = contributionRepository
            .findByTourIdAndSessionId(tourId, sessionId).stream()
            .anyMatch(c -> contributorId.equals(c.getContributorId())
                        && beneficiaryId.equals(c.getBeneficiaryId()));
        if (alreadyRecorded) {
            throw new BusinessRuleException(
                "Cette cotisation a déjà été enregistrée pour cette séance.");
        }

        // ── Échec (montant 0) ────────────────────────────────
        // Seule la ligne est enregistrée : la sanction et le recul dans l'ordre de
        // passage ne s'appliquent qu'à la clôture (applyDefaultConsequences), pour
        // qu'une erreur corrigée avant n'ait aucune conséquence. Aucune dette n'est
        // créée ni soldée : l'échec devra être recouvert à une séance suivante.
        if (amount.compareTo(BigDecimal.ZERO) == 0) {
            return contributionRepository.save(new TontineContribution(
                null, tourId, sessionId, contributorId, beneficiaryId,
                BigDecimal.ZERO, PaymentStatus.default_status, LocalDateTime.now()));
        }

        applyPaymentEffects(tourId, sessionId, contributorId, beneficiaryId, amount);
        return contributionRepository.save(new TontineContribution(
            null, tourId, sessionId, contributorId, beneficiaryId,
            amount, PaymentStatus.paid, LocalDateTime.now()));
    }

    /**
     * Effets d'un versement de {@code contributorId} à {@code beneficiaryId} :
     * remboursement exact d'une dette s'il en existe une, sinon cotisation libre
     * (multiple de 5 000 FCFA) qui crée la dette inverse.
     */
    private void applyPaymentEffects(Long tourId, Long sessionId, Long contributorId,
                                     Long beneficiaryId, BigDecimal amount) {
        // F-07 — Debt orientation invariant:
        //   A debt is stored as debtor = the member who RECEIVED (beneficiary),
        //   creditor = the member who GAVE (contributor).
        //
        // To SETTLE a debt, the current contributor must be its DEBTOR: in a
        // later session they pay back the member who previously funded them.
        //   e.g. S1 benef=A, B pays  → debt (debtor=A, creditor=B)
        //        S2 benef=B, A pays  → settles that debt: debtor=A, creditor=B
        Optional<TontineDebt> existingDebt = debtRepository
            .findByTourIdAndDebtorIdAndCreditorIdAndStatus(
                tourId, contributorId, beneficiaryId, DebtStatus.owed);

        // ── Remboursement (dette existante) ──────────────────
        if (existingDebt.isPresent()) {
            TontineDebt debt = existingDebt.get();
            if (amount.compareTo(debt.getAmount()) != 0) {
                throw new BusinessRuleException(
                    "Vous devez rembourser exactement " + debt.getAmount().toBigInteger()
                        + " FCFA (montant de la dette envers ce bénéficiaire).");
            }
            debt.markAsRepaid(sessionId);
            debtRepository.save(debt);
            return;
        }

        // ── Cotisation libre (pas de dette) ──────────────────
        if (amount.compareTo(MIN_CONTRIBUTION) < 0) {
            throw new BusinessRuleException(
                "La cotisation minimale est de " + MIN_CONTRIBUTION + " FCFA.");
        }
        if (amount.remainder(MIN_CONTRIBUTION).compareTo(BigDecimal.ZERO) != 0) {
            throw new BusinessRuleException(
                "Le montant doit être un multiple de " + MIN_CONTRIBUTION + " FCFA.");
        }

        // Le bénéficiaire cotise pour lui-même : la somme entre dans sa tontine,
        // mais aucune dette n'est créée — il ne se doit rien à lui-même.
        if (!contributorId.equals(beneficiaryId)) {
            debtRepository.save(new TontineDebt(
                null, tourId, beneficiaryId, contributorId,
                amount, sessionId, DebtStatus.owed, null,
                LocalDateTime.now(), LocalDateTime.now()));
        }
    }

    /**
     * Replace an existing contribution for the same (tour, session, contributor).
     * Deletes the old contribution and reverses its side effects before recording
     * the new one. This allows the sheet to be edited multiple times before closing.
     */
    @Transactional
    public TontineContribution replaceContribution(Long tourId, Long sessionId, Long contributorId,
                                                    Long beneficiaryId, BigDecimal amount) {
        Optional<TontineContribution> existing = contributionRepository
            .findByTourIdAndSessionId(tourId, sessionId).stream()
            .filter(c -> contributorId.equals(c.getContributorId()))
            .findFirst();

        if (existing.isPresent()) {
            TontineContribution old = existing.get();
            if (old.isDefault()) {
                // Lignes enregistrées avant que les sanctions ne soient reportées
                // à la clôture : la sanction créée à la saisie disparaît avec elle.
                sanctionRepository.findByOriginAndReferenceId(SanctionOrigin.tontine_default, old.getId())
                    .ifPresent(sanction -> sanctionRepository.deleteById(sanction.getId()));
            } else {
                reversePaymentEffects(tourId, sessionId, old.getContributorId(), old.getBeneficiaryId());
            }
            contributionRepository.deleteById(old.getId());
        }

        return execute(tourId, sessionId, contributorId, beneficiaryId, amount);
    }

    /**
     * Annule les effets d'un versement fait pendant cette séance : la dette qu'il
     * avait soldée redevient due, ou celle qu'il avait créée disparaît.
     */
    private void reversePaymentEffects(Long tourId, Long sessionId, Long contributorId, Long beneficiaryId) {
        if (contributorId.equals(beneficiaryId)) {
            return;
        }
        debtRepository.findByTourIdAndDebtorIdAndCreditorIdAndStatus(
                tourId, contributorId, beneficiaryId, DebtStatus.repaid)
            .filter(debt -> sessionId.equals(debt.getRepaymentSessionId()))
            .ifPresent(debt -> {
                debt.markAsUnrepaid();
                debtRepository.save(debt);
            });

        debtRepository.findByTourIdAndDebtorId(tourId, beneficiaryId).stream()
            .filter(d -> contributorId.equals(d.getCreditorId())
                      && sessionId.equals(d.getOriginSessionId())
                      && d.isOwed())
            .findFirst()
            .ifPresent(d -> debtRepository.deleteById(d.getId()));
    }

    // ── Helpers ──────────────────────────────────────────────

    /**
     * Montant imposé d'un recouvrement déjà saisi dans la séance : la dette qu'il
     * a soldée (repassée « remboursée » par ce recouvrement), sinon libre.
     */
    private BigDecimal imposedRecoveryAmount(Long tourId, TontineContribution failure, Long sessionId) {
        return debtRepository.findByTourIdAndDebtorIdAndCreditorIdAndStatus(
                tourId, failure.getContributorId(), failure.getBeneficiaryId(), DebtStatus.repaid)
            .filter(debt -> sessionId.equals(debt.getRepaymentSessionId()))
            .map(TontineDebt::getAmount)
            .orElse(null);
    }

    private LocalDate sessionDate(Long sessionId) {
        MeetingSessionEntity session = entityManager.find(MeetingSessionEntity.class, sessionId);
        return session != null ? session.getSessionDate() : null;
    }
}
