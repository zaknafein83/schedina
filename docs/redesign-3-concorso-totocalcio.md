# Redesign #3 — Calendario per-lega, Concorso Totocalcio, Schedina utente

> Stato: **PROPOSTO / da implementare** (consolidato il 2026-05-30 dalle indicazioni dell'utente).
> Corregge un equivoco del redesign #2: lì la "Giornata" era la cosa giocabile e mista.
> Qui si separano nettamente **calendario**, **concorso giocabile** e **bollettino utente**.

## Decisioni consolidate (confermate dall'utente)

1. **Nomi**: la cosa giocabile del totocalcio si chiama **Concorso**; il coupon del singolo utente si chiama **Schedina**.
2. **Composizione del Concorso**: l'admin sceglie **a mano** le partite, prendendole **sempre dallo stesso numero di turno** (es. tutte le "giornata 1" delle varie leghe).
3. **Stato di gioco** (apertura/chiusura, regola/soglie) vive **solo sul Concorso**. La giornata di campionato è **solo calendario**, senza stato.
4. Le **partite** stanno sotto la **giornata di campionato** (per-lega); il Concorso le **referenzia** (non le sposta).
5. Le **scommesse "di partita"** si agganciano a una **partita** specifica; quelle di **fine campionato** alla stagione.

## Modello target

### Giornata di campionato (per-lega) — *solo calendario*
```
Giornata
├─ leagueId      lega di appartenenza (Serie A / B / C…)   [OBBLIGATORIO]
├─ seasonId      stagione (opzionale)
├─ number        numero di turno (1, 2, 3…)
└─ name          es. "Serie A — Giornata 1"
```
Contiene le **partite** di quella lega in quel turno. Niente stato/apertura/chiusura/regola.
Esistono in parallelo "Serie A g.1", "Serie B g.1", "Serie C g.1"…

### Match (partita)
```
Match
├─ giornataId    la giornata di campionato (per-lega) a cui appartiene
├─ leagueId      = giornata.leagueId (le due squadre stessa lega)
├─ homeTeamId / awayTeamId
├─ scheduledAt, overUnderLine (def 2.5)
├─ homeScore / awayScore, status
└─ concorsoId?   valorizzato se la partita è stata selezionata in un Concorso
```
`result1x2()` / `resultUO()` calcolati dal punteggio.

### Concorso (Totocalcio) — *la cosa giocabile*
```
Concorso
├─ number          turno di riferimento (le partite selezionate sono tutte di questo numero)
├─ name            es. "Totocalcio — Turno 1"
├─ seasonId?       stagione
├─ ruleId?         regola con le soglie vincenti
├─ openAt / closeAt
├─ status          DRAFT → OPEN → CLOSED → PROCESSED → CANCELLED
└─ winningThresholds  legacy/fallback se ruleId è null
```
Selezione partite: l'admin sceglie a mano N partite (di leghe diverse ma **stesso numero di turno**)
→ ogni Match selezionato ha `concorsoId = concorso.id`.

### Schedina (bollettino utente)
```
Schedina (una per utente per Concorso)
├─ userId, concorsoId, status, correctCount, isWinner, confirmedAt
└─ selezioni[]  → Selezione { matchId, choice1x2, choiceUo, correct1x2, correctUo }
```
Per ogni partita del concorso: **1X2 + Under/Over** (1 punto ciascuno). Vincita a soglia esatta (dalla regola del concorso).

### Scommesse + Giocate

Due meccaniche distinte:

**A) Scommesse di FINE CAMPIONATO (catalogo, create dall'admin)**
```
Scommessa  (solo scope SEASON)
├─ market, seasonId
├─ opzioni[] (BetOption) = candidati (squadre o giocatori)
├─ status (OPEN/RESOLVED/VOID), officialResultRef
Giocata  → { userId, scommessaId, choiceRef, isCorrect }
```
L'admin crea la scommessa con i candidati; l'utente sceglie un'opzione; l'admin **risolve a mano**
indicando il vincitore → le giocate vengono valutate.

**B) Scommesse DI PARTITA (guidate dall'utente, sempre disponibili)**
```
GiocataPartita  → { userId, matchId, market, prediction, isCorrect }
```
- **Nessuna** scommessa pre-creata dall'admin: per **ogni partita** di ogni giornata l'utente trova
  sempre i 4 tipi. Flusso: scegli **partita** → scegli **tipo** → inserisci **previsione**.
- Previsione per tipo: Gol/No gol = `GOAL`/`NOGOAL`; Vincitore = `teamId` (una delle due squadre);
  Risultato esatto = **punteggio libero** digitato (es. `2-1`); Primo marcatore = `playerId` (delle due squadre).
- **Risoluzione**: Gol/No gol, Vincitore e Risultato esatto si risolvono **in automatico dal punteggio**
  della partita; **Primo marcatore** lo inserisce l'**admin a mano** (chi ha segnato per primo).

**Mercati**
| Mercato | Scope | Target opzioni |
|---|---|---|
| Gol/No gol | MATCH | TOKEN (GOAL/NOGOAL), **auto** dal punteggio |
| Risultato esatto | MATCH | TOKEN |
| Vincitore | MATCH | TEAM (**solo le due squadre** della partita, niente X) |
| Primo marcatore | MATCH | PLAYER (giocatori delle due squadre della partita) |
| Capocannoniere | SEASON | PLAYER |
| Miglior assist | SEASON | PLAYER |
| Miglior portiere | SEASON | PLAYER (**solo portieri** nel picker) |
| Più clean sheet | SEASON | PLAYER (**solo portieri** nel picker) |
| Più gol fatti | SEASON | TEAM |
| Meno gol subiti | SEASON | TEAM |

> **Flusso scommesse di partita (utente)**: l'utente sceglie prima la **partita** (dall'elenco
> delle partite della giornata), poi il **tipo** di scommessa, poi la previsione. Non è l'admin a
> creare ogni singola scommessa di partita (vedi domande aperte sotto).

