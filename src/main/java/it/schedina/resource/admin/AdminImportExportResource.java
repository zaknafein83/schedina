package it.schedina.resource.admin;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import it.schedina.entity.*;
import it.schedina.service.AuthService;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Path("/admin")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.TEXT_PLAIN)
public class AdminImportExportResource {

    @Inject AuthService auth;
    @Inject ObjectMapper mapper;

    // ═══════════════════════════════════════════════════════════════════════════
    // EXPORT
    // ═══════════════════════════════════════════════════════════════════════════

    @GET
    @Path("/export/leagues")
    @Transactional
    public Response exportLeagues(@HeaderParam("Authorization") String token) {
        auth.requireAdmin(token);
        return Response.ok(League.<League>listAll().stream().map(this::leagueToMap).toList()).build();
    }

    @GET
    @Path("/export/teams")
    @Transactional
    public Response exportTeams(@HeaderParam("Authorization") String token) {
        auth.requireAdmin(token);
        return Response.ok(Team.<Team>listAll().stream().map(this::teamToMap).toList()).build();
    }

    @GET
    @Path("/export/players")
    @Transactional
    public Response exportPlayers(@HeaderParam("Authorization") String token) {
        auth.requireAdminOrMod(token);
        return Response.ok(Player.<Player>listAll().stream().map(this::playerToMap).toList()).build();
    }

    @GET
    @Path("/export/giornate")
    @Transactional
    public Response exportGiornate(@HeaderParam("Authorization") String token) {
        auth.requireAdminOrMod(token);
        List<Map<String, Object>> rows = new ArrayList<>();
        for (Giornata g : Giornata.<Giornata>find("order by leagueId, number").list()) {
            League l = League.findById(g.leagueId);
            List<Match> matches = Match.findByGiornata(g.id);
            if (matches.isEmpty()) {
                rows.add(giornataRow(l, g, null, null, null, null));
                continue;
            }
            for (Match m : matches) {
                Team home = Team.findById(m.homeTeamId);
                Team away = Team.findById(m.awayTeamId);
                rows.add(giornataRow(l, g,
                        home != null ? home.name : null,
                        away != null ? away.name : null,
                        m.scheduledAt, m.overUnderLine));
            }
        }
        return Response.ok(rows).build();
    }

