package com.example.urlshortener.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.example.urlshortener.domain.User;

/**
 * Data access for {@link User}s.
 */
public interface UserRepository extends JpaRepository<User, Long> {

    /**
     * Finds a user by their (unique) email — used at login (Phase 6).
     */
    Optional<User> findByEmail(String email);
}