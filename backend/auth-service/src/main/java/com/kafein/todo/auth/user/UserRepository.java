package com.kafein.todo.auth.user;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {

    /** {@code email} parametresi {@link User#normalizeEmail(String)} ile normalize edilmiş olmalı. */
    Optional<User> findByEmail(String email);

    boolean existsByEmail(String email);
}
