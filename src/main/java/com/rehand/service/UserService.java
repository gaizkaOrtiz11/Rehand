package com.rehand.service;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

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

/** Accounts: registration, profile editing, avatar and password. */
@Service
public class UserService {

    /** BCrypt only looks at the first 72 bytes of a password. */
    static final int MAX_PASSWORD_BYTES = 72;

    private final UserRepository userRepository;
    private final WalletRepository walletRepository;
    private final PasswordEncoder passwordEncoder;
    private final FileStorageService fileStorageService;

    public UserService(UserRepository userRepository,
                       WalletRepository walletRepository,
                       PasswordEncoder passwordEncoder,
                       FileStorageService fileStorageService) {
        this.userRepository = userRepository;
        this.walletRepository = walletRepository;
        this.passwordEncoder = passwordEncoder;
        this.fileStorageService = fileStorageService;
    }

    // ---------------------------------------------------------------- lookups

    @Transactional(readOnly = true)
    public User getById(Long id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("User " + id + " not found"));
    }

    @Transactional(readOnly = true)
    public boolean isEmailTaken(String email) {
        return email != null && userRepository.existsByEmailIgnoreCase(email.trim());
    }

    /** True when another account (not {@code exceptUserId}) already uses the username, ignoring case. */
    @Transactional(readOnly = true)
    public boolean isUsernameTaken(String username, Long exceptUserId) {
        if (username == null) {
            return false;
        }
        return userRepository.findByUsernameIgnoreCase(username.trim())
                .map(found -> exceptUserId == null || !found.getId().equals(exceptUserId))
                .orElse(false);
    }

    @Transactional(readOnly = true)
    public BigDecimal getWalletBalance(Long userId) {
        return walletRepository.findByUserId(userId)
                .map(Wallet::getBalance)
                .orElse(BigDecimal.ZERO.setScale(2));
    }

    // ----------------------------------------------------------- registration

    /**
     * Creates a MEMBER account and its empty wallet in one transaction.
     *
     * @throws BusinessRuleException when the e-mail or the username is already taken
     */
    @Transactional
    public User register(RegisterForm form) {
        String email = form.getEmail().trim().toLowerCase(Locale.ROOT);
        String username = form.getUsername().trim();

        if (userRepository.existsByEmailIgnoreCase(email)) {
            throw new BusinessRuleException("That email address is already registered.");
        }
        if (userRepository.existsByUsernameIgnoreCase(username)) {
            throw new BusinessRuleException("That username is already taken.");
        }
        requireBcryptLength(form.getPassword());

        User user = new User();
        user.setEmail(email);
        user.setUsername(username);
        user.setPasswordHash(passwordEncoder.encode(form.getPassword()));
        user.setCity(form.getCity());
        user.setRole(Role.MEMBER);
        user.setStatus(UserStatus.ACTIVE);

        try {
            user = userRepository.save(user);
        } catch (DataIntegrityViolationException e) {
            // Two people registering the same name at the same moment: the unique keys of the database decide.
            throw new BusinessRuleException("That email address or username was just taken. Please choose another.");
        }
        walletRepository.save(new Wallet(user, BigDecimal.ZERO.setScale(2)));
        return user;
    }

    // ---------------------------------------------------------------- profile

    /**
     * Updates username, city and bio.
     *
     * @throws BusinessRuleException when another account already uses the username
     */
    @Transactional
    public User updateProfile(Long userId, ProfileForm form) {
        User user = getById(userId);
        String username = form.getUsername().trim();
        if (isUsernameTaken(username, userId)) {
            throw new BusinessRuleException("That username is already taken.");
        }
        user.setUsername(username);
        user.setCity(form.getCity());
        user.setBio(form.getBio());
        try {
            return userRepository.save(user);
        } catch (DataIntegrityViolationException e) {
            throw new BusinessRuleException("That username is already taken.");
        }
    }

    /** Stores the uploaded image as the new avatar and deletes the previous file. */
    @Transactional
    public User changeAvatar(Long userId, MultipartFile file) {
        User user = getById(userId);
        String newPath = fileStorageService.saveImage(file, "avatars");
        String oldPath = user.getAvatarPath();
        user.setAvatarPath(newPath);
        User saved = userRepository.save(user);
        fileStorageService.delete(oldPath);
        return saved;
    }

    /** Removes the avatar file and clears the path; the page then shows the initials circle. */
    @Transactional
    public User removeAvatar(Long userId) {
        User user = getById(userId);
        String oldPath = user.getAvatarPath();
        user.setAvatarPath(null);
        User saved = userRepository.save(user);
        fileStorageService.delete(oldPath);
        return saved;
    }

    // --------------------------------------------------------------- password

    /**
     * Changes the password after checking the current one.
     *
     * @throws BusinessRuleException when the current password is wrong or the new one equals it
     */
    @Transactional
    public void changePassword(Long userId, String currentPassword, String newPassword) {
        User user = getById(userId);
        if (currentPassword == null || !passwordEncoder.matches(currentPassword, user.getPasswordHash())) {
            throw new BusinessRuleException("Your current password is not correct.");
        }
        if (newPassword.equals(currentPassword)) {
            throw new BusinessRuleException("Choose a new password that is different from the current one.");
        }
        requireBcryptLength(newPassword);
        user.setPasswordHash(passwordEncoder.encode(newPassword));
        userRepository.save(user);
    }

    private static void requireBcryptLength(String password) {
        if (password.getBytes(StandardCharsets.UTF_8).length > MAX_PASSWORD_BYTES) {
            throw new BusinessRuleException("The password is too long. Use at most 72 bytes (about 72 letters and digits).");
        }
    }
}
