package org.asvosonk.session.infrastructure.persistence.entity;

import jakarta.persistence.*;
import lombok.*;
import org.asvosonk.member.infrastructure.persistence.entity.MemberEntity;
import org.asvosonk.security.infrastructure.persistence.entity.AppUserEntity;
import org.asvosonk.session.domain.valueobject.SessionStatus;
import org.asvosonk.session.domain.valueobject.SessionStep;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "meeting_session")
@Getter @Setter
@NoArgsConstructor
@EqualsAndHashCode(of = "id")
public class MeetingSessionEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "session_date", nullable = false, unique = true)
    private LocalDate sessionDate;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(nullable = false, columnDefinition = "session_status")
    private SessionStatus status = SessionStatus.planned;

    @Column
    private String agenda;

    @Column(name = "current_step", nullable = false)
    private String currentStep = SessionStep.CREATED.name();

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "presence_beneficiary_id")
    private MemberEntity presenceBeneficiary;

    @Column(name = "presence_closed_at")
    private LocalDateTime presenceClosedAt;

    @Column(name = "tontine_closed_at")
    private LocalDateTime tontineClosedAt;

    @Column(name = "report_generated_at")
    private LocalDateTime reportGeneratedAt;

    @Column(name = "closed_at")
    private LocalDateTime closedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by")
    private AppUserEntity createdBy;

    @OneToMany(mappedBy = "session", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private List<SessionAttendanceEntity> attendances = new ArrayList<>();

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt = LocalDateTime.now();

    @PreUpdate
    public void preUpdate() { this.updatedAt = LocalDateTime.now(); }

    public boolean isClosed()  { return status == SessionStatus.closed;  }
    public boolean isOpen()    { return status == SessionStatus.open;    }
    public boolean isPlanned() { return status == SessionStatus.planned; }

    // ── Step helpers ──────────────────────────────────────────

    public SessionStep getCurrentStepEnum() {
        return SessionStep.valueOf(currentStep);
    }

    public void setCurrentStepEnum(SessionStep step) {
        this.currentStep = step.name();
    }

    public boolean isStepAtLeast(SessionStep step) {
        return getCurrentStepEnum().ordinal() >= step.ordinal();
    }

    public boolean isStepExactly(SessionStep step) {
        return getCurrentStepEnum() == step;
    }
}
