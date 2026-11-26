package com.rehand.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import com.rehand.domain.Role;
import com.rehand.domain.User;
import com.rehand.domain.UserStatus;
import com.rehand.domain.Wallet;
import com.rehand.dto.ProfileForm;
import com.rehand.dto.RegisterForm;
import com.rehand.exception.BusinessRuleException;
import com.rehand.exception.NotFoundException;
import com.rehand.repository.UserRepository;
import com.rehand.repository.WalletRepository;

/** Pure unit test: repositories and the file store are mocks, the password encoder is the real BCrypt. */
@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private WalletRepository walletRepository;

    @Mock
    private FileStorageService fileStorageService;

    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder(4);

    private UserService service;

    @BeforeEach
    void setUp() {
        service = new UserService(userRepository, walletRepository, passwordEncoder, fileStorageService);
    }

    private static RegisterForm registerForm(String email, String username, String password) {
        RegisterForm form = new RegisterForm();
        form.setEmail(email);
        form.setUsername(username);
        form.setPassword(password);
        form.setConfirmPassword(password);
        form.setCity("Kaunas");
        form.setAcceptTerms(true);
        return form;
    }

    private User existingUser(long id, String username, String rawPassword) {
        User user = new User();
        ReflectionTestUtils.setField(user, "id", id);
        user.setEmail(username + "@rehand.test");
        user.setUsername(username);
        user.setPasswordHash(passwordEncoder.encode(rawPassword));
        return user;
    }

    // ------------------------------------------------------------ registration

    @Test
    void registerCreatesActiveMemberWithHashedPasswordAndEmptyWallet() {
        when(userRepository.existsByEmailIgnoreCase("gaizka@example.com")).thenReturn(false);
        when(userRepository.existsByUsernameIgnoreCase("gaizka")).thenReturn(false);
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        User saved = service.register(registerForm("  Gaizka@Example.com ", " gaizka ", "Secret123"));

        assertThat(saved.getEmail()).isEqualTo("gaizka@example.com");
        assertThat(saved.getUsername()).isEqualTo("gaizka");
        assertThat(saved.getCity()).isEqualTo("Kaunas");
        assertThat(saved.getRole()).isEqualTo(Role.MEMBER);
        assertThat(saved.getStatus()).isEqualTo(UserStatus.ACTIVE);
        assertThat(saved.getPasswordHash()).isNotEqualTo("Secret123");
        assertThat(passwordEncoder.matches("Secret123", saved.getPasswordHash())).isTrue();

        ArgumentCaptor<Wallet> wallet = ArgumentCaptor.forClass(Wallet.class);
        verify(walletRepository).save(wallet.capture());
        assertThat(wallet.getValue().getUser()).isSameAs(saved);
        assertThat(wallet.getValue().getBalance()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    void registerRejectsDuplicateEmailIgnoringCase() {
        when(userRepository.existsByEmailIgnoreCase("ausra@rehand.test")).thenReturn(true);

        assertThatThrownBy(() -> service.register(registerForm("Ausra@Rehand.test", "newname", "Secret123")))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("email");

        verify(userRepository, never()).save(any(User.class));
        verify(walletRepository, never()).save(any(Wallet.class));
    }

    @Test
    void registerRejectsDuplicateUsername() {
        when(userRepository.existsByEmailIgnoreCase("new@example.com")).thenReturn(false);
        when(userRepository.existsByUsernameIgnoreCase("ausra_k")).thenReturn(true);

        assertThatThrownBy(() -> service.register(registerForm("new@example.com", "ausra_k", "Secret123")))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("username");

        verify(userRepository, never()).save(any(User.class));
        verify(walletRepository, never()).save(any(Wallet.class));
    }

    @Test
    void registerTurnsADatabaseUniquenessRaceIntoABusinessRuleError() {
        when(userRepository.existsByEmailIgnoreCase("race@example.com")).thenReturn(false);
        when(userRepository.existsByUsernameIgnoreCase("racer")).thenReturn(false);
        when(userRepository.save(any(User.class))).thenThrow(new DataIntegrityViolationException("uk_users_email"));

        assertThatThrownBy(() -> service.register(registerForm("race@example.com", "racer", "Secret123")))
                .isInstanceOf(BusinessRuleException.class);

        verify(walletRepository, never()).save(any(Wallet.class));
    }

    @Test
    void registerRejectsPasswordsLongerThanBcryptCanUse() {
        when(userRepository.existsByEmailIgnoreCase("long@example.com")).thenReturn(false);
        when(userRepository.existsByUsernameIgnoreCase("longpass")).thenReturn(false);
        String tooLong = "a1" + "é".repeat(40); // 2 + 80 bytes in UTF-8, but only 42 characters

        assertThatThrownBy(() -> service.register(registerForm("long@example.com", "longpass", tooLong)))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("too long");

        verify(userRepository, never()).save(any(User.class));
    }

    // -------------------------------------------------------------- uniqueness

    @Test
    void usernameOfTheSameUserDoesNotCountAsTaken() {
        User me = existingUser(7L, "mikel_b", "Secret123");
        when(userRepository.findByUsernameIgnoreCase("MIKEL_B")).thenReturn(Optional.of(me));

        assertThat(service.isUsernameTaken("MIKEL_B", 7L)).isFalse();
        assertThat(service.isUsernameTaken("MIKEL_B", 8L)).isTrue();
        assertThat(service.isUsernameTaken("MIKEL_B", null)).isTrue();
    }

    @Test
    void unknownUsernameIsFree() {
        when(userRepository.findByUsernameIgnoreCase("fresh")).thenReturn(Optional.empty());

        assertThat(service.isUsernameTaken("fresh", null)).isFalse();
    }

    // ----------------------------------------------------------------- profile

    @Test
    void updateProfileChangesUsernameCityAndBio() {
        User me = existingUser(7L, "mikel_b", "Secret123");
        when(userRepository.findById(7L)).thenReturn(Optional.of(me));
        when(userRepository.findByUsernameIgnoreCase("mikel.b")).thenReturn(Optional.empty());
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        User saved = service.updateProfile(7L, new ProfileForm("mikel.b", "Vilnius", "Books and bike parts."));

        assertThat(saved.getUsername()).isEqualTo("mikel.b");
        assertThat(saved.getCity()).isEqualTo("Vilnius");
        assertThat(saved.getBio()).isEqualTo("Books and bike parts.");
    }

    @Test
    void updateProfileRejectsAUsernameOfAnotherUser() {
        User me = existingUser(7L, "mikel_b", "Secret123");
        User other = existingUser(2L, "ausra_k", "Secret123");
        when(userRepository.findById(7L)).thenReturn(Optional.of(me));
        when(userRepository.findByUsernameIgnoreCase("Ausra_K")).thenReturn(Optional.of(other));

        assertThatThrownBy(() -> service.updateProfile(7L, new ProfileForm("Ausra_K", null, null)))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("username");

        assertThat(me.getUsername()).isEqualTo("mikel_b");
        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    void updateProfileKeepsTheOwnUsername() {
        User me = existingUser(7L, "mikel_b", "Secret123");
        when(userRepository.findById(7L)).thenReturn(Optional.of(me));
        when(userRepository.findByUsernameIgnoreCase("mikel_b")).thenReturn(Optional.of(me));
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        User saved = service.updateProfile(7L, new ProfileForm("mikel_b", "Kaunas", null));

        assertThat(saved.getCity()).isEqualTo("Kaunas");
        assertThat(saved.getBio()).isNull();
    }

    @Test
    void unknownUserIsNotFound() {
        when(userRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getById(99L)).isInstanceOf(NotFoundException.class);
    }

    // ------------------------------------------------------------------ avatar

    @Test
    void changeAvatarStoresTheNewFileAndDeletesTheOldOne() {
        User me = existingUser(7L, "mikel_b", "Secret123");
        me.setAvatarPath("avatars/old.png");
        MockMultipartFile file = new MockMultipartFile("avatar", "me.png", "image/png", new byte[] {1, 2, 3});
        when(userRepository.findById(7L)).thenReturn(Optional.of(me));
        when(fileStorageService.saveImage(file, "avatars")).thenReturn("avatars/new.png");
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        User saved = service.changeAvatar(7L, file);

        assertThat(saved.getAvatarPath()).isEqualTo("avatars/new.png");
        verify(fileStorageService).delete("avatars/old.png");
    }

    @Test
    void changeAvatarKeepsTheOldPhotoWhenTheNewFileIsRejected() {
        User me = existingUser(7L, "mikel_b", "Secret123");
        me.setAvatarPath("avatars/old.png");
        MockMultipartFile file = new MockMultipartFile("avatar", "me.txt", "text/plain", new byte[] {1});
        when(userRepository.findById(7L)).thenReturn(Optional.of(me));
        when(fileStorageService.saveImage(file, "avatars"))
                .thenThrow(new BusinessRuleException("Only JPEG, PNG and WebP images are allowed."));

        assertThatThrownBy(() -> service.changeAvatar(7L, file)).isInstanceOf(BusinessRuleException.class);

        assertThat(me.getAvatarPath()).isEqualTo("avatars/old.png");
        verify(fileStorageService, never()).delete(any());
    }

    @Test
    void removeAvatarClearsThePathAndDeletesTheFile() {
        User me = existingUser(7L, "mikel_b", "Secret123");
        me.setAvatarPath("avatars/old.png");
        when(userRepository.findById(7L)).thenReturn(Optional.of(me));
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        User saved = service.removeAvatar(7L);

        assertThat(saved.getAvatarPath()).isNull();
        verify(fileStorageService).delete("avatars/old.png");
    }

    // ---------------------------------------------------------------- password

    @Test
    void changePasswordStoresANewHash() {
        User me = existingUser(7L, "mikel_b", "OldSecret1");
        String oldHash = me.getPasswordHash();
        when(userRepository.findById(7L)).thenReturn(Optional.of(me));

        service.changePassword(7L, "OldSecret1", "NewSecret2");

        assertThat(me.getPasswordHash()).isNotEqualTo(oldHash);
        assertThat(passwordEncoder.matches("NewSecret2", me.getPasswordHash())).isTrue();
        assertThat(passwordEncoder.matches("OldSecret1", me.getPasswordHash())).isFalse();
        verify(userRepository).save(me);
    }

    @Test
    void changePasswordRejectsAWrongCurrentPassword() {
        User me = existingUser(7L, "mikel_b", "OldSecret1");
        String oldHash = me.getPasswordHash();
        when(userRepository.findById(7L)).thenReturn(Optional.of(me));

        assertThatThrownBy(() -> service.changePassword(7L, "WrongGuess1", "NewSecret2"))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("current password");

        assertThat(me.getPasswordHash()).isEqualTo(oldHash);
        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    void changePasswordRejectsReusingTheCurrentPassword() {
        User me = existingUser(7L, "mikel_b", "OldSecret1");
        when(userRepository.findById(7L)).thenReturn(Optional.of(me));

        assertThatThrownBy(() -> service.changePassword(7L, "OldSecret1", "OldSecret1"))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("different");

        verify(userRepository, never()).save(any(User.class));
    }

    // ------------------------------------------------------------------ wallet

    @Test
    void walletBalanceIsReadFromTheWallet() {
        Wallet wallet = new Wallet(new User(), new BigDecimal("75.50"));
        when(walletRepository.findByUserId(3L)).thenReturn(Optional.of(wallet));

        assertThat(service.getWalletBalance(3L)).isEqualByComparingTo("75.50");
    }

    @Test
    void missingWalletShowsZero() {
        when(walletRepository.findByUserId(4L)).thenReturn(Optional.empty());

        assertThat(service.getWalletBalance(4L)).isEqualByComparingTo(BigDecimal.ZERO);
    }
}
