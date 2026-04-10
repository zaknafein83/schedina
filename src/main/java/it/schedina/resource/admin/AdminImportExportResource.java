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
    @Path("/export/rules")
    @Transactional
    public Response exportRules(@HeaderParam("Authorization") String token) {
        auth.requireAdmin(token);
        return Response.ok(Rule.<Rule>listAll().stream().map(this::ruleToMap).toList()).build();
    }

    @GET
    @Path("/export/contests")
    @Transactional
    public Response exportContests(@HeaderParam("Authorization") String token) {
        auth.requireAdmin(token);
        return Response.ok(Contest.<Contest>listAll().stream().map(this::contestToMap).toList()).build();
    }

    @GET
    @Path("/export/all")
    @Transactional
    public Response exportAll(@HeaderParam("Authorization") String token) {
        auth.requireAdmin(token);
        Map<String, Object> all = new LinkedHashMap<>();
        all.put("leagues",  League.<League>listAll().stream().map(this::leagueToMap).toList());
        all.put("teams",    Team.<Team>listAll().stream().map(this::teamToMap).toList());
        all.put("rules",    Rule.<Rule>listAll().stream().map(this::ruleToMap).toList());
        all.put("contests", Contest.<Contest>listAll().stream().map(this::contestToMap).toList());
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
    @Path("/import/rules")
    @Transactional
    public Response importRules(@HeaderParam("Authorization") String token, String body) {
        auth.requireAdmin(token);
        List<Map<String, String>> rows = parseBody(body);
        int count = 0;
        for (Map<String, String> row : rows) {
            String name = row.get("name");
            if (name == null || name.isBlank()) continue;
            Long leagueId = resolveLeagueId(row);
            if (leagueId == null) continue;
            Rule r = Rule.<Rule>find("name", name).firstResult();
            if (r == null) r = new Rule();
            r.name     = name;
            r.leagueId = leagueId;
            if (row.containsKey("description"))          r.description          = row.get("description");
            if (hasValue(row, "requiredMatches"))         r.requiredMatches      = Integer.parseInt(row.get("requiredMatches"));
            if (hasValue(row, "winningThresholds"))       r.winningThresholds    = parseIntList(row.get("winningThresholds"));
            if (hasValue(row, "maxDoubles"))              r.maxDoubles           = Integer.parseInt(row.get("maxDoubles"));
            if (hasValue(row, "maxTriples"))              r.maxTriples           = Integer.parseInt(row.get("maxTriples"));
            if (hasValue(row, "maxCouponsPerUser"))       r.maxCouponsPerUser    = Integer.parseInt(row.get("maxCouponsPerUser"));
            if (row.containsKey("fullCompletionRequired"))r.fullCompletionRequired = Boolean.parseBoolean(row.get("fullCompletionRequired"));
            if (row.containsKey("isActive"))             r.isActive             = Boolean.parseBoolean(row.get("isActive"));
            r.persist();
            count++;
        }
        return Response.ok(Map.of("imported", count, "entity", "rules")).build();
    }

    @POST
    @Path("/import/contests")
    @Transactional
    public Response importContests(@HeaderParam("Authorization") String token, String body) {
        auth.requireAdmin(token);
        List<Map<String, String>> rows = parseBody(body);
        int count = 0;
        for (Map<String, String> row : rows) {
            String name = row.get("name");
            if (name == null || name.isBlank()) continue;
            Long leagueId = resolveLeagueId(row);
            Long ruleId   = resolveRuleId(row);
            if (leagueId == null || ruleId == null) continue;
            Contest c = Contest.<Contest>find("name", name).firstResult();
            if (c == null) c = new Contest();
            c.name     = name;
            c.leagueId = leagueId;
            c.ruleId   = ruleId;
            if (row.containsKey("description")) c.description = row.get("description");
            if (hasValue(row, "openAt"))  c.openAt  = LocalDateTime.parse(normalizeDateTime(row.get("openAt")));
            if (hasValue(row, "closeAt")) c.closeAt = LocalDateTime.parse(normalizeDateTime(row.get("closeAt")));
            if (c.status == null) c.status = Contest.Status.DRAFT;
            c.persist();
            count++;
        }
        return Response.ok(Map.of("imported", count, "entity", "contests")).build();
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // MAP HELPERS
    // ═══════════════════════════════════════════════════════════════════════════

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

    private Map<String, Object> ruleToMap(Rule r) {
        League l = League.findById(r.leagueId);
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", r.id); m.put("name", r.name); m.put("description", r.description);
        m.put("leagueId", r.leagueId); m.put("leagueName", l != null ? l.name : null);
        m.put("requiredMatches", r.requiredMatches); m.put("winningThresholds", r.winningThresholds);
        m.put("maxCouponsPerUser", r.maxCouponsPerUser); m.put("maxDoubles", r.maxDoubles);
        m.put("maxTriples", r.maxTriples); m.put("fullCompletionRequired", r.fullCompletionRequired);
        m.put("isActive", r.isActive);
        return m;
    }

    private Map<String, Object> contestToMap(Contest c) {
        League l = League.findById(c.leagueId);
        Rule   r = Rule.findById(c.ruleId);
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", c.id); m.put("name", c.name); m.put("description", c.description);
        m.put("leagueId", c.leagueId); m.put("leagueName", l != null ? l.name : null);
        m.put("ruleId",   c.ruleId);   m.put("ruleName",   r != null ? r.name : null);
        m.put("openAt", c.openAt); m.put("closeAt", c.closeAt); m.put("status", c.status);
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

    private Long resolveRuleId(Map<String, String> row) {
        if (hasValue(row, "ruleId")) {
            try { return Long.parseLong(row.get("ruleId")); } catch (NumberFormatException ignored) {}
        }
        if (hasValue(row, "ruleName")) {
            Rule r = Rule.<Rule>find("name", row.get("ruleName")).firstResult();
            return r != null ? r.id : null;
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

    private String normalizeDateTime(String s) {
        // Remove trailing Z, normalize to LocalDateTime format
        return s.replace("Z", "").replace(" ", "T").substring(0, 19);
    }
}
