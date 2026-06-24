package it.schedina.entity;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.*;

import java.time.LocalDateTime;
import java.util.Locale;
import java.util.Optional;

@Entity
@Table(name = "users")
public class User extends PanacheEntityBase {

    public enum Role { ADMIN, MOD, USER }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @Column(nullable = false, unique = true, length = 255)
    public String email;

    @Column(unique = true, length = 100)
    public String username;

    @Column(name = "hashed_password", nullable = false, length = 255)
    public String hashedPassword;

    @Column(name = "first_name", nullable = false, length = 100)
    public String firstName;

    @Column(name = "last_name", nullable = false, length = 100)
    public String lastName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    public Role role = Role.USER;

    @Column(name = "is_active", nullable = false)
    public boolean isActive = true;

    @Column(name = "created_at", nullable = false)
    public LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "reset_token", length = 64)
    public String resetToken;

    @Column(name = "reset_token_expires_at")
    public LocalDateTime resetTokenExpiresAt;

    /** Normalizza un'email per confronto/archiviazione: trim + minuscolo (ASCII-safe). */
    public static String normalizeEmail(String email) {
        return email == null ? null : email.trim().toLowerCase(Locale.ROOT);
    }

    public static Optional<User> findByEmail(String email) {
        return find("email", email).firstResultOptional();
    }

    public static Optional<User> findByResetToken(String token) {
        return find("resetToken", token).firstResultOptional();
    }
}
