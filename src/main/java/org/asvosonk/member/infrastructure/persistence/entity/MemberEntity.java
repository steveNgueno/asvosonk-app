package org.asvosonk.member.infrastructure.persistence.entity;

import jakarta.persistence.*;
import lombok.*;
import org.asvosonk.member.domain.valueobject.MemberStatus;
import org.asvosonk.security.infrastructure.persistence.entity.AppUserEntity;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "member")
@Getter @Setter
@NoArgsConstructor
@EqualsAndHashCode(of = "id")
public class MemberEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "full_name", nullable = false, length = 150)
    private String fullName;

    @Column(length = 30)
    private String phone;

    @Column(name = "join_date", nullable = false)
    private LocalDate joinDate;

    @Column(name = "is_resident", nullable = false)
    private boolean resident = true;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, columnDefinition = "member_status")
    private MemberStatus status = MemberStatus.active;

    @OneToMany(mappedBy = "member", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private List<MembershipFee> fees = new ArrayList<>();

    @OneToOne(mappedBy = "member")
    private AppUserEntity appUser;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt = LocalDateTime.now();

    @PreUpdate
    public void preUpdate() { this.updatedAt = LocalDateTime.now(); }
}
