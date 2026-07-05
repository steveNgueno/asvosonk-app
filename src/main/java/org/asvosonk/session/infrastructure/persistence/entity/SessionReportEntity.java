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

    @Column(name = "gross_tontine", nullable = false, precision = 12, scale = 2)
    private BigDecimal grossTontine = BigDecimal.ZERO;

    @Column(name = "sanction_deductions", nullable = false, precision = 12, scale = 2)
    private BigDecimal sanctionDeductions = BigDecimal.ZERO;

    @Column(name = "net_tontine", nullable = false, precision = 12, scale = 2)
    private BigDecimal netTontine = BigDecimal.ZERO;

    @Column(name = "total_development", nullable = false, precision = 12, scale = 2)
    private BigDecimal totalDevelopment = BigDecimal.ZERO;

    @Column(name = "total_beverage_pool", nullable = false, precision = 12, scale = 2)
    private BigDecimal totalBeveragePool = BigDecimal.ZERO;

    @Column(name = "actual_beverage_cost", nullable = false, precision = 12, scale = 2)
    private BigDecimal actualBeverageCost = BigDecimal.ZERO;

    @Column(name = "beverage_reliquat", nullable = false, precision = 12, scale = 2)
    private BigDecimal beverageReliquat = BigDecimal.ZERO;

    @Column(name = "total_cotisants", nullable = false)
    private Integer totalCotisants = 0;

    @Column(name = "present_count", nullable = false)
    private Integer presentCount = 0;

    @Column(name = "fund_covered_count", nullable = false)
    private Integer fundCoveredCount = 0;

    @Column(name = "default_count", nullable = false)
    private Integer defaultCount = 0;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();
}
