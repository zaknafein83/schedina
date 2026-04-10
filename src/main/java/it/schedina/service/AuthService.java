package it.schedina.service;

import io.quarkus.elytron.security.common.BcryptUtil;
import it.schedina.entity.User;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Response;

import java.util.Map;

@ApplicationScoped
public class AuthService {

    @Inject
    JwtService jwtService;

    public String hashPassword(String plain) {
        return BcryptUtil.bcryptHash(plain);
    }

    public boolean checkPassword(String plain, String hashed) {
        return BcryptUtil.matches(plain, hashed);
    }

    /**
     * Parses the Authorization header, validates the token, loads and returns the User.
     * Throws 401 if the token is missing, invalid, or the user is inactive.
     * Must be called from a @Transactional context.
     */
    public User requireAuth(String authHeader) {
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            throw unauthorized("Missing or invalid Authorization header");
        }
        Long userId = jwtService.extractUserId(authHeader.substring(7));
        if (userId == null) {
            throw unauthorized("Invalid or expired token");
        }
        User user = User.findById(userId);
        if (user == null || !user.isActive) {
            throw unauthorized("User not found or inactive");
        }
        return user;
    }

    /** Like requireAuth but additionally checks for ADMIN role. */
    public User requireAdmin(String authHeader) {
        User user = requireAuth(authHeader);
        if (user.role != User.Role.ADMIN) {
            throw new WebApplicationException(
                    Response.status(403).entity(Map.of("error", "Admin access required")).build());
        }
        return user;
    }

    private WebApplicationException unauthorized(String message) {
        return new WebApplicationException(
                Response.status(401).entity(Map.of("error", message)).build());
    }
}
