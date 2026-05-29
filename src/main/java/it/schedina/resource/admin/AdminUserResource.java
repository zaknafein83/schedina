package it.schedina.resource.admin;

import it.schedina.dto.UserDto;
import it.schedina.entity.Schedina;
import it.schedina.entity.User;
import it.schedina.service.AuthService;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.util.List;
import java.util.Map;

@Path("/admin/users")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class AdminUserResource {

    @Inject AuthService auth;

    @GET
    @Transactional
    public List<UserDto.UserResponse> list(@HeaderParam("Authorization") String token) {
        auth.requireAdmin(token);
        return User.<User>listAll().stream().map(UserDto.UserResponse::from).toList();
    }

    @GET
    @Path("/{id}")
    @Transactional
    public UserDto.UserResponse get(@HeaderParam("Authorization") String token, @PathParam("id") Long id) {
        auth.requireAdmin(token);
        User u = User.findById(id);
        if (u == null) throw new NotFoundException();
        return UserDto.UserResponse.from(u);
    }

    @PATCH
    @Path("/{id}/status")
    @Transactional
    public UserDto.UserResponse updateStatus(
            @HeaderParam("Authorization") String token,
            @PathParam("id") Long id,
            UserDto.StatusUpdate req) {
        auth.requireAdmin(token);
        User u = User.findById(id);
        if (u == null) throw new NotFoundException();
        u.isActive = req.isActive();
        u.persist();
        return UserDto.UserResponse.from(u);
    }

    @PATCH
    @Path("/{id}/role")
    @Transactional
    public UserDto.UserResponse updateRole(
            @HeaderParam("Authorization") String token,
            @PathParam("id") Long id,
            UserDto.RoleUpdate req) {
        auth.requireAdmin(token);
        User u = User.findById(id);
        if (u == null) throw new NotFoundException();
        u.role = req.role();
        u.persist();
        return UserDto.UserResponse.from(u);
    }

    @GET
    @Path("/{id}/stats")
    @Transactional
    public Response stats(@HeaderParam("Authorization") String token, @PathParam("id") Long id) {
        auth.requireAdmin(token);
        User u = User.findById(id);
        if (u == null) throw new NotFoundException();
        long total = Schedina.count("userId", id);
        long winning = Schedina.count("userId = ?1 and status = ?2", id, Schedina.Status.WINNING);
        return Response.ok(Map.of("userId", id, "totalSchedine", total, "winningSchedine", winning)).build();
    }
}
