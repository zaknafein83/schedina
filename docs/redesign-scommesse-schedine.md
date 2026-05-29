# Redesign — Separazione Scommesse / Schedine

> Stato: **backend implementato (Fasi 1–4 + test) — frontend (Fase 5) da fare**
> Data: 2026-05-29
> Obiettivo: promuovere la **Scommessa** a entità di prima classe, indipendente dalla
> **Schedina**, e fondere i due sistemi attuali (concorsi a giornata + stagionali) in
> un unico modello.

---

## 1. Motivazione

Nel modello attuale "scommessa" e "schedina" sono accoppiate e la scommessa **non esiste
come concetto autonomo**:

- Una scommessa 1X2/UO **è** il `Match` (`betType` e `officialResult` vivono sulla partita).
- I side bet (`MatchSidePrediction`) sono attaccati al match.
- Il `CouponEngine` fa tre lavori insieme: valida la schedina, risolve la scommessa
  (`computeResult` sul Match) e calcola il punteggio.
- Le scommesse non legate a una singola partita (scudetto, capocannoniere) hanno richiesto
  un **secondo sistema intero** (`Season*`), con logica ed entità duplicate.

Conseguenza: ~10 entità, 2 engine, due aree di gestione parallele.

---

## 2. Decisioni di design (approvate)

| Tema | Decisione |
|---|---|
| **Logica di vincita** | Match esatto: `winningThresholds.contains(correctCount)` (invariato) |
| **Punteggio** | Ogni selezione corretta vale **1**, nessun peso per scommessa |
| **Scelta multipla** | **Rimossa**: scelta singola ovunque. Spariscono doppie/triple, `maxDoubles`, `maxTriples` |
| **Unificazione sistemi** | I due sistemi (giornata + stagionale) collassano in uno |
| **Migrazione dati prod** | **Cutover pulito**: i dati di gioco sono di test → si azzerano. Si preservano utenti e anagrafiche (leghe, squadre, giocatori, stagioni, tornei) |

---

## 3. Modello target

### 3.1 `Match` — il fatto sportivo
Resta, ma **non è più "la scommessa"**: solo l'incontro reale (squadre, orario, punteggio).
Inserire il punteggio è un evento che può **risolvere automaticamente** le scommesse legate.

### 3.2 `Scommessa` (Bet) — entità di prima classe ⭐
```
Scommessa
├─ id
├─ label                es. "Milan–Inter · Esito", "Capocannoniere Serie A"
├─ market               RESULT_1X2 | UNDER_OVER | GOAL_NOGOAL | FIRST_SCORER |
│                       WINNER | TOP_SCORER | TOP_ASSIST | CLEAN_SHEET_TEAM |
│                       BEST_GOALKEEPER | MOST_GOALS_FOR | LEAST_GOALS_AGAINST
├─ opzioni[]            le scelte ammesse (token "1/X/2/U/O", Team, o Player)
├─ contesto             matchId (opz.) | tournamentId (opz.) | seasonId (opz.) | leagueId
├─ overUnderLine        soglia U/O (solo market UNDER_OVER)
├─ resolutionMode       AUTO (calcolato da punteggio match) | MANUAL (admin sceglie il vincitore)
├─ status               OPEN → RESOLVED  (+ VOID se la partita salta / scommessa annullata)
├─ officialResultRef    opzione vincente; null finché OPEN
├─ resolvedAt
└─ concorsoId           a quale concorso appartiene (opz. finché non assegnata)
```

**Markets** — ogni market definisce *target kind*, *come si risolve*, *come si generano le opzioni*:

