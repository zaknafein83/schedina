package it.schedina.dto;

import it.schedina.entity.User;

import java.time.LocalDateTime;

public final class UserDto {

    private UserDto() {}

    public record UserResponse(
            Long id, String email, String username,
            String firstName, String lastName,
            User.Role role, boolean isActive, LocalDateTime createdAt
    ) {
        public static UserResponse from(User u) {
            return new UserResponse(u.id, u.email, u.username,
                    u.firstName, u.lastName, u.role, u.isActive, u.createdAt);
        }
    }

    public record StatusUpdate(boolean isActive) {}

    public record RoleUpdate(User.Role role) {}
}
