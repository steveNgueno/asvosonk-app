package org.asvosonk.web.presentation.controller;

import lombok.RequiredArgsConstructor;
import org.asvosonk.member.application.usecase.SearchMemberUseCase;
import org.asvosonk.member.domain.model.Member;
import org.asvosonk.web.infrastructure.persistence.entity.GlobalMovementView;
import org.asvosonk.web.infrastructure.persistence.repository.GlobalMovementViewRepository;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Controller
@RequiredArgsConstructor
public class SearchController {

    private final SearchMemberUseCase        searchMemberUseCase;
    private final GlobalMovementViewRepository globalMovementViewRepository;

    @GetMapping("/search")
    @PreAuthorize("hasAuthority('SEARCH_GLOBAL')")
    public String searchPage(@RequestParam(value = "q", required = false) String query,
                             @RequestParam(value = "module", required = false) String module,
                             @RequestParam(value = "memberId", required = false) Long memberId,
                             Model model) {
        model.addAttribute("pageTitle", "Recherche globale");
        model.addAttribute("modules", List.of("presence", "grand_tontine", "sanction", "cashbox_development", "cashbox_sanction", "cashbox_beverage", "cashbox_bank"));

        if (query != null && !query.isBlank()) {
            // Search members
            List<Member> memberResults = searchMemberUseCase.search(query);
            model.addAttribute("memberResults", memberResults);

            // Search movements across all modules via global_movement_view
            // Try to parse query as member ID for cross-reference
            Long searchMemberId = null;
            try {
                searchMemberId = Long.parseLong(query.trim());
            } catch (NumberFormatException ignored) {}

            List<GlobalMovementView> movements = globalMovementViewRepository.searchByKeyword(
                searchMemberId, "%" + query + "%");
            model.addAttribute("movements", movements);
            model.addAttribute("query", query);
        } else if (module != null || memberId != null) {
            // Filter mode: search movements with specific filters
            List<GlobalMovementView> movements = globalMovementViewRepository.search(
                memberId, module, null, null);
            model.addAttribute("movements", movements);
        }

        return "search/index";
    }
}