| Market | Target | Risoluzione | Opzioni |
|---|---|---|---|
| RESULT_1X2 | token | AUTO da score | 1 / X / 2 |
| UNDER_OVER | token | AUTO da score | U / O (rispetto a `overUnderLine`) |
| GOAL_NOGOAL | token | MANUAL | GOAL / NOGOAL |
| FIRST_SCORER | player | MANUAL | giocatori candidati (+ NONE) |
| WINNER | team | MANUAL | squadre del torneo |
| TOP_SCORER / TOP_ASSIST / BEST_GOALKEEPER | player | MANUAL | giocatori candidati |
| CLEAN_SHEET_TEAM / MOST_GOALS_FOR / LEAST_GOALS_AGAINST | team | MANUAL | squadre del torneo |

### 3.3 `Concorso` — contenitore (fonde `Contest` + `SeasonPool`)
```
Concorso
├─ id, name, description
├─ kind                 MATCHDAY | SEASON   (puramente descrittivo/di filtro)
├─ ruleId
├─ openAt / closeAt
├─ status               DRAFT → OPEN → CLOSED → PROCESSED  (+ CANCELLED)
└─ (le Scommesse puntano qui via concorsoId)
```

### 3.4 `Schedina` (Coupon) — sola raccolta
```
Schedina
├─ id, userId, concorsoId
├─ status               DRAFT → CONFIRMED → WINNING | NOT_WINNING  (+ CANCELLED)
├─ correctCount, isWinner, confirmedAt
└─ selezioni[]
       └─ Selezione { scommessaId, choiceRef (singola), isCorrect }
```

### 3.5 `Regola` (Rule) — semplificata
```
Regola
├─ requiredBets         quante scommesse deve coprire la schedina
├─ winningThresholds    punteggi vincenti (match esatto)
├─ maxSchedinePerUser
└─ fullCompletionRequired
   (RIMOSSI: maxDoubles, maxTriples)
```

---

## 4. Motore di punteggio (unico, idempotente multi-step)

Si adotta il modello del `SeasonCouponEngine` (il più robusto), generalizzato:

