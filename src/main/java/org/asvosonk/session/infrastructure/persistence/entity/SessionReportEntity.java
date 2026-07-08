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

    @Column(name = "presence_sanction_deductions", nullable = false, precision = 12, scale = 2)
    private BigDecimal presenceSanctionDeductions = BigDecimal.ZERO;

    @Column(name = "presence_net_tontine", nullable = false, precision = 12, scale = 2)
    private BigDecimal presenceNetTontine = BigDecimal.ZERO;

    @Column(name = "presence_development_total", nullable = false, precision = 12, scale = 2)
    private BigDecimal presenceDevelopmentTotal = BigDecimal.ZERO;

    @Column(name = "presence_beverage_reliquat", nullable = false, precision = 12, scale = 2)
    private BigDecimal presenceBeverageReliquat = BigDecimal.ZERO;

    // ── Grande Tontine ───────────────────────────────────────

    @Column(name = "tontine_beneficiary_id")
    private Long tontineBeneficiaryId;

    @Column(name = "tontine_gross_collected", nullable = false, precision = 12, scale = 2)
    private BigDecimal tontineGrossCollected = BigDecimal.ZERO;

    @Column(name = "tontine_sanction_deductions", nullable = false, precision = 12, scale = 2)
    private BigDecimal tontineSanctionDeductions = BigDecimal.ZERO;

    @Column(name = "tontine_net_paid", nullable = false, precision = 12, scale = 2)
    private BigDecimal tontineNetPaid = BigDecimal.ZERO;

    // ── Banque Projet ────────────────────────────────────────

    @Column(name = "banque_projet_collected", nullable = false, precision = 12, scale = 2)
    private BigDecimal banqueProjetCollected = BigDecimal.ZERO;

    // ── Banque Annuelle ──────────────────────────────────────

    @Column(name = "banque_annuelle_savings", nullable = false, precision = 12, scale = 2)
    private BigDecimal banqueAnnuelleSavings = BigDecimal.ZERO;

    @Column(name = "banque_annuelle_repayments", nullable = false, precision = 12, scale = 2)
    private BigDecimal banqueAnnuelleRepayments = BigDecimal.ZERO;

    // ── Synthèse ─────────────────────────────────────────────

    @Column(name = "total_to_treasurer", nullable = false, precision = 12, scale = 2)
    private BigDecimal totalToTreasurer = BigDecimal.ZERO;

    @Column(name = "generated_at", nullable = false, updatable = false)
    private LocalDateTime generatedAt = LocalDateTime.now();
}
