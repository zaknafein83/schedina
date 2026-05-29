package it.schedina.resource.admin;

import it.schedina.dto.SchedinaDto;
import it.schedina.entity.BetOption;
import it.schedina.entity.Schedina;
import it.schedina.entity.Scommessa;
import it.schedina.entity.Selezione;
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
            @HeaderParam("Authorization") String token,
            @QueryParam("concorsoId") Long concorsoId) {
        auth.requireAdminOrMod(token);
        if (concorsoId == null) throw new BadRequestException("concorsoId obbligatorio");
        return Schedina.findByConcorso(concorsoId).stream()
                .map(SchedinaDto.SchedinaSummary::from).toList();
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
            Scommessa b = Scommessa.findById(sel.betId);
            String betLabel = b != null ? b.label : "?";
            String official = b != null ? b.officialResultRef : null;
            String choiceLabel = sel.choiceRef;
            for (BetOption o : BetOption.findByBet(sel.betId)) {
                if (o.ref.equals(sel.choiceRef)) { choiceLabel = o.label; break; }
            }
            sels.add(new SchedinaDto.SelezioneResponse(
                    sel.betId, betLabel, sel.choiceRef, choiceLabel, sel.isCorrect, official));
        }
        return new SchedinaDto.SchedinaDetail(s.id, s.userId, s.concorsoId, s.status,
                s.correctCount, s.isWinner, s.confirmedAt, sels);
    }
}
