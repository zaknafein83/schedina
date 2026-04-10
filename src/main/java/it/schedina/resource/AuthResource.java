package it.schedina.resource;

import it.schedina.dto.AuthDto;
import it.schedina.dto.UserDto;
import it.schedina.entity.User;
import it.schedina.service.AuthService;
import it.schedina.service.JwtService;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.validation.Valid;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.Map;

@Path("/auth")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class AuthResource {

    @Inject AuthService authService;
    @Inject JwtService jwtService;

    @POST
    @Path("/register")
    @Transactional
    public Response register(@Valid AuthDto.RegisterRequest req) {
        if (User.findByEmail(req.email()).isPresent()) {
            return Response.status(400).entity(Map.of("error", "Email già registrata")).build();
        }
        User user = new User();
        user.email = req.email();
        user.username = req.username();
        user.hashedPassword = authService.hashPassword(req.password());
        user.firstName = req.firstName();
        user.lastName = req.lastName();
        user.persist();
        return Response.status(201).entity(UserDto.UserResponse.from(user)).build();
    }

    @POST
    @Path("/login")
    @Transactional
    public Response login(@Valid AuthDto.LoginRequest req) {
        User user = User.findByEmail(req.email()).orElse(null);
        if (user == null || !authService.checkPassword(req.password(), user.hashedPassword)) {
            return Response.status(401).entity(Map.of("error", "Credenziali non valide")).build();
        }
        if (!user.isActive) {
            return Response.status(403).entity(Map.of("error", "Account disabilitato")).build();
        }
        String token = jwtService.createToken(user.id);
        return Response.ok(new AuthDto.TokenResponse(token)).build();
    }

    @GET
    @Path("/me")
    @Transactional
    public Response me(@HeaderParam("Authorization") String auth) {
        User user = authService.requireAuth(auth);
        return Response.ok(UserDto.UserResponse.from(user)).build();
    }

    @POST
    @Path("/forgot-password")
    @Transactional
    public Response forgotPassword(@Valid AuthDto.ForgotPasswordRequest req) {
        User user = User.findByEmail(req.email()).orElse(null);
        if (user == null) {
            // Non rivelare se l'email esiste o meno
            return Response.ok(Map.of("message", "Se l'email è registrata riceverai le istruzioni")).build();
        }
        byte[] bytes = new byte[32];
        new SecureRandom().nextBytes(bytes);
        String token = HexFormat.of().formatHex(bytes);
        user.resetToken = token;
        user.resetTokenExpiresAt = LocalDateTime.now().plusHours(1);
        user.persist();
        // In produzione qui si manderebbe l'email; per ora il token è nella risposta
        return Response.ok(Map.of(
                "message", "Token generato",
                "resetToken", token,
                "expiresAt", user.resetTokenExpiresAt.toString()
        )).build();
    }

    @POST
    @Path("/reset-password")
    @Transactional
    public Response resetPassword(@Valid AuthDto.ResetPasswordRequest req) {
        User user = User.findByResetToken(req.token()).orElse(null);
        if (user == null || user.resetTokenExpiresAt == null
                || LocalDateTime.now().isAfter(user.resetTokenExpiresAt)) {
            return Response.status(400).entity(Map.of("error", "Token non valido o scaduto")).build();
        }
        user.hashedPassword = authService.hashPassword(req.newPassword());
        user.resetToken = null;
        user.resetTokenExpiresAt = null;
        user.persist();
        return Response.ok(Map.of("message", "Password aggiornata")).build();
    }
}
