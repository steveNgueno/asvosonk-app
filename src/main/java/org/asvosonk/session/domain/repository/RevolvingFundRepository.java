package org.asvosonk.session.domain.repository;

import org.asvosonk.session.domain.model.RevolvingFund;

import java.util.Optional;

public interface RevolvingFundRepository {

    Optional<RevolvingFund> findByMemberId(Long memberId);

    RevolvingFund save(RevolvingFund fund);
}
