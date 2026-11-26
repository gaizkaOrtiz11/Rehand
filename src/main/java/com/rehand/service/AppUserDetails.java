package com.rehand.service;

import java.util.Collection;
import java.util.List;
import java.util.Objects;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import com.rehand.domain.Role;
import com.rehand.domain.User;

/**
 * The logged-in principal. Login is by e-mail address, so {@link #getUsername()} returns the e-mail;
 * the name shown to other members is {@link #getDisplayName()}.
 *
 * Controllers receive it with {@code @AuthenticationPrincipal AppUserDetails me} and use {@code me.getId()}.
 * Banned accounts are reported as disabled and locked, so they cannot log in or keep a remember-me session.
 *
 * Display name, city and avatar are copied at login time; {@link #refreshFrom(User)} updates them
 * after the user edits the profile.
 */
public class AppUserDetails implements UserDetails {

    private static final long serialVersionUID = 1L;

    private final Long id;
    private final String email;
    private final String passwordHash;
    private final Role role;
    private final boolean banned;
    private String displayName;
    private String city;
    private String avatarPath;

    public AppUserDetails(User user) {
        this.id = user.getId();
        this.email = user.getEmail();
        this.passwordHash = user.getPasswordHash();
        this.role = user.getRole();
        this.banned = user.isBanned();
        this.displayName = user.getUsername();
        this.city = user.getCity();
        this.avatarPath = user.getAvatarPath();
    }

    /** Copies the editable profile fields (username, city, avatar) from the entity. */
    public void refreshFrom(User user) {
        this.displayName = user.getUsername();
        this.city = user.getCity();
        this.avatarPath = user.getAvatarPath();
    }

    public Long getId() {
        return id;
    }

    /** The username members see (not the login name). */
    public String getDisplayName() {
        return displayName;
    }

    public String getCity() {
        return city;
    }

    public Role getRole() {
        return role;
    }

    public String getAvatarPath() {
        return avatarPath;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority("ROLE_" + role.name()));
    }

    @Override
    public String getPassword() {
        return passwordHash;
    }

    /** The e-mail address: it is what the login form submits. */
    @Override
    public String getUsername() {
        return email;
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return !banned;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return !banned;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof AppUserDetails other)) {
            return false;
        }
        return Objects.equals(email, other.email);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(email);
    }
}
