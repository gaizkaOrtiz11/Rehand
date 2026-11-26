package com.rehand.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.test.util.ReflectionTestUtils;

import com.rehand.domain.Role;
import com.rehand.domain.User;
import com.rehand.domain.UserStatus;
import com.rehand.repository.UserRepository;

@ExtendWith(MockitoExtension.class)
class AppUserDetailsTest {

    @Mock
    private UserRepository userRepository;

    private static User user(Role role, UserStatus status) {
        User user = new User();
        ReflectionTestUtils.setField(user, "id", 5L);
        user.setEmail("ausra@rehand.test");
        user.setUsername("ausra_k");
        user.setPasswordHash("$2b$10$hash");
        user.setCity("Kaunas");
        user.setAvatarPath("avatars/a.png");
        user.setRole(role);
        user.setStatus(status);
        return user;
    }

    @Test
    void exposesTheFieldsOtherClassesRelyOn() {
        AppUserDetails details = new AppUserDetails(user(Role.MEMBER, UserStatus.ACTIVE));

        assertThat(details.getId()).isEqualTo(5L);
        assertThat(details.getUsername()).isEqualTo("ausra@rehand.test");
        assertThat(details.getDisplayName()).isEqualTo("ausra_k");
        assertThat(details.getCity()).isEqualTo("Kaunas");
        assertThat(details.getRole()).isEqualTo(Role.MEMBER);
        assertThat(details.getAvatarPath()).isEqualTo("avatars/a.png");
        assertThat(details.getPassword()).isEqualTo("$2b$10$hash");
    }

    @Test
    void memberAndAdminGetTheirRoleAuthority() {
        assertThat(authorityNames(new AppUserDetails(user(Role.MEMBER, UserStatus.ACTIVE)))).containsExactly("ROLE_MEMBER");
        assertThat(authorityNames(new AppUserDetails(user(Role.ADMIN, UserStatus.ACTIVE)))).containsExactly("ROLE_ADMIN");
    }

    private static List<String> authorityNames(AppUserDetails details) {
        return details.getAuthorities().stream().map(GrantedAuthority::getAuthority).toList();
    }

    @Test
    void activeAccountCanLogIn() {
        AppUserDetails details = new AppUserDetails(user(Role.MEMBER, UserStatus.ACTIVE));

        assertThat(details.isEnabled()).isTrue();
        assertThat(details.isAccountNonLocked()).isTrue();
        assertThat(details.isAccountNonExpired()).isTrue();
        assertThat(details.isCredentialsNonExpired()).isTrue();
    }

    @Test
    void bannedAccountIsDisabledAndLocked() {
        AppUserDetails details = new AppUserDetails(user(Role.MEMBER, UserStatus.BANNED));

        assertThat(details.isEnabled()).isFalse();
        assertThat(details.isAccountNonLocked()).isFalse();
    }

    @Test
    void refreshCopiesTheEditableProfileFields() {
        AppUserDetails details = new AppUserDetails(user(Role.MEMBER, UserStatus.ACTIVE));
        User changed = user(Role.MEMBER, UserStatus.ACTIVE);
        changed.setUsername("ausra.new");
        changed.setCity("Vilnius");
        changed.setAvatarPath(null);

        details.refreshFrom(changed);

        assertThat(details.getDisplayName()).isEqualTo("ausra.new");
        assertThat(details.getCity()).isEqualTo("Vilnius");
        assertThat(details.getAvatarPath()).isNull();
        assertThat(details.getUsername()).isEqualTo("ausra@rehand.test");
    }

    @Test
    void serviceLoadsByEmailIgnoringCaseAndTrimmingSpaces() {
        when(userRepository.findByEmailIgnoreCase("Ausra@Rehand.test"))
                .thenReturn(Optional.of(user(Role.MEMBER, UserStatus.ACTIVE)));
        AppUserDetailsService service = new AppUserDetailsService(userRepository);

        UserDetails loaded = service.loadUserByUsername("  Ausra@Rehand.test ");

        assertThat(loaded).isInstanceOf(AppUserDetails.class);
        assertThat(loaded.getUsername()).isEqualTo("ausra@rehand.test");
    }

    @Test
    void serviceRejectsAnUnknownEmail() {
        when(userRepository.findByEmailIgnoreCase("nobody@rehand.test")).thenReturn(Optional.empty());
        AppUserDetailsService service = new AppUserDetailsService(userRepository);

        assertThatThrownBy(() -> service.loadUserByUsername("nobody@rehand.test"))
                .isInstanceOf(UsernameNotFoundException.class);
    }
}
