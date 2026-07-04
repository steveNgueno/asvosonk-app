package org.asvosonk.member.presentation.response;

import lombok.*;
import org.asvosonk.member.domain.model.Member;
import org.asvosonk.member.domain.valueobject.MemberStatus;

import java.time.LocalDate;

@Getter
@AllArgsConstructor
public class MemberResponse {

    private final Long id;
    private final String fullName;
    private final String phone;
    private final LocalDate joinDate;
    private final boolean resident;
    private final MemberStatus status;
    private final String statusLabel;

    public static MemberResponse fromDomain(Member member) {
        return new MemberResponse(
            member.getId(),
            member.getFullName(),
            member.getPhone(),
            member.getJoinDate(),
            member.isResident(),
            member.getStatus(),
            member.getStatusLabel()
        );
    }
}
