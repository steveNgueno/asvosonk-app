package org.asvosonk.cashbox.infrastructure.persistence.entity;

import jakarta.persistence.*;
import lombok.*;
import org.asvosonk.cashbox.domain.valueobject.CashboxType;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "cashbox")
@Getter @Setter
@NoArgsConstructor
@EqualsAndHashCode(of = "id")
public class CashboxEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, unique = true, columnDefinition = "cashbox_type")
    private CashboxType type;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal balance = BigDecimal.ZERO;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt = LocalDateTime.now();

    @PreUpdate
    public void preUpdate() { this.updatedAt = LocalDateTime.now(); }
}
