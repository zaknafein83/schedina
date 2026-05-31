package it.schedina.resource.user;

import it.schedina.dto.GiocataDto;
import it.schedina.dto.GiocataPartitaDto;
import it.schedina.dto.ScommessaDto;
import it.schedina.entity.*;
import it.schedina.service.AuthService;
import it.schedina.service.ScommessaResolutionService;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.validation.Valid;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.util.List;

@Path("/scommesse")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class UserScommessaResource {

    @Inject AuthService auth;
    @Inject ScommessaResolutionService resolution;

    // ---- Fine campionato ----

    @GET
    @Path("/mine")
    @Transactional
    public List<GiocataDto.GiocataResponse> mine(@HeaderParam("Authorization") String token) {
        User user = auth.requireAuth(token);
        return Giocata.findByUser(user.id).stream().map(this::toResponse).toList();
    }

    /** Giocata self-service di fine campionato: lega + mercato + bersaglio (id giocatore/squadra). */
    @POST
    @Path("/stagione")
    @Transactional
    public Response placeStagione(@HeaderParam("Authorization") String token, @Valid GiocataDto.GiocataStagioneRequest req) {
        User user = auth.requireAuth(token);
        Giocata g = resolution.placeGiocataStagione(user.id, req.seasonId(), req.leagueId(), req.market(), req.prediction());
        return Response.status(201).entity(toResponse(g)).build();
    }

    // ---- Di partita ----

    @POST
    @Path("/partita")
    @Transactional
    public Response placePartita(@HeaderParam("Authorization") String token, @Valid GiocataPartitaDto.GiocataPartitaRequest req) {
        User user = auth.requireAuth(token);
        GiocataPartita g = resolution.placeGiocataPartita(user.id, req.matchId(), req.market(), req.prediction());
        return Response.status(201).entity(toPartitaResponse(g)).build();
    }

    @GET
    @Path("/partita/mine")
    @Transactional
    public List<GiocataPartitaDto.GiocataPartitaResponse> minePartita(@HeaderParam("Authorization") String token) {
        User user = auth.requireAuth(token);
        return GiocataPartita.findByUser(user.id).stream().map(this::toPartitaResponse).toList();
    }

    // ---- mappers ----

    private GiocataDto.GiocataResponse toResponse(Giocata g) {
        Scommessa b = Scommessa.findById(g.scommessaId);
        String label = b != null ? b.label : "?";
        String choiceLabel = g.choiceRef;
        if (b != null) {
            Scommessa.TargetKind kind = b.targetKind();
            if (kind == Scommessa.TargetKind.PLAYER) {
                Player p = Player.findById(parseLong(g.choiceRef));
                if (p != null) choiceLabel = p.firstName + " " + p.lastName;
            } else if (kind == Scommessa.TargetKind.TEAM) {
                choiceLabel = teamName(parseLong(g.choiceRef));
            }
        }
        return new GiocataDto.GiocataResponse(g.id, g.scommessaId, label,
                b != null ? b.market : null, g.choiceRef, choiceLabel, g.isCorrect,
                b != null ? b.status : null, b != null ? b.officialResultRef : null);
    }

    private GiocataPartitaDto.GiocataPartitaResponse toPartitaResponse(GiocataPartita g) {
        Match m = Match.findById(g.matchId);
        String home = m != null ? teamName(m.homeTeamId) : "?";
        String away = m != null ? teamName(m.awayTeamId) : "?";
        String label = predictionLabel(g.market, g.prediction);
        return new GiocataPartitaDto.GiocataPartitaResponse(g.id, g.matchId, home, away,
                g.market, g.prediction, label, g.isCorrect);
    }

    private String predictionLabel(Scommessa.Market market, String prediction) {
        return switch (market) {
            case GOAL_NOGOAL -> prediction.equals("GOAL") ? "Gol" : "No gol";
            case WINNER -> teamName(parseLong(prediction));
            case FIRST_SCORER -> {
                if (prediction.equals(it.schedina.entity.Match.OWN_GOAL_REF)) yield "Autogol";
                Player p = Player.findById(parseLong(prediction));
                yield p != null ? p.firstName + " " + p.lastName : prediction;
            }
            default -> prediction; // EXACT_SCORE
        };
    }

    private String teamName(Long id) {
        if (id == null) return "?";
        Team t = Team.findById(id);
        return t != null ? t.name : "?";
    }

    private long parseLong(String s) {
        try { return Long.parseLong(s); } catch (Exception e) { return -1L; }
    }
}
