package org.asvosonk.session.infrastructure.persistence.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "session_report")
@Getter
@Setter
@NoArgsConstructor
@EqualsAndHashCode(of = "id")
public class SessionReportEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "session_id", nullable = false, unique = true)
    private Long sessionId;

    // ── Présence ─────────────────────────────────────────────

    @Column(name = "presence_beneficiary_id")
    private Long presenceBeneficiaryId;

    @Column(name = "presence_total_cotisants", nullable = false)
    private Integer presenceTotalCotisants = 0;

    @Column(name = "presence_present_count", nullable = false)
    private Integer presencePresentCount = 0;

    @Column(name = "presence_fund_covered_count", nullable = false)
    private Integer presenceFundCoveredCount = 0;

    @Column(name = "presence_default_count", nullable = false)
    private Integer presenceDefaultCount = 0;

    @Column(name = "presence_gross_tontine", nullable = false, precision = 12, scale = 2)
    private BigDecimal presenceGrossTontine = BigDecimal.ZERO;

    /**
     * Part de la tontine prélevée d'office pour remettre le bénéficiaire à jour
     * vis-à-vis de son fond de roulement (avances, échecs, cotisation du jour).
     */
    @Column(name = "presence_fund_catch_up", nullable = false, precision = 12, scale = 2)
    private BigDecimal presenceFundCatchUp = BigDecimal.ZERO;

    @Column(name = "presence_sanction_deductions", nullable = false, precision = 12, scale = 2)
    private BigDecimal presenceSanctionDeductions = BigDecimal.ZERO;

    @Column(name = "presence_net_tontine", nullable = false, precision = 12, scale = 2)
    private BigDecimal presenceNetTontine = BigDecimal.ZERO;

    @Column(name = "presence_development_total", nullable = false, precision = 12, scale = 2)
    private BigDecimal presenceDevelopmentTotal = BigDecimal.ZERO;

    @Column(name = "presence_beverage_reliquat", nullable = false, precision = 12, scale = 2)
    private BigDecimal presenceBeverageReliquat = BigDecimal.ZERO;

    /** Argent revenu dans les fonds de roulement : exclu du total remis au trésorier. */
    @Column(name = "presence_return_to_fund", nullable = false, precision = 12, scale = 2)
    private BigDecimal presenceReturnToFund = BigDecimal.ZERO;

    /** Rattrapages dus aux bénéficiaires des séances dont un échec a été recouvert. */
    @Column(name = "presence_recovery_total", nullable = false, precision = 12, scale = 2)
    private BigDecimal presenceRecoveryTotal = BigDecimal.ZERO;

    // ── Grande Tontine ───────────────────────────────────────

    @Column(name = "tontine_beneficiary_id")
    private Long tontineBeneficiaryId;

    @Column(name = "tontine_gross_collected", nullable = false, precision = 12, scale = 2)
    private BigDecimal tontineGrossCollected = BigDecimal.ZERO;

    @Column(name = "tontine_sanction_deductions", nullable = false, precision = 12, scale = 2)
    private BigDecimal tontineSanctionDeductions = BigDecimal.ZERO;

    @Column(name = "tontine_net_paid", nullable = false, precision = 12, scale = 2)
    private BigDecimal tontineNetPaid = BigDecimal.ZERO;

    // ── Banques (saisie non encore implémentée) ──────────────

    @Column(name = "banque_projet_collected", nullable = false, precision = 12, scale = 2)
    private BigDecimal banqueProjetCollected = BigDecimal.ZERO;

    @Column(name = "banque_annuelle_savings", nullable = false, precision = 12, scale = 2)
    private BigDecimal banqueAnnuelleSavings = BigDecimal.ZERO;

    @Column(name = "banque_annuelle_repayments", nullable = false, precision = 12, scale = 2)
    private BigDecimal banqueAnnuelleRepayments = BigDecimal.ZERO;

    // ── Entrées et sorties de caisse de la séance ────────────

    @Column(name = "sanctions_collected", nullable = false, precision = 12, scale = 2)
    private BigDecimal sanctionsCollected = BigDecimal.ZERO;

    /** Frais d'adhésion encaissés en séance : remis au trésorier, hors caisse. */
    @Column(name = "membership_fees_collected", nullable = false, precision = 12, scale = 2)
    private BigDecimal membershipFeesCollected = BigDecimal.ZERO;

    /** Entrées diverses saisies pendant la séance (dons, remboursements ponctuels…). */
    @Column(name = "other_income", nullable = false, precision = 12, scale = 2)
    private BigDecimal otherIncome = BigDecimal.ZERO;

    @Column(name = "total_outflow", nullable = false, precision = 12, scale = 2)
    private BigDecimal totalOutflow = BigDecimal.ZERO;

    // ── Synthèse ─────────────────────────────────────────────

    /**
     * Argent effectivement remis au trésorier : entrées de caisse de la séance
     * moins ses sorties, <strong>plancher à zéro</strong>. N'inclut ni les
     * tontines (remises en main propre aux bénéficiaires) ni le retour dans les
     * fonds de roulement.
     */
    @Column(name = "total_to_treasurer", nullable = false, precision = 12, scale = 2)
    private BigDecimal totalToTreasurer = BigDecimal.ZERO;

    /**
     * Cas inverse : les sorties de la séance ont dépassé ses entrées. Le
     * complément a dû être prélevé sur le solde déjà en caisse — le secrétaire ne
     * remet rien au trésorier, il lui en redemande.
     */
    @Column(name = "total_from_cashboxes", nullable = false, precision = 12, scale = 2)
    private BigDecimal totalFromCashboxes = BigDecimal.ZERO;

    @Column(name = "generated_at", nullable = false, updatable = false)
    private LocalDateTime generatedAt = LocalDateTime.now();
}
