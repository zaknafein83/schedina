package it.schedina.resource.user;

import it.schedina.dto.MatchDto;
import it.schedina.entity.Contest;
import it.schedina.entity.Match;
import it.schedina.entity.Rule;
import it.schedina.entity.Team;
import it.schedina.service.AuthService;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;

@Path("/contests")
@Produces(MediaType.APPLICATION_JSON)
public class UserContestResource {

    @Inject AuthService auth;

    @GET
    @Transactional
    public Response listOpen(@HeaderParam("Authorization") String token) {
        auth.requireAuth(token);
        LocalDateTime now = LocalDateTime.now();
        List<Contest> open = Contest.find("status", Contest.Status.OPEN).list();
        var result = open.stream().map(c -> {
            Rule rule = c.rule();
            long secondsLeft = Math.max(0, ChronoUnit.SECONDS.between(now, c.closeAt));
            return Map.ofEntries(
                    Map.entry("id", c.id),
                    Map.entry("name", c.name),
                    Map.entry("description", c.description != null ? c.description : ""),
                    Map.entry("leagueId", c.leagueId),
                    Map.entry("openAt", c.openAt),
                    Map.entry("closeAt", c.closeAt),
                    Map.entry("timeLeftSeconds", secondsLeft),
                    Map.entry("requiredMatches", rule != null ? rule.requiredMatches : 0),
                    Map.entry("winningThresholds", rule != null ? rule.winningThresholds : List.of()),
                    Map.entry("maxDoubles", rule != null ? rule.maxDoubles : 0),
                    Map.entry("maxTriples", rule != null ? rule.maxTriples : 0)
            );
        }).toList();
        return Response.ok(result).build();
    }

    @GET
    @Path("/{id}/matches")
    @Transactional
    public List<MatchDto.MatchResponse> contestMatches(
            @HeaderParam("Authorization") String token,
            @PathParam("id") Long id) {
        auth.requireAuth(token);
        return Match.findByContest(id).stream().map(m -> {
            Team home = Team.findById(m.homeTeamId);
            Team away = Team.findById(m.awayTeamId);
            return MatchDto.MatchResponse.from(m,
                    home != null ? home.name : "?",
                    away != null ? away.name : "?");
        }).toList();
    }
}
