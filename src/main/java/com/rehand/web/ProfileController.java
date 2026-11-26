package com.rehand.web;

import jakarta.validation.Valid;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.rehand.domain.User;
import com.rehand.dto.PasswordForm;
import com.rehand.dto.ProfileForm;
import com.rehand.exception.BusinessRuleException;
import com.rehand.service.AppUserDetails;
import com.rehand.service.UserService;

/**
 * The logged-in member's own profile: details, edit form, avatar and password.
 * Every POST redirects afterwards (post/redirect/get) and reports the outcome as a flash message.
 * All routes need a login (SecurityConfig: anyRequest().authenticated()).
 */
@Controller
public class ProfileController {

    private static final String FLASH_SUCCESS = "flashSuccess";
    private static final String FLASH_ERROR = "flashError";

    private final UserService userService;

    public ProfileController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping("/profile")
    public String view(@AuthenticationPrincipal AppUserDetails me, Model model) {
        model.addAttribute("profileUser", userService.getById(me.getId()));
        model.addAttribute("balance", userService.getWalletBalance(me.getId()));
        return "profile/view";
    }

    // ------------------------------------------------------------ edit details

    @GetMapping("/profile/edit")
    public String editForm(@AuthenticationPrincipal AppUserDetails me, Model model) {
        User user = userService.getById(me.getId());
        model.addAttribute("profileUser", user);
        model.addAttribute("profileForm", new ProfileForm(user.getUsername(), user.getCity(), user.getBio()));
        return "profile/edit";
    }

    @PostMapping("/profile/edit")
    public String edit(@AuthenticationPrincipal AppUserDetails me,
                       @Valid @ModelAttribute("profileForm") ProfileForm form,
                       BindingResult result,
                       Model model,
                       RedirectAttributes redirect) {
        if (form.getUsername() != null && !result.hasFieldErrors("username")
                && userService.isUsernameTaken(form.getUsername(), me.getId())) {
            result.rejectValue("username", "username.taken", "That username is already taken. Please choose another.");
        }
        if (!result.hasErrors()) {
            try {
                User saved = userService.updateProfile(me.getId(), form);
                me.refreshFrom(saved);
                redirect.addFlashAttribute(FLASH_SUCCESS, "Your profile has been updated.");
                return "redirect:/profile";
            } catch (BusinessRuleException e) {
                result.reject("profile.failed", e.getMessage());
            }
        }
        model.addAttribute("profileUser", userService.getById(me.getId()));
        return "profile/edit";
    }

    // ------------------------------------------------------------------ avatar

    @PostMapping("/profile/avatar")
    public String uploadAvatar(@AuthenticationPrincipal AppUserDetails me,
                               @RequestParam(name = "avatar", required = false) MultipartFile avatar,
                               RedirectAttributes redirect) {
        try {
            User saved = userService.changeAvatar(me.getId(), avatar);
            me.refreshFrom(saved);
            redirect.addFlashAttribute(FLASH_SUCCESS, "Your photo has been updated.");
        } catch (BusinessRuleException e) {
            redirect.addFlashAttribute(FLASH_ERROR, e.getMessage());
        }
        return "redirect:/profile/edit";
    }

    @PostMapping("/profile/avatar/remove")
    public String removeAvatar(@AuthenticationPrincipal AppUserDetails me, RedirectAttributes redirect) {
        User saved = userService.removeAvatar(me.getId());
        me.refreshFrom(saved);
        redirect.addFlashAttribute(FLASH_SUCCESS, "Your photo has been removed.");
        return "redirect:/profile/edit";
    }

    // ---------------------------------------------------------------- password

    @GetMapping("/profile/password")
    public String passwordForm(Model model) {
        model.addAttribute("passwordForm", new PasswordForm());
        return "profile/password";
    }

    @PostMapping("/profile/password")
    public String changePassword(@AuthenticationPrincipal AppUserDetails me,
                                 @Valid @ModelAttribute("passwordForm") PasswordForm form,
                                 BindingResult result,
                                 RedirectAttributes redirect) {
        if (result.hasErrors()) {
            return "profile/password";
        }
        try {
            userService.changePassword(me.getId(), form.getCurrentPassword(), form.getNewPassword());
        } catch (BusinessRuleException e) {
            result.reject("password.failed", e.getMessage());
            return "profile/password";
        }
        redirect.addFlashAttribute(FLASH_SUCCESS, "Your password has been changed.");
        return "redirect:/profile";
    }
}
