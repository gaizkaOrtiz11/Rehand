package com.rehand.web;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import jakarta.validation.Valid;

import org.springframework.beans.propertyeditors.StringTrimmerEditor;
import org.springframework.context.MessageSource;
import org.springframework.data.domain.Page;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.WebDataBinder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.InitBinder;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.rehand.domain.ItemCondition;
import com.rehand.domain.Listing;
import com.rehand.domain.ListingStatus;
import com.rehand.domain.Role;
import com.rehand.dto.CategoryContext;
import com.rehand.dto.CategoryGroup;
import com.rehand.dto.CategoryNode;
import com.rehand.dto.ListingDetail;
import com.rehand.dto.ListingForm;
import com.rehand.dto.ListingSearchCriteria;
import com.rehand.dto.PageNav;
import com.rehand.exception.BusinessRuleException;
import com.rehand.service.AppUserDetails;
import com.rehand.service.CategoryService;
import com.rehand.service.ListingService;
import com.rehand.service.OfferService;

/**
 * Browse, search, detail, publish, edit, status change, delete and photo management.
 * Reading routes are public (see SecurityConfig); everything that changes data needs a login and
 * is re-checked in {@link ListingService} (owner or admin). Every POST redirects afterwards.
 */
@Controller
public class ListingController {

    private static final String FLASH_SUCCESS = "flashSuccess";

    private final ListingService listingService;
    private final CategoryService categoryService;
    private final MessageSource messages;
    private final OfferService offerService;

    public ListingController(ListingService listingService, CategoryService categoryService, MessageSource messages,
                             OfferService offerService) {
        this.listingService = listingService;
        this.categoryService = categoryService;
        this.messages = messages;
        this.offerService = offerService;
    }

    /** Blank text fields become null, so optional fields (brand, size, color) are stored as NULL. */
    @InitBinder
    void trimStrings(WebDataBinder binder) {
        binder.registerCustomEditor(String.class, new StringTrimmerEditor(true));
    }

    // ================================================================ browse and search

    @GetMapping("/listings")
    public String search(@ModelAttribute("criteria") ListingSearchCriteria criteria,
                         BindingResult ignoredBindingErrors,
                         Model model, Locale locale) {
        List<CategoryNode> tree = categoryService.tree();
        CategoryContext ctx = CategoryContext.of(tree, criteria.getCategory());

        Page<Listing> page = listingService.search(criteria, ctx.getCategoryIds());
        if (page.getTotalPages() > 0 && criteria.getPageIndex() >= page.getTotalPages()) {
            criteria.setPage(page.getTotalPages());
            page = listingService.search(criteria, ctx.getCategoryIds());
        }

        String categoryLabel = ctx.getSelected() == null ? null : ctx.getSelected().getName();
        String queryWithoutCategory = criteria.queryString("category", null);

        model.addAttribute("page", page);
        model.addAttribute("pageWindow", PageNav.window(page.getNumber() + 1, page.getTotalPages()));
        model.addAttribute("pageBase", "/listings?" + criteria.pageLinkPrefix());
        model.addAttribute("railBase", "/listings?" + (queryWithoutCategory.isEmpty() ? "" : queryWithoutCategory + "&") + "category=");
        model.addAttribute("ctx", ctx);
        model.addAttribute("filterGroups", CategoryGroup.forFilter(tree));
        model.addAttribute("chips", criteria.activeChips(categoryLabel, c -> conditionLabel(c, locale)));
        model.addAttribute("hiddenParams", criteria.paramsWithout("sort"));
        model.addAttribute("cities", listingService.cities());
        model.addAttribute("conditions", ItemCondition.values());
        model.addAttribute("sortOptions", ListingSearchCriteria.SORT_OPTIONS);
        return "listings/search";
    }

    // ================================================================ detail

    @GetMapping("/listings/{id:\\d+}")
    public String detail(@PathVariable Long id, @AuthenticationPrincipal AppUserDetails me, Model model) {
        ListingDetail detail = listingService.getDetail(id, actorId(me), isAdmin(me));
        model.addAttribute("detail", detail);
        model.addAttribute("offerPanel", offerService.panel(detail.getListing(), actorId(me)));
        return "listings/detail";
    }

    // ================================================================ publish

    @GetMapping("/listings/new")
    public String newForm(@AuthenticationPrincipal AppUserDetails me, Model model, Locale locale) {
        ListingForm form = new ListingForm();
        form.setCity(me.getCity());
        model.addAttribute("listingForm", form);
        prepareForm(model, null, locale);
        return "listings/form";
    }

    @PostMapping("/listings/new")
    public String create(@Valid @ModelAttribute("listingForm") ListingForm form,
                         BindingResult binding,
                         @RequestParam(name = "photos", required = false) List<MultipartFile> photos,
                         @AuthenticationPrincipal AppUserDetails me,
                         Model model, RedirectAttributes redirect, Locale locale) {
        if (!binding.hasErrors()) {
            try {
                Listing created = listingService.create(form, photos, me.getId());
                redirect.addFlashAttribute(FLASH_SUCCESS, "Your listing is live.");
                return "redirect:/listings/" + created.getId();
            } catch (BusinessRuleException e) {
                binding.reject("listing.rule", e.getMessage());
            }
        }
        prepareForm(model, null, locale);
        return "listings/form";
    }

