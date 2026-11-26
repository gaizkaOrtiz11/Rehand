package com.rehand.web;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;

import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.rehand.domain.User;
import com.rehand.dto.RegisterForm;
import com.rehand.exception.BusinessRuleException;
import com.rehand.service.AppUserDetails;
import com.rehand.service.UserService;

/**
 * Registration and the login page. The POST /login request itself is handled by Spring Security
 * (see SecurityConfig), which reads the fields {@code email}, {@code password} and {@code remember-me}.
 */
@Controller
public class AuthController {

    private final UserService userService;
    private final SecurityContextRepository securityContextRepository = new HttpSessionSecurityContextRepository();

    public AuthController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping("/login")
    public String login(@AuthenticationPrincipal AppUserDetails me) {
        if (me != null) {
            return "redirect:/";
        }
        return "login";
    }

    @GetMapping("/register")
    public String registerForm(@AuthenticationPrincipal AppUserDetails me, Model model) {
        if (me != null) {
            return "redirect:/";
        }
        model.addAttribute("registerForm", new RegisterForm());
        return "register";
    }

    @PostMapping("/register")
    public String register(@Valid @ModelAttribute("registerForm") RegisterForm form,
                           BindingResult result,
                           @AuthenticationPrincipal AppUserDetails me,
                           HttpServletRequest request,
                           HttpServletResponse response,
                           RedirectAttributes redirect) {
        if (me != null) {
            return "redirect:/";
        }
        if (form.getEmail() != null && !result.hasFieldErrors("email") && userService.isEmailTaken(form.getEmail())) {
            result.rejectValue("email", "email.taken", "That email address is already registered. Try logging in instead.");
        }
        if (form.getUsername() != null && !result.hasFieldErrors("username")
                && userService.isUsernameTaken(form.getUsername(), null)) {
            result.rejectValue("username", "username.taken", "That username is already taken. Please choose another.");
        }
        if (result.hasErrors()) {
            return "register";
        }

        User user;
        try {
            user = userService.register(form);
        } catch (BusinessRuleException e) {
            result.reject("register.failed", e.getMessage());
            return "register";
        }

        logIn(user, request, response);
        redirect.addFlashAttribute("flashSuccess",
                "Welcome to Rehand, " + user.getUsername() + ". Your account and your wallet are ready.");
        return "redirect:/profile";
    }

    /**
     * Signs the new user in without asking for the password again: the authentication is stored in the
     * security context and the context in the HTTP session, exactly what the login filter does.
     * The session id is changed first so an id known before login cannot be reused afterwards.
     */
    private void logIn(User user, HttpServletRequest request, HttpServletResponse response) {
        if (request.getSession(false) != null) {
            request.changeSessionId();
        }
        AppUserDetails principal = new AppUserDetails(user);
        UsernamePasswordAuthenticationToken authentication =
                UsernamePasswordAuthenticationToken.authenticated(principal, null, principal.getAuthorities());
        authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authentication);
        SecurityContextHolder.setContext(context);
        securityContextRepository.saveContext(context, request, response);
    }
}
