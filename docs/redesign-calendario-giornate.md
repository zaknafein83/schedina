# Redesign #2 — Calendario, Giornate, Schedina vs Scommesse extra

> Stato: **✅ COMPLETATO e DEPLOYATO IN PRODUZIONE (2026-05-30).** Tutte le fasi 0-6 chiuse.
> Data: 2026-05-29 (design) · 2026-05-30 (frontend + deploy)
> Separa due giochi: la **Schedina** (1X2 + U/O su ogni giornata del calendario)
> e le **Scommesse extra** (fine stagione o di giornata), giocate con un flow diverso.

## Decisioni approvate

| Tema | Decisione |
|---|---|
| **Calendario** | Unico; ogni **giornata** è mista (partite anche di divisioni diverse A/B/C). Una sola schedina per giornata. |
| **Schedina** | Per ogni partita della giornata: **1X2 + Under/Over** (due pronostici a partita), entrambi a punteggio. |
| **Scommesse extra** | Giocate **singole e indipendenti** (una previsione per scommessa, vinta/persa a sé). Scope: **fine stagione** o **di giornata**. |

---

## Modello target

### Calendario / Giornata (nuovo)
```
Giornata
├─ seasonId        stagione di riferimento
├─ number          numero di giornata (1, 2, 3…)
├─ name            es. "Giornata 1"
├─ openAt / closeAt  finestra di gioco della schedina
└─ status          DRAFT → OPEN → CLOSED → PROCESSED
```
Il **calendario** = elenco ordinato delle giornate della stagione corrente.

### Match (modificato)
Aggiunge `giornataId` e `overUnderLine` (soglia U/O della partita, default 2.5).
Resta il vincolo: le due squadre di una partita stessa divisione; la giornata
può mischiare divisioni. `homeScore`/`awayScore` invariati.

### Schedina (gioco principale) — 1X2 + U/O
```
Schedina  (una per utente per giornata)
├─ userId, giornataId, status, correctCount, isWinner, confirmedAt
└─ selezioni[]
       └─ Selezione { matchId, market (RESULT_1X2 | UNDER_OVER), choiceRef, isCorrect }
```
- Auto-strutturata dalle partite della giornata: per ogni partita servono 2 pronostici (1X2 e U/O).
- Punteggio: +1 per ogni selezione corretta. `isWinner = winningThresholds.contains(correctCount)`.
- Risoluzione: inserito il punteggio della partita, 1X2 e U/O si risolvono in automatico.
- `winningThresholds` sulla Giornata (niente più Rule generica per la schedina).

### Scommesse extra (catalogo) + Giocate
```
Scommessa  (catalogo, gestione separata)
├─ scope           SEASON (fine stagione) | GIORNATA (di giornata)
├─ seasonId | giornataId    secondo lo scope
├─ market          GOAL_NOGOAL | FIRST_SCORER | WINNER | TOP_SCORER | ...
├─ matchId (opz.)  se legata a una partita specifica
├─ opzioni[], resolutionMode, status, officialResultRef
Giocata  (la singola puntata di un utente)
└─ { userId, scommessaId, choiceRef, isCorrect, createdAt }
```
- Flow utente **separato** dalla schedina: l'utente vede le scommesse aperte (fine stagione + di giornata) e ne gioca quante vuole, una previsione ciascuna.
- Vinta/persa indipendentemente: alla risoluzione della scommessa, ogni Giocata è corretta se `choiceRef == officialResultRef`.

### Rimosso
`Concorso` (sostituito da Giornata). La `Rule` generica non serve più per la
schedina (le soglie vivono sulla Giornata); valutare se tenerla per altro.

---

## Mapping dal modello attuale (redesign #1)

| Oggi | Nuovo |
|---|---|
| `Concorso` (MATCHDAY/SEASON) | **Giornata** (per la schedina) |
| `Scommessa` 1X2/UO dentro un concorso | derivate dalle partite della **Giornata** (non più entità separate per la schedina) |
| `Scommessa` stagionale/altro | **Scommessa** del catalogo extra (scope SEASON/GIORNATA) |
| `Schedina`+`Selezione` (scelta singola su scommesse) | **Schedina**+`Selezione` (1X2+UO per partita della giornata) |
| — | **Giocata** (nuova: puntata singola su scommessa extra) |

---

## Flussi