    // ================================================================ edit

    @GetMapping("/listings/{id:\\d+}/edit")
    public String editForm(@PathVariable Long id, @AuthenticationPrincipal AppUserDetails me,
                           Model model, Locale locale) {
        Listing listing = listingService.getForEdit(id, actorId(me), isAdmin(me));
        model.addAttribute("listingForm", ListingForm.from(listing));
        prepareForm(model, listing, locale);
        return "listings/form";
    }

    @PostMapping("/listings/{id:\\d+}/edit")
    public String update(@PathVariable Long id,
                         @Valid @ModelAttribute("listingForm") ListingForm form,
                         BindingResult binding,
                         @RequestParam(name = "photos", required = false) List<MultipartFile> photos,
                         @AuthenticationPrincipal AppUserDetails me,
                         Model model, RedirectAttributes redirect, Locale locale) {
        if (!binding.hasErrors()) {
            try {
                listingService.update(id, form, photos, actorId(me), isAdmin(me));
                redirect.addFlashAttribute(FLASH_SUCCESS, "Your changes are saved.");
                return "redirect:/listings/" + id;
            } catch (BusinessRuleException e) {
                binding.reject("listing.rule", e.getMessage());
            }
        }
        Listing listing = listingService.getForEdit(id, actorId(me), isAdmin(me));
        prepareForm(model, listing, locale);
        return "listings/form";
    }

    // ================================================================ status and delete

    @PostMapping("/listings/{id:\\d+}/status")
    public String changeStatus(@PathVariable Long id,
                               @RequestParam("status") ListingStatus status,
                               @RequestParam(name = "from", required = false) String from,
                               @AuthenticationPrincipal AppUserDetails me,
                               RedirectAttributes redirect) {
        listingService.changeStatus(id, status, actorId(me), isAdmin(me));
        redirect.addFlashAttribute(FLASH_SUCCESS, status == ListingStatus.HIDDEN
                ? "The listing is hidden. It no longer shows up in search."
                : "The listing is available again.");
        return "mine".equals(from) ? "redirect:/my/listings" : "redirect:/listings/" + id;
    }

    @PostMapping("/listings/{id:\\d+}/delete")
    public String delete(@PathVariable Long id, @AuthenticationPrincipal AppUserDetails me,
                         RedirectAttributes redirect) {
        listingService.delete(id, actorId(me), isAdmin(me));
        redirect.addFlashAttribute(FLASH_SUCCESS, "The listing was deleted.");
        return "redirect:/my/listings";
    }

    // ================================================================ photos

    @PostMapping("/listings/{id:\\d+}/photos/{photoId:\\d+}/delete")
    public String deletePhoto(@PathVariable Long id, @PathVariable Long photoId,
                              @AuthenticationPrincipal AppUserDetails me, RedirectAttributes redirect) {
        listingService.removePhoto(id, photoId, actorId(me), isAdmin(me));
        redirect.addFlashAttribute(FLASH_SUCCESS, "Photo removed.");
        return "redirect:/listings/" + id + "/edit";
    }

    @PostMapping("/listings/{id:\\d+}/photos/{photoId:\\d+}/cover")
    public String makeCover(@PathVariable Long id, @PathVariable Long photoId,
                            @AuthenticationPrincipal AppUserDetails me, RedirectAttributes redirect) {
        listingService.makeCover(id, photoId, actorId(me), isAdmin(me));
        redirect.addFlashAttribute(FLASH_SUCCESS, "The cover photo was changed.");
        return "redirect:/listings/" + id + "/edit";
    }

    // ================================================================ helpers

    /** Model attributes shared by the create and the edit page. {@code listing} is null when creating. */
    private void prepareForm(Model model, Listing listing, Locale locale) {
        Map<String, String> conditionOptions = new LinkedHashMap<>();
        for (ItemCondition c : ItemCondition.values()) {
            conditionOptions.put(c.name(), conditionLabel(c, locale));
        }
        model.addAttribute("conditionOptions", conditionOptions);
        model.addAttribute("categoryGroups", CategoryGroup.forListingForm(categoryService.tree()));
        model.addAttribute("listing", listing);
        model.addAttribute("editing", listing != null);
        model.addAttribute("maxPhotos", Listing.MAX_PHOTOS);
    }

    private String conditionLabel(ItemCondition condition, Locale locale) {
        return messages.getMessage("condition." + condition.name(), null, condition.name(), locale);
    }

    static Long actorId(AppUserDetails me) {
        return me == null ? null : me.getId();
    }

    static boolean isAdmin(AppUserDetails me) {
        return me != null && me.getRole() == Role.ADMIN;
    }
}
