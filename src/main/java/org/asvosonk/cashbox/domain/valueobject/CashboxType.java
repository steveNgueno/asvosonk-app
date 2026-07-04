package org.asvosonk.cashbox.domain.valueobject;

public enum CashboxType {
    development,
    sanction,
    beverage,
    bank;

    public String label() {
        return switch (this) {
            case development -> "Caisse Développement";
            case sanction    -> "Caisse Sanction";
            case beverage    -> "Caisse Boisson";
            case bank        -> "Caisse Banque";
        };
    }
}
