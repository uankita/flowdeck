package com.flowdeck.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * An account holder. Not scoped to a {@link Workspace} — the same user can
 * belong to many, via {@link WorkspaceMember}.
 *
 * <p>Login/auth is not wired up yet (see {@code SecurityConfig}); this is the
 * row that a future JWT filter would resolve {@code sub} against.
 */
@Entity
@Table(name = "users")
@Getter
@Setter
@NoArgsConstructor
public class User extends BaseEntity {

    /** Normalize to lowercase before persisting; the unique index is case-sensitive. */
    @Column(nullable = false, unique = true, length = 320)
    private String email;

    @Column(name = "display_name", nullable = false, length = 120)
    private String displayName;

    /** BCrypt hash. Never populate from a request DTO directly. */
    @Column(name = "password_hash", nullable = false, length = 100)
    private String passwordHash;

    @Column(nullable = false)
    private boolean active = true;
}
