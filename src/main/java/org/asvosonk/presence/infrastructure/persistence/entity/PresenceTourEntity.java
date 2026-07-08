package org.asvosonk.presence.infrastructure.persistence.entity;

import jakarta.persistence.*;
import lombok.*;
import org.asvosonk.presence.domain.valueobject.PresenceTourStatus;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "presence_tour")
@Getter @Setter
@NoArgsConstructor
@EqualsAndHashCode(of = "id")
public class PresenceTourEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Column(name = "end_date")
    private LocalDate endDate;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(nullable = false, columnDefinition = "presence_tour_status")
    private PresenceTourStatus status = PresenceTourStatus.open;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @PrePersist
    public void prePersist() { this.createdAt = LocalDateTime.now(); }
}
