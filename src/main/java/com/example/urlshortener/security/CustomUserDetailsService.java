package com.example.urlshortener.security;

import com.example.urlshortener.domain.User;
import com.example.urlshortener.repository.UserRepository;
import java.util.List;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

/**
 * Minimal {@link UserDetailsService} required by Spring Security (§12 of the
 * plan). Lookup uses the existing {@link UserRepository} (email is the unique,
 * user-visible identifier) — PostgreSQL remains the source of truth and no
 * separate user store is introduced.
 */
@Service
public class CustomUserDetailsService implements UserDetailsService {

    private final UserRepository userRepository;

    public CustomUserDetailsService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new UsernameNotFoundException("No account for email: " + email));

        // Deliberately generic authority set — fine-grained roles arrive in later
        // phases. The password returned here is the BCrypt hash, which Spring's
        // DaoAuthenticationProvider compares against credentials.
        return org.springframework.security.core.userdetails.User
                .withUsername(email)
                .password(user.getPassword())
                .authorities(List.of(new SimpleGrantedAuthority("ROLE_USER")))
                .build();
    }
}