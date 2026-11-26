package com.rehand.service;

import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.rehand.repository.UserRepository;

/** Loads accounts for Spring Security by e-mail address (case-insensitive). */
@Service
public class AppUserDetailsService implements UserDetailsService {

    private final UserRepository userRepository;

    public AppUserDetailsService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        String normalized = email == null ? "" : email.trim();
        return userRepository.findByEmailIgnoreCase(normalized)
                .map(AppUserDetails::new)
                .orElseThrow(() -> new UsernameNotFoundException("No account for " + normalized));
    }
}
