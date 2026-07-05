package org.asvosonk.web.presentation.controller;

import org.asvosonk.member.application.usecase.SearchMemberUseCase;
import org.asvosonk.member.domain.model.Member;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

@Controller
public class SearchController {

    private final SearchMemberUseCase searchMemberUseCase;

    public SearchController(SearchMemberUseCase searchMemberUseCase) {
        this.searchMemberUseCase = searchMemberUseCase;
    }

    @GetMapping("/search")
    @PreAuthorize("hasAuthority('SEARCH_GLOBAL')")
    public String searchPage(@RequestParam(value = "q", required = false) String query,
                             Model model) {
        model.addAttribute("pageTitle", "Recherche globale");

        if (query != null && !query.isBlank()) {
            List<Member> results = searchMemberUseCase.search(query);
            model.addAttribute("results", results);
            model.addAttribute("query", query);
        }

        return "search/index";
    }
}
