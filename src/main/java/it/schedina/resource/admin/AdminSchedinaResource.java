package it.schedina.resource.admin;

import it.schedina.dto.SchedinaDto;
import it.schedina.entity.Match;
import it.schedina.entity.Schedina;
import it.schedina.entity.Selezione;
import it.schedina.entity.Team;
import it.schedina.service.AuthService;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;

import java.util.ArrayList;
import java.util.List;

@Path("/admin/schedine")
@Produces(MediaType.APPLICATION_JSON)
public class AdminSchedinaResource {

    @Inject AuthService auth;

    @GET
    @Path("/by-concorso")
    @Transactional
    public List<SchedinaDto.SchedinaSummary> byConcorso(
            @HeaderParam("Authorization") String token, @QueryParam("concorsoId") Long concorsoId) {
        auth.requireAdminOrMod(token);
        if (concorsoId == null) throw new BadRequestException("concorsoId obbligatorio");
        return Schedina.findByConcorso(concorsoId).stream().map(SchedinaDto.SchedinaSummary::from).toList();
    }

    @GET
    @Path("/{id}")
    @Transactional
    public SchedinaDto.SchedinaDetail get(@HeaderParam("Authorization") String token, @PathParam("id") Long id) {
        auth.requireAdminOrMod(token);
        Schedina s = Schedina.findById(id);
        if (s == null) throw new NotFoundException();
        return detailOf(s);
    }

    public static SchedinaDto.SchedinaDetail detailOf(Schedina s) {
        List<SchedinaDto.SelezioneResponse> sels = new ArrayList<>();
        for (Selezione sel : Selezione.findBySchedina(s.id)) {
            Match m = Match.findById(sel.matchId);
            String home = m != null ? teamName(m.homeTeamId) : "?";
            String away = m != null ? teamName(m.awayTeamId) : "?";
            sels.add(new SchedinaDto.SelezioneResponse(
                    sel.matchId, home, away, sel.choice1x2, sel.choiceUo,
                    sel.correct1x2, sel.correctUo,
                    m != null ? m.result1x2() : null,
                    m != null ? m.resultUO() : null,
                    m != null ? m.overUnderLine : null));
        }
        return new SchedinaDto.SchedinaDetail(s.id, s.userId, s.concorsoId, s.status,
                s.correctCount, s.isWinner, sels);
    }

    private static String teamName(Long id) {
        Team t = Team.findById(id);
        return t != null ? t.name : "?";
    }
}
