package com.rehand.web;

import java.nio.charset.StandardCharsets;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.util.UriComponentsBuilder;

import com.rehand.service.CategoryService;

/**
 * Pretty category URLs. {@code /categories/shoes} redirects to {@code /listings?category=shoes},
 * so category pages share the filters, sorting and pagination of the search page.
 */
@Controller
public class CategoryController {

    private final CategoryService categoryService;

    public CategoryController(CategoryService categoryService) {
        this.categoryService = categoryService;
    }

    @GetMapping("/categories")
    public String all() {
        return "redirect:/listings";
    }

    @GetMapping("/categories/{slug}")
    public String show(@PathVariable String slug) {
        categoryService.requireBySlug(slug);
        String url = UriComponentsBuilder.fromPath("/listings")
                .queryParam("category", slug)
                .build()
                .encode(StandardCharsets.UTF_8)
                .toUriString();
        return "redirect:" + url;
    }
}