**Admin/Mod**
1. Crea le **Giornate** del calendario (stagione corrente).
2. Per ogni giornata aggiunge le **partite** (casa/ospite per divisione, soglia U/O).
3. Apre la giornata → utenti compilano la schedina.
4. (Catalogo **Scommesse extra**) crea scommesse fine-stagione o di-giornata.
5. Inserisce i punteggi → schedine 1X2/UO si risolvono; **Elabora** la giornata.
6. Risolve le **scommesse extra** → le Giocate vengono valutate.

**Utente**
- **Schedina**: sceglie la giornata aperta, pronostica 1X2 + U/O per ogni partita, conferma.
- **Scommesse**: pagina separata con le scommesse aperte (fine stagione / di giornata); gioca le previsioni che vuole, una per scommessa.

---

## Piano di implementazione

> Migrazione: **preserva le anagrafiche** già caricate in prod (leghe, squadre,
> giocatori, stagioni, tornei). Azzera i dati di gioco correnti (concorsi/schedine
> di test). Cutover forward-only (Flyway V18).

- [x] **Fase 0** — verifica DB prod (cosa preservare vs azzerare).
- [x] **Fase 1** — Backend dominio: `Giornata` (new), `Giocata` (new); `Match` (+giornataId, +overUnderLine); `Schedina`→giornataId, `Selezione`→(matchId, market); `Scommessa` riscope (SEASON/GIORNATA); rimuovere `Concorso`.
- [x] **Fase 2** — Flyway **V18**: preserva anagrafiche, ricrea tabelle di gioco (giornate, schedine, selezioni, scommesse, giocate; matches +colonne).
- [x] **Fase 3** — Engine: `SchedinaScoringEngine` (giornata, 1X2+UO da punteggio) + risoluzione scommesse extra con scoring delle Giocate.
- [x] **Fase 4** — REST: admin (calendario/giornate, partite, scommesse extra, risoluzione) + user (schedina per giornata, scommesse extra).
- [x] **Fase 5** — Frontend: admin Calendario/Giornate + catalogo Scommesse; user Schedina (per giornata) + pagina Scommesse separata; guida + screenshot.
- [x] **Fase 6** — Test (integrazione BE + e2e Playwright) e deploy (preservando le anagrafiche).

---

## Cronologia / esito

**2026-05-29** — Backend (Fasi 0-4) implementato, committato e testato sul branch
`feat/calendario-giornate` (commit `18dd472`).

**2026-05-30** — Frontend (Fase 5) + test e deploy (Fase 6):

- **Frontend** (repo `schedina-frontend`, branch `feat/calendario-giornate`):
  - Admin: `Giornate` (CRUD + open/close/process, soglie vincenti), `GiornataDetail`
    (partite multi-divisione + inserimento punteggio/validazione, lista schedine),
    `Scommesse` (catalogo SEASON/GIORNATA, mercati TOKEN/TEAM/PLAYER, resolve/void),
    `Schedine` per giornata, `Dashboard` aggiornata.
  - User: `Giornate` aperte, `GiornataDetail` (1X2 + U/O obbligatori su tutte le partite),
    `Scommesse` (gioca + le mie giocate), `Schedine`. Componente `SchedinaSelezioni`.
  - `client.js` riallineato; routing/layout (Calendario + Scommesse, rimosso Regole);
    rimossi Concorso/Rules. Guida riscritta + screenshot ricatturati in `public/aiuto/`.
- **Test e2e Playwright full-stack: 16/16 verdi** contro `docker-compose.test.yml`
  (BE:8081, PG:5434, FE:5174). `seed.ts`/`flow.spec.ts`/`auth.spec.ts` riallineati.
- **Deploy in produzione** (`https://fantarole.zaknafein.ovh`): PR BE #6 + PR FE #4
  mergiate su `main` (`gh pr merge --admin`), CI deploy OK.
  - **V18 applicata**: dati di gioco azzerati; **anagrafiche preservate** (verificato:
    3 leghe, 53 squadre, 980 giocatori).
  - Endpoint verificati: `/api/admin/giornate` → 200 (login admin); vecchi
    `/api/(admin/)concorsi` → 404; FE HTTPS 200.

> Follow-up minore (non bloccante): la CI di entrambi i repo usa ancora action su
> Node.js 20 (checkout/setup-java/docker) — da bumpare prima di giugno 2026.
