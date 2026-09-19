package com.example.urlshortener.service;

import com.example.urlshortener.domain.User;
import com.example.urlshortener.dto.LoginRequest;
import com.example.urlshortener.dto.LoginResponse;
import com.example.urlshortener.dto.RegisterRequest;
import com.example.urlshortener.dto.RegisterResponse;
import com.example.urlshortener.exception.DuplicateEmailException;
import com.example.urlshortener.exception.InvalidCredentialsException;
import com.example.urlshortener.repository.UserRepository;
import com.example.urlshortener.security.JwtService;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Registration and login orchestration (§13 of the plan).
 *
 * <ul>
 *   <li>Register: validate → hash password (BCrypt) → persist to PostgreSQL.</li>
 *   <li>Login: verify credentials via Spring Security → issue a JWT.</li>
 * </ul>
 */
@Service
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;

    public AuthService(UserRepository userRepository,
                       PasswordEncoder passwordEncoder,
                       AuthenticationManager authenticationManager,
                       JwtService jwtService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.authenticationManager = authenticationManager;
        this.jwtService = jwtService;
    }

    /**
     * Registers a new user. Only the password hash is stored — never plaintext.
     *
     * @throws DuplicateEmailException if the email is already registered
     */
    @Transactional
    public RegisterResponse register(RegisterRequest request) {
        String email = normaliseEmail(request.email());
        String password = request.password();

        // Convenience pre-check for the common (non-concurrent) case.
        if (userRepository.findByEmail(email).isPresent()) {
            throw new DuplicateEmailException("An account with that email already exists");
        }

        User user = new User();
        user.setEmail(email);
        user.setPassword(passwordEncoder.encode(password));

        try {
            userRepository.saveAndFlush(user);
        } catch (DataIntegrityViolationException ex) {
            // Final protection against concurrent duplicate registrations: the DB
            // unique constraint on users.email.
            throw new DuplicateEmailException("An account with that email already exists");
        }

        return new RegisterResponse(user.getId(), user.getEmail());
    }

    /**
     * Verifies credentials and returns a JWT access token.
     *
     * <p>A single generic failure is thrown for both unknown email and wrong
     * password so callers cannot enumerate accounts (§6 of the plan).
     *
     * @throws InvalidCredentialsException on authentication failure
     */
    @Transactional(readOnly = true)
    public LoginResponse login(LoginRequest request) {
        try {
            // Throws BadCredentialsException for both unknown email and wrong password.
            authenticationManager.authenticate(new UsernamePasswordAuthenticationToken(
                    normaliseEmail(request.email()), request.password()));
        } catch (AuthenticationException ex) {
            throw new InvalidCredentialsException("Invalid email or password");
        }

        User user = userRepository.findByEmail(normaliseEmail(request.email())).orElseThrow(
                // By this point the user definitely exists (authentication succeeded).
                () -> new InvalidCredentialsException("Invalid email or password"));
        return LoginResponse.bearer(jwtService.issueToken(user));
    }

    private static String normaliseEmail(String email) {
        return email == null ? null : email.trim().toLowerCase();
    }
}