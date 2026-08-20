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
import java.util.Objects;

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
        model.addAttribute("modules", List.of("presence", "grand_tontine", "sanction",
            "cashbox_development", "cashbox_sanction", "cashbox_beverage", "cashbox_bank"));
        model.addAttribute("members", searchMemberUseCase.findAll());
        model.addAttribute("query", query);
        model.addAttribute("selectedModule", module);
        model.addAttribute("selectedMemberId", memberId);

        List<GlobalMovementView> movements = List.of();

        if (query != null && !query.isBlank()) {
            // Search members by name / phone
            List<Member> memberResults = searchMemberUseCase.search(query);
            model.addAttribute("memberResults", memberResults);

            // Search movements across all modules via global_movement_view.
            // A purely numeric query is also treated as a member id so the
            // member's own movements surface.
            Long searchMemberId = null;
            try {
                searchMemberId = Long.parseLong(query.trim());
            } catch (NumberFormatException ignored) {
                // not an id — keyword search only
            }

            movements = globalMovementViewRepository.searchByKeyword(
                searchMemberId, "%" + escapeLike(query) + "%");
            model.addAttribute("searched", true);

        } else if (module != null || memberId != null) {
            // Filter mode: movements matching the selected module / member
            movements = globalMovementViewRepository.search(memberId, module, null, null);
            model.addAttribute("searched", true);
        }

        model.addAttribute("movements", movements);
        model.addAttribute("movementMemberNames", searchMemberUseCase.findNamesByIds(
            movements.stream().map(GlobalMovementView::getMemberId)
                .filter(Objects::nonNull).distinct().toList()));

        return "search/index";
    }

    /**
     * Neutralises LIKE wildcards typed by the user, so a query containing
     * "%" or "_" matches those characters literally instead of everything.
     */
    private String escapeLike(String raw) {
        return raw.trim().replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }
}
