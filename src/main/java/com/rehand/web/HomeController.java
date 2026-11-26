package com.rehand.web;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import com.rehand.service.CategoryService;
import com.rehand.service.ListingService;

/** Home page: the twelve newest available listings and the top-level categories with their counts. */
@Controller
public class HomeController {

    private static final int LATEST_COUNT = 12;

    private final ListingService listingService;
    private final CategoryService categoryService;

    public HomeController(ListingService listingService, CategoryService categoryService) {
        this.listingService = listingService;
        this.categoryService = categoryService;
    }

    @GetMapping("/")
    public String home(Model model) {
        model.addAttribute("latestListings", listingService.latestActive(LATEST_COUNT));
        model.addAttribute("categories", categoryService.tree());
        return "home";
    }
}