    @GET
    @Path("/export/all")
    @Transactional
    public Response exportAll(@HeaderParam("Authorization") String token) {
        auth.requireAdmin(token);
        Map<String, Object> all = new LinkedHashMap<>();
        all.put("leagues",  League.<League>listAll().stream().map(this::leagueToMap).toList());
        all.put("teams",    Team.<Team>listAll().stream().map(this::teamToMap).toList());
        all.put("players",  Player.<Player>listAll().stream().map(this::playerToMap).toList());
        return Response.ok(all).build();
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // IMPORT
    // Accetta sia JSON array che CSV (rilevamento automatico dal contenuto)
    // Upsert per nome: aggiorna se esiste, crea se non esiste
    // ═══════════════════════════════════════════════════════════════════════════

    @POST
    @Path("/import/leagues")
    @Transactional
    public Response importLeagues(@HeaderParam("Authorization") String token, String body) {
        auth.requireAdmin(token);
        List<Map<String, String>> rows = parseBody(body);
        int count = 0;
        for (Map<String, String> row : rows) {
            String name = row.get("name");
            if (name == null || name.isBlank()) continue;
            League l = League.<League>find("name", name).firstResult();
            if (l == null) l = new League();
            l.name = name;
            if (row.containsKey("description")) l.description = row.get("description");
            if (row.containsKey("country"))     l.country     = row.get("country");
            if (row.containsKey("isActive"))    l.isActive    = Boolean.parseBoolean(row.get("isActive"));
            l.persist();
            count++;
        }
        return Response.ok(Map.of("imported", count, "entity", "leagues")).build();
    }

    @POST
    @Path("/import/teams")
    @Transactional
    public Response importTeams(@HeaderParam("Authorization") String token, String body) {
        auth.requireAdmin(token);
        List<Map<String, String>> rows = parseBody(body);
        int count = 0;
        for (Map<String, String> row : rows) {
            String name = row.get("name");
            if (name == null || name.isBlank()) continue;
            Long leagueId = resolveLeagueId(row);
            if (leagueId == null) continue;
            Team t = Team.<Team>find("name = ?1 and leagueId = ?2", name, leagueId).firstResult();
            if (t == null) t = new Team();
            t.name = name;
            t.shortName = row.get("shortName");
            t.leagueId  = leagueId;
            if (row.containsKey("isActive")) t.isActive = Boolean.parseBoolean(row.get("isActive"));
            t.persist();
            count++;
        }
        return Response.ok(Map.of("imported", count, "entity", "teams")).build();
    }

    @POST
    @Path("/import/players")
    @Transactional
    public Response importPlayers(@HeaderParam("Authorization") String token, String body) {
        auth.requireAdminOrMod(token);
        List<Map<String, String>> rows = parseBody(body);
        int count = 0;
        for (Map<String, String> row : rows) {
            String firstName = row.get("firstName");
            String lastName  = row.get("lastName");
            if (firstName == null || firstName.isBlank() || lastName == null || lastName.isBlank()) continue;
            Long teamId = resolveTeamId(row);
            Player p;
            if (teamId != null) {
                p = Player.<Player>find("firstName = ?1 and lastName = ?2 and teamId = ?3", firstName, lastName, teamId).firstResult();
            } else {
                p = Player.<Player>find("firstName = ?1 and lastName = ?2 and teamId is null", firstName, lastName).firstResult();
            }
            if (p == null) p = new Player();
            p.firstName = firstName;
            p.lastName  = lastName;
            p.teamId    = teamId;
            if (hasValue(row, "role")) {
                try { p.role = Player.Role.valueOf(row.get("role").toUpperCase()); }
                catch (IllegalArgumentException ignored) {}
            }
            if (row.containsKey("isActive")) p.isActive = Boolean.parseBoolean(row.get("isActive"));
            p.persist();
            count++;
        }
        return Response.ok(Map.of("imported", count, "entity", "players")).build();
    }

    /**
     * Import del calendario: ogni riga è una partita di una giornata per-lega.
     * Colonne: number (turno), homeTeamName, awayTeamName, [leagueName/leagueId], [giornataName], [date], [overUnderLine].
     * Le squadre devono già esistere. Se leagueName/leagueId manca, la lega è dedotta dalla squadra di casa.
     * Le righe con squadre non trovate vengono saltate e segnalate.
     * La giornata (leagueId+number) viene creata se non esiste; le partite sono upsert per giornata+casa+ospite.
     */
    @POST
    @Path("/import/giornate")
    @Transactional
    public Response importGiornate(@HeaderParam("Authorization") String token, String body) {
        auth.requireAdminOrMod(token);
        List<Map<String, String>> rows = parseBody(body);
        int matchesImported = 0;
        int giornateCreated = 0;
        List<String> skipped = new ArrayList<>();
        Map<String, Giornata> cache = new HashMap<>();

        int rownum = 1; // riga 1 = header
        for (Map<String, String> row : rows) {
            rownum++;
            Integer number = parseIntOrNull(row.get("number"));
            if (number == null) {
                skipped.add("riga " + rownum + ": numero giornata mancante o non valido");
                continue;
            }

            String homeName = firstNonBlank(row.get("homeTeamName"), row.get("home"));
            String awayName = firstNonBlank(row.get("awayTeamName"), row.get("away"));
            if (homeName == null || awayName == null) {
                skipped.add("riga " + rownum + ": casa/ospite mancanti");
                continue;
            }

            // Lega: esplicita dalla riga, altrimenti dedotta dalla squadra di casa.
            // Matching squadre fuzzy (più probabile, non esatto).
            Long leagueId = resolveLeagueId(row);
            Team home = findTeamFuzzy(homeName, leagueId);
            if (home == null) { skipped.add(homeName + " - " + awayName + ": squadra di casa \"" + homeName + "\" non trovata"); continue; }
            if (leagueId == null) leagueId = home.leagueId;
            Team away = findTeamFuzzy(awayName, leagueId);
            if (away == null) { skipped.add(homeName + " - " + awayName + ": squadra ospite \"" + awayName + "\" non trovata"); continue; }
            Long homeId = home.id, awayId = away.id;

            String key = leagueId + "|" + number;
            Giornata g = cache.get(key);
            if (g == null) {
                g = Giornata.<Giornata>find("leagueId = ?1 and number = ?2", leagueId, number).firstResult();
                if (g == null) {
                    g = new Giornata();
                    g.leagueId = leagueId;
                    g.number = number;
                    g.name = hasValue(row, "giornataName") ? row.get("giornataName") : "Giornata " + number;
                    g.persist();
                    giornateCreated++;
                } else if (hasValue(row, "giornataName")) {
                    g.name = row.get("giornataName");
                }
                cache.put(key, g);
            }

            Match existing = Match.<Match>find("giornataId = ?1 and homeTeamId = ?2 and awayTeamId = ?3", g.id, homeId, awayId).firstResult();
            Match mt = existing != null ? existing : new Match();
            mt.leagueId = leagueId;
            mt.giornataId = g.id;
            mt.homeTeamId = homeId;
            mt.awayTeamId = awayId;
            if (hasValue(row, "overUnderLine")) mt.overUnderLine = parseDoubleOrDefault(row.get("overUnderLine"), 3.5);
            else if (mt.overUnderLine == null) mt.overUnderLine = 3.5;
            mt.scheduledAt = parseDate(row.get("date"));
            mt.persist();
            matchesImported++;
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("imported", matchesImported);
        result.put("giornateCreated", giornateCreated);
        result.put("skipped", skipped.size());
        result.put("skippedDetails", skipped);
        result.put("entity", "giornate");
        return Response.ok(result).build();
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // MAP HELPERS
    // ═══════════════════════════════════════════════════════════════════════════

    private Map<String, Object> giornataRow(League l, Giornata g, String home, String away, LocalDateTime when, Double line) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("leagueName", l != null ? l.name : null);
        m.put("number", g.number);
        m.put("giornataName", g.name);
        m.put("homeTeamName", home);
        m.put("awayTeamName", away);
        m.put("date", when != null ? when.toLocalDate().toString() : null);
        m.put("overUnderLine", line);
        return m;
    }

    private Map<String, Object> leagueToMap(League l) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", l.id); m.put("name", l.name);
        m.put("description", l.description); m.put("country", l.country);
        m.put("isActive", l.isActive);
        return m;
    }

    private Map<String, Object> teamToMap(Team t) {
        League l = League.findById(t.leagueId);
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", t.id); m.put("name", t.name); m.put("shortName", t.shortName);
        m.put("leagueId", t.leagueId); m.put("leagueName", l != null ? l.name : null);
        m.put("isActive", t.isActive);
        return m;
    }

    private Map<String, Object> playerToMap(Player p) {
        Team t = p.teamId != null ? Team.<Team>findById(p.teamId) : null;
        League l = t != null ? League.<League>findById(t.leagueId) : null;
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", p.id);
        m.put("firstName", p.firstName);
        m.put("lastName", p.lastName);
        m.put("teamId", p.teamId);
        m.put("teamName", t != null ? t.name : null);
        m.put("leagueId", t != null ? t.leagueId : null);
        m.put("leagueName", l != null ? l.name : null);
        m.put("role", p.role != null ? p.role.name() : null);
        m.put("isActive", p.isActive);
        return m;
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // PARSE HELPERS
    // ═══════════════════════════════════════════════════════════════════════════

    /** Auto-detect JSON vs CSV and return list of field maps */
    private List<Map<String, String>> parseBody(String body) {
        if (body == null || body.isBlank()) return List.of();
        String trimmed = body.trim();
        if (trimmed.startsWith("[")) {
            try {
                List<Map<String, Object>> list = mapper.readValue(trimmed, new TypeReference<>() {});
                return list.stream().map(row -> {
                    Map<String, String> r = new LinkedHashMap<>();
                    row.forEach((k, v) -> r.put(k, v != null ? v.toString() : null));
                    return r;
                }).toList();
            } catch (Exception e) {
                throw new BadRequestException("JSON non valido: " + e.getMessage());
            }
        }
        return parseCsv(trimmed);
    }

    private List<Map<String, String>> parseCsv(String csv) {
        List<Map<String, String>> result = new ArrayList<>();
        String[] lines = csv.split("\r?\n");
        if (lines.length < 2) return result;
        String[] headers = parseCsvLine(lines[0]);
        for (int i = 1; i < lines.length; i++) {
            String line = lines[i].trim();
            if (line.isBlank()) continue;
            String[] values = parseCsvLine(line);
            Map<String, String> row = new LinkedHashMap<>();
            for (int j = 0; j < headers.length; j++) {
                row.put(headers[j].trim(), j < values.length ? values[j].trim() : "");
            }
            result.add(row);
        }
        return result;
    }

    /** CSV line parser — handles fields quoted with double-quotes */
    private String[] parseCsvLine(String line) {
        List<String> fields = new ArrayList<>();
        boolean inQuotes = false;
        StringBuilder current = new StringBuilder();
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (c == '"') {
                inQuotes = !inQuotes;
            } else if (c == ',' && !inQuotes) {
                fields.add(current.toString());
                current = new StringBuilder();
            } else {
                current.append(c);
            }
        }
        fields.add(current.toString());
        return fields.toArray(new String[0]);
    }

    private Long resolveLeagueId(Map<String, String> row) {
        if (hasValue(row, "leagueId")) {
            try { return Long.parseLong(row.get("leagueId")); } catch (NumberFormatException ignored) {}
        }
        if (hasValue(row, "leagueName")) {
            League l = League.<League>find("name", row.get("leagueName")).firstResult();
            return l != null ? l.id : null;
        }
        return null;
    }

    private Long resolveTeamId(Map<String, String> row) {
        if (hasValue(row, "teamId")) {
            try { return Long.parseLong(row.get("teamId")); } catch (NumberFormatException ignored) {}
        }
        if (hasValue(row, "teamName")) {
            Long leagueId = resolveLeagueId(row);
            Team t = leagueId != null
                    ? Team.<Team>find("name = ?1 and leagueId = ?2", row.get("teamName"), leagueId).firstResult()
                    : Team.<Team>find("name", row.get("teamName")).firstResult();
            return t != null ? t.id : null;
        }
        return null;
    }

    private List<Integer> parseIntList(String s) {
        if (s == null || s.isBlank()) return List.of();
        return Arrays.stream(s.split(","))
                .map(String::trim).filter(v -> !v.isBlank())
                .map(v -> { try { return Integer.parseInt(v); } catch (NumberFormatException e) { return null; } })
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    private boolean hasValue(Map<String, String> row, String key) {
        return row.containsKey(key) && row.get(key) != null && !row.get(key).isBlank();
    }

    /** Soglia minima di similarità per accettare un match fuzzy del nome squadra. */
    private static final double FUZZY_THRESHOLD = 0.70;

    /**
     * Trova la squadra più probabile per nome (match fuzzy), nella lega indicata se data,
     * altrimenti fra tutte. Tollera maiuscole/accenti/punteggiatura e piccoli errori di battitura.
     * Ritorna null se nessun candidato supera la soglia di similarità.
     */
    private Team findTeamFuzzy(String name, Long leagueId) {
        if (name == null || name.isBlank()) return null;
        String q = normalizeName(name);
        List<Team> candidates = leagueId != null
                ? Team.<Team>find("leagueId", leagueId).list()
                : Team.<Team>listAll();
        Team best = null;
        double bestScore = 0;
        for (Team t : candidates) {
            if (t.name == null) continue;
            double s = similarity(q, normalizeName(t.name));
            if (s > bestScore) { bestScore = s; best = t; }
        }
        return bestScore >= FUZZY_THRESHOLD ? best : null;
    }

    /** Minuscolo, senza diacritici, senza punteggiatura, spazi normalizzati. */
    private static String normalizeName(String s) {
        String n = java.text.Normalizer.normalize(s, java.text.Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "");
        return n.toLowerCase().replaceAll("[^a-z0-9]+", " ").trim();
    }

    /** Similarità 0..1: 1 se uguali, alta se uno contiene l'altro, altrimenti rapporto di Levenshtein. */
    private static double similarity(String a, String b) {
        if (a.isEmpty() || b.isEmpty()) return 0;
        if (a.equals(b)) return 1.0;
        if (a.contains(b) || b.contains(a)) return 0.9;
        int max = Math.max(a.length(), b.length());
        return 1.0 - (double) levenshtein(a, b) / max;
    }

    private static int levenshtein(String a, String b) {
        int[] prev = new int[b.length() + 1];
        int[] cur = new int[b.length() + 1];
        for (int j = 0; j <= b.length(); j++) prev[j] = j;
        for (int i = 1; i <= a.length(); i++) {
            cur[0] = i;
            for (int j = 1; j <= b.length(); j++) {
                int cost = a.charAt(i - 1) == b.charAt(j - 1) ? 0 : 1;
                cur[j] = Math.min(Math.min(cur[j - 1] + 1, prev[j] + 1), prev[j - 1] + cost);
            }
            int[] tmp = prev; prev = cur; cur = tmp;
        }
        return prev[b.length()];
    }

    private Integer parseIntOrNull(String s) {
        if (s == null || s.isBlank()) return null;
        try { return Integer.parseInt(s.trim()); } catch (NumberFormatException e) { return null; }
    }

    private double parseDoubleOrDefault(String s, double def) {
        if (s == null || s.isBlank()) return def;
        try { return Double.parseDouble(s.trim().replace(',', '.')); } catch (NumberFormatException e) { return def; }
    }

    /** Accetta "YYYY-MM-DD" o un ISO LocalDateTime; default = oggi a mezzanotte. */
    private LocalDateTime parseDate(String s) {
        if (s == null || s.isBlank()) return LocalDate.now().atStartOfDay();
        String v = s.trim();
        try {
            if (v.contains("T")) return LocalDateTime.parse(v);
            return LocalDate.parse(v).atStartOfDay();
        } catch (Exception e) {
            return LocalDate.now().atStartOfDay();
        }
    }

    private String firstNonBlank(String a, String b) {
        if (a != null && !a.isBlank()) return a.trim();
        if (b != null && !b.isBlank()) return b.trim();
        return null;
    }
}
