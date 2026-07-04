package org.asvosonk.member.domain.model;

import lombok.*;
import org.asvosonk.member.domain.valueobject.MemberStatus;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Pure domain model for a Member.
 * All JPA concerns are handled by MemberEntity in the infrastructure layer.
 */
@Getter
@EqualsAndHashCode(of = "id")
public class Member {

    private final Long id;
    private String fullName;
    private String phone;
    private LocalDate joinDate;
    private boolean resident;
    private MemberStatus status;
    private final LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public Member(Long id, String fullName, String phone, LocalDate joinDate,
                  boolean resident, MemberStatus status,
                  LocalDateTime createdAt, LocalDateTime updatedAt) {
        this.id = id;
        this.fullName = fullName;
        this.phone = phone;
        this.joinDate = joinDate;
        this.resident = resident;
        this.status = status;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    // ── Business methods ────────────────────────────────────

    public boolean isActive() {
        return status == MemberStatus.active;
    }

    public boolean canReceiveSanction() {
        return isActive();
    }

    public boolean canPayMembership() {
        return isActive();
    }

    public String getStatusLabel() {
        return switch (status) {
            case active    -> "Actif";
            case suspended -> "Suspendu";
            case resigned  -> "Démissionné";
            case deceased  -> "Décédé";
        };
    }

    // ── Mutators ────────────────────────────────────────────

    public void updateProfile(String fullName, String phone, LocalDate joinDate, boolean resident) {
        this.fullName = fullName;
        this.phone = phone;
        this.joinDate = joinDate;
        this.resident = resident;
        this.updatedAt = LocalDateTime.now();
    }

    public void changeStatus(MemberStatus newStatus) {
        this.status = newStatus;
        this.updatedAt = LocalDateTime.now();
    }

    public void suspend() {
        this.status = MemberStatus.suspended;
        this.updatedAt = LocalDateTime.now();
    }

    public void resign() {
        this.status = MemberStatus.resigned;
        this.updatedAt = LocalDateTime.now();
    }
}
