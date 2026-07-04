package org.asvosonk.member.domain.valueobject;

public enum FeeType {
    registration,       // 2 500 FCFA
    revolving_fund,     // 5 000 FCFA
    emergency_fund,     // 20 000 FCFA
    development_fund;   // 12 500 FCFA

    public java.math.BigDecimal defaultAmount() {
        return switch (this) {
            case registration    -> new java.math.BigDecimal("2500");
            case revolving_fund  -> new java.math.BigDecimal("5000");
            case emergency_fund  -> new java.math.BigDecimal("20000");
            case development_fund-> new java.math.BigDecimal("12500");
        };
    }

    public String label() {
        return switch (this) {
            case registration    -> "Inscription";
            case revolving_fund  -> "Fond de roulement";
            case emergency_fund  -> "Fond de secours";
            case development_fund-> "Fond de développement";
        };
    }
}