1. Le scommesse si risolvono **una alla volta** man mano che arrivano i risultati
   (AUTO all'inserimento del punteggio match; MANUAL via azione admin).
2. `process(concorso)` è **idempotente**: per ogni schedina, per ogni selezione,
   se la scommessa è `RESOLVED` → `isCorrect = (officialResultRef == choiceRef)` e somma +1;
   le selezioni di scommesse ancora `OPEN` restano `isCorrect = null`.
3. Il concorso diventa `PROCESSED` solo quando **tutte** le scommesse sono risolte;
   allora `isWinner = rule.winningThresholds.contains(correctCount)`.
4. I side bet **non sono più un caso speciale**: sono semplicemente altre Scommesse del concorso.

---

## 5. Mapping vecchio → nuovo

| Oggi | Nel nuovo modello |
|---|---|
| `Match` con `betType` 1X2/UO | `Scommessa` market RESULT_1X2/UNDER_OVER, `matchId` set, AUTO |
| `MatchSidePrediction` (gol/nogol, marcatore) | `Scommessa` con quei market, `matchId` set, MANUAL |
| `SeasonBet` (scudetto, capocannoniere...) | `Scommessa` con quei market, `tournamentId` set, MANUAL |
| `Contest` / `SeasonPool` | `Concorso` (kind MATCHDAY / SEASON) |
| `Coupon`+`CouponPrediction`+`CouponSidePrediction` | `Schedina` + `Selezione` |
| `SeasonCoupon`+`SeasonCouponPrediction` | `Schedina` + `Selezione` |
| `CouponEngine` + `SeasonCouponEngine` | un solo `ScommessaResolutionService` + `SchedinaScoringEngine` |

Netto: **2 engine → 1**, **~10 entità → ~5**.

---

## 6. Aree di gestione (separazione richiesta)

- **Gestione Scommesse** (admin/mod): catalogo delle scommesse — crea, configura opzioni,
  assegna a un concorso, **risolve** (inserisce risultato). Indipendente dalle schedine.
- **Gestione Concorsi** (admin/mod): raggruppa scommesse, apre/chiude, lancia il processing.
- **Gestione Schedine** (utente): seleziona scommesse dei concorsi aperti, conferma.
- **Risultati** scommesse AUTO: derivati dall'inserimento del punteggio sul `Match`.

---

## 7. Piano di implementazione

> Approccio: **cutover pulito**. Non servono migrazioni di conversione dati; si rimpiazza
> lo schema di gioco con una migrazione forward-only, preservando utenti e anagrafiche.
>
> **Avanzamento:** Fasi 0–4 ✅ fatte sul branch `feat/redesign-scommesse-schedine`.
> Fase 6 ✅ test e2e scritti e verdi (`mvn test` su Postgres reale, 2/2 pass).
> Fase 5 (frontend) ⬜ da fare. Deploy del cutover ⬜ da fare.

### Fase 0 — Verifica DB prod (rapida) ✅ FATTA (2026-05-29)
- Esito: DB **privo di dati reali**. Solo seed: `users`=4 (V3/V6/V7), `seasons`=1 e
  `tournaments`=5 (V16). `leagues`/`teams`/`players` e tutte le tabelle di gioco = **0**.
- Cutover pulito confermato senza rischi: la V17 può droppare/ricreare tutto lo schema di
  gioco. Si preservano i 4 utenti solo per comodità (resta l'admin di login).

### Fase 1 — Backend: nuovo dominio
- Nuove entità: `Scommessa`, `BetOption`, `Concorso`, `Schedina`, `Selezione`.
- `Rule` semplificata (rimuovere `maxDoubles`/`maxTriples`).
- Ritirare: `Contest`, `Coupon`, `CouponPrediction`, `CouponSidePrediction`,
  `MatchSidePrediction`, `SeasonPool`, `SeasonBet`, `SeasonCoupon`, `SeasonCouponPrediction`.
- `Match` mantenuto (alleggerito: via `betType`/`overUnderLine`/`officialResult`,
  che migrano sulla Scommessa).

### Fase 2 — Flyway V17 (cutover)
- `DROP` tabelle di gioco vecchie; `CREATE` nuovo schema (`bets`, `bet_options`,
  `concorsi`, `schedine`, `schedina_selezioni`, `rules` aggiornata).
- **Non** toccare `users` e anagrafiche. **Non** modificare V1–V16 (storia Flyway immutabile).
- Reseed minimo (eventuale concorso demo) idempotente.

### Fase 3 — Engine unificato
- `ScommessaResolutionService`: risoluzione AUTO (hook da inserimento score match) e MANUAL.
- `SchedinaScoringEngine`: `createSchedina`, `confirm`, `process` (idempotente multi-step).

### Fase 4 — REST API
- Admin: `/admin/scommesse`, `/admin/concorsi`, `/admin/schedine`, risoluzione scommesse.
- User: `/concorsi`, `/schedine`, `/my-schedine`.
- Rifare i DTO coinvolti; rimuovere quelli obsoleti (`CouponDto`, `SeasonCouponDto`, ecc.).

### Fase 5 — Frontend (repo `schedina-frontend`)
- Nuove aree admin **Scommesse** e **Schedine** separate.
- Aggiornare flusso utente (compilazione schedina su scommesse, scelta singola).
- Rimuovere UI doppie/triple e le pagine del vecchio sistema stagionale.

### Fase 6 — Test + deploy
- Test backend (oggi assenti): engine di punteggio, validazione, risoluzione.
- Deploy: BE via CI/GHCR; per il cutover, **azzerare il volume `pgdata`** o lasciare
  che la V17 droppi/ricrei. FE via rsync CI.

---

## 8. Rischi / note
- Lo schema di gioco viene rimpiazzato: irreversibile sui dati di gioco (accettato: sono test).
- Gli **utenti restano** → il `JWT_SECRET` e gli hash bcrypt non vanno toccati.
- Flyway è forward-only: la cutover è una nuova migrazione, non una riscrittura di V1–V16.
- Cfr. nota deploy: `/opt/schedina/.env`, dominio `fantarole.zaknafein.ovh`, CORS.
