package com.rehand.web;

import java.util.ArrayList;
import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.rehand.domain.Listing;
import com.rehand.service.ListingService;

/**
 * JSON for the search-box suggestions (progressive enhancement, see static/js/catalog.js).
 * {@code GET /api/search/suggest?q=ja} returns at most six {@code {"id":1,"title":"..."}} objects
 * for ACTIVE listings whose title contains the text; fewer than two characters give an empty array.
 */
@RestController
@RequestMapping("/api/search")
public class SearchApiController {

    /** One suggestion in the JSON array. */
    public record Suggestion(Long id, String title) {
    }

    private final ListingService listingService;

    public SearchApiController(ListingService listingService) {
        this.listingService = listingService;
    }

    @GetMapping("/suggest")
    public List<Suggestion> suggest(@RequestParam(name = "q", defaultValue = "") String q) {
        List<Suggestion> out = new ArrayList<>();
        for (Listing l : listingService.suggest(q)) {
            out.add(new Suggestion(l.getId(), l.getTitle()));
        }
        return out;
    }
}