## Mapping dal modello attuale (redesign #2, in prod)

| Oggi (prod) | Nuovo |
|---|---|
| `Giornata` (mista, giocabile, con status/rule/open-close) | **Concorso** (rinominata + spostati lì status/rule/open-close/number=turno) |
| — | **Giornata di campionato** (NUOVA: leagueId + number + partite) |
| `Match.giornataId` → giornata mista | `Match.giornataId` → giornata **per-lega**; `+concorsoId` per la selezione |
| `Schedina.giornataId` | `Schedina.concorsoId` |
| `Scommessa.scope ∈ {SEASON, GIORNATA}` | `scope ∈ {SEASON, MATCH}` (le "di giornata" diventano legate alla partita) |

## Migrazione V20 (cutover, preserva anagrafiche)
- Crea `concorsi`; `giornate` += `league_id` (NOT NULL) e perde status/open/close/rule/thresholds (spostati su `concorsi`); `matches` += `concorso_id`; `schedine` rimpiazza `giornata_id` con `concorso_id`; `bets.scope` MATCH.
- Azzera i dati di gioco correnti (concorsi/schedine/partite di test), **preserva** leghe/squadre/giocatori/stagioni/regole.
- Forward-only.

## Piano di implementazione (fasi)
- **Fase 1** — Backend dominio: `Concorso` (new); `Giornata` → per-lega (leagueId+number, niente stato); `Match` +concorsoId; `Schedina` → concorsoId; `Scommessa.scope` SEASON|MATCH. Selezione partite nel concorso (a mano, stesso turno).
- **Fase 2** — Flyway **V20** (cutover, preserva anagrafiche).
- **Fase 3** — Engine: scoring schedina sui match del concorso; risoluzione scommesse SEASON/MATCH (gol/no-gol AUTO dal punteggio).
- **Fase 4** — REST: admin (calendario/giornate per-lega, partite, concorsi + selezione partite, scommesse, risoluzione) + user (concorso aperto + schedina, scommesse).
- **Fase 5** — Frontend: admin Calendario (giornate per-lega + partite), Concorsi (crea turno + seleziona partite), Scommesse (SEASON/MATCH, picker portieri filtrato); user Concorso/Schedina + Scommesse; guida + screenshot.
- **Fase 6** — Test (RestAssured + e2e Playwright) e deploy (preservando le anagrafiche).
