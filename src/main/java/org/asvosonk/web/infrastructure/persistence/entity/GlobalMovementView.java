package org.asvosonk.web.infrastructure.persistence.entity;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * DTO representing a row from the global_movement_view SQL view.
 * Not a JPA entity because the view's reference_id is not unique across UNION ALL modules.
 */
public class GlobalMovementView {

    private Long referenceId;
    private String module;
    private Long memberId;
    private LocalDate eventDate;
    private BigDecimal amount;
    private String status;

    public GlobalMovementView() {}

    public GlobalMovementView(Long referenceId, String module, Long memberId,
                              LocalDate eventDate, BigDecimal amount, String status) {
        this.referenceId = referenceId;
        this.module = module;
        this.memberId = memberId;
        this.eventDate = eventDate;
        this.amount = amount;
        this.status = status;
    }

    // ── Getters ─────────────────────────────────────────────

    public Long getReferenceId() { return referenceId; }
    public String getModule() { return module; }
    public Long getMemberId() { return memberId; }
    public LocalDate getEventDate() { return eventDate; }
    public BigDecimal getAmount() { return amount; }
    public String getStatus() { return status; }

    // ── Setters ─────────────────────────────────────────────

    public void setReferenceId(Long referenceId) { this.referenceId = referenceId; }
    public void setModule(String module) { this.module = module; }
    public void setMemberId(Long memberId) { this.memberId = memberId; }
    public void setEventDate(LocalDate eventDate) { this.eventDate = eventDate; }
    public void setAmount(BigDecimal amount) { this.amount = amount; }
    public void setStatus(String status) { this.status = status; }

    // ── Derived helpers for templates ───────────────────────

    public String getModuleLabel() {
        if (module == null) return "";
        return switch (module) {
            case "presence" -> "Présence";
            case "grand_tontine" -> "Gde Tontine";
            case "sanction" -> "Sanction";
            case "cashbox_development" -> "Caisse Dév.";
            case "cashbox_sanction" -> "Caisse Sanction";
            case "cashbox_beverage" -> "Caisse Boisson";
            case "cashbox_bank" -> "Caisse Banque";
            default -> module;
        };
    }

    public String getModuleBadgeClass() {
        if (module == null) return "bg-secondary";
        return switch (module) {
            case "presence" -> "bg-success";
            case "grand_tontine" -> "bg-warning text-dark";
            case "sanction" -> "bg-danger";
            case "cashbox_development", "cashbox_sanction", "cashbox_beverage", "cashbox_bank" -> "bg-info text-dark";
            default -> "bg-secondary";
        };
    }
}
