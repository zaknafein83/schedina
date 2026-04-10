# Schedina Sportiva

Piattaforma per la gestione di concorsi di pronostici sportivi ispirata al Totocalcio. Gli utenti compilano schedine scegliendo 1/X/2 per ogni partita; il sistema calcola i punteggi, assegna premi e invia notifiche ai vincitori.

---

## Funzionalità

### Utenti
- Registrazione, login, recupero password
- Visualizzazione dei concorsi aperti con countdown alla chiusura
- Compilazione schedine con supporto a **doppia** (2 scelte) e **tripla** (3 scelte) sulla stessa partita
- Gestione bozze: salva, modifica e conferma la schedina prima della chiusura
- Storico schedine personali con punteggio e stato
- Notifiche in-app per vincite e risultati

### Amministratori
- Dashboard con statistiche in tempo reale (utenti, schedine, concorsi, notifiche)
- Gestione **Leghe**: crea e modifica leghe sportive con paese
- Gestione **Squadre**: associa squadre alle leghe
- Gestione **Regole**: configura partite richieste, soglie di vincita, max doppi/tripli
- Gestione **Concorsi**: crea concorsi con lega, regola, date di apertura/chiusura; apri, chiudi e processa i risultati
- Gestione **Partite**: assegna partite ai concorsi e inserisci i risultati ufficiali (1/X/2)
- Gestione **Utenti**: abilita/disabilita account, cambia ruolo (ADMIN / MOD / USER)
- Gestione **Notifiche**: monitora lo stato delle notifiche e reinvia quelle fallite

---

## Flusso operativo

### Ruoli

| Ruolo | Chi è | Cosa può fare |
|-------|-------|---------------|
| **ADMIN** | Gestore della piattaforma | Tutto: configurazione, concorsi, risultati, utenti |
| **MOD** | Moderatore | Inserimento risultati partite, gestione concorsi (da implementare lato permessi) |
| **USER** | Giocatore | Compilare e confermare schedine, vedere risultati e notifiche |

---

### Fase 1 — Configurazione iniziale (ADMIN, una tantum)

Queste operazioni vanno fatte la prima volta prima di creare qualsiasi concorso.

```
Admin → Leghe       → Crea le leghe (es. "Serie A", "Premier League")
Admin → Squadre     → Crea le squadre e associale alle leghe
Admin → Regole      → Crea le regole di gioco per ogni lega
                       (partite richieste, soglie vincita, max doppi/tripli)
```

**Esempio regola "Schedina 13":**
- Lega: Serie A
- Partite richieste: 13
- Soglie vincita: `11, 12, 13` (vince chi indovina almeno 11 su 13)
- Max doppi: 2, Max tripli: 1

---

### Fase 2 — Creazione concorso (ADMIN)

```
Admin → Concorsi → "Nuovo concorso"
  ├── Seleziona lega e regola
  ├── Imposta data apertura e data chiusura
  └── Stato iniziale: DRAFT (non ancora visibile agli utenti)

Admin → Concorsi → [concorso] → Aggiungi partite
  └── Associa le partite (home team vs away team + data/ora)

Admin → Concorsi → [▶ Apri concorso]
  └── Stato passa a OPEN → il concorso è visibile agli utenti
```

> ⚠️ Le partite vanno aggiunte **prima** di aprire il concorso. Una volta aperto gli utenti possono già compilare.

---

### Fase 3 — Compilazione schedina (USER)

```
Utente → Concorsi → Seleziona un concorso aperto
  └── Per ogni partita seleziona: 1 (casa) / X (pareggio) / 2 (trasferta)
       Doppia: seleziona 2 risultati sulla stessa partita
       Tripla:  seleziona tutti e 3 i risultati

Utente → "Salva bozza"      → schedina salvata ma modificabile
Utente → "Conferma schedina" → schedina bloccata, non più modificabile
```

> La schedina deve essere **confermata** prima della chiusura del concorso per essere valida.

---

### Fase 4 — Inserimento risultati e chiusura (ADMIN / MOD)

Dopo che le partite si sono giocate:

```
Admin → Concorsi → [■ Chiudi concorso]
  └── Stato passa a CLOSED → nessun nuovo utente può più confermare schedine

Admin → Concorsi → [concorso] → Partite
  └── Per ogni partita inserisci il risultato ufficiale: 1 / X / 2
```

> I risultati possono essere inseriti anche prima della chiusura man mano che le partite finiscono.

---

### Fase 5 — Elaborazione e premi (ADMIN)

```
Admin → Concorsi → [⚙ Processa concorso]
  └── Il sistema:
       1. Calcola il punteggio di ogni schedina confermata
       2. Confronta con le soglie della regola
       3. Marca le schedine vincenti
       4. Genera le notifiche per i vincitori
       └── Stato passa a PROCESSED
```

---

### Fase 6 — Ricezione premi (USER)

```
Utente → Le mie schedine → Vede punteggio e stato (SCORED / WINNER)
Utente → Notifiche       → Riceve il messaggio di vincita con dettagli
```

---

### Riepilogo ciclo di vita di un concorso

```
DRAFT → OPEN → CLOSED → PROCESSED
  │       │       │          │
  │    Utenti   Nessuna    Risultati
Nessuno compilano  nuova    calcolati
visibile schedine  schedina  e notifiche
```

---

### Riepilogo ciclo di vita di una schedina

```
DRAFT → CONFIRMED → SCORED → WINNER (se vince)
  │          │          │
Modificabile Bloccata  Punteggio
             valida    assegnato
```

---

## Stack tecnologico

| Layer | Tecnologia |
|-------|-----------|
| Backend | Java 21 + Quarkus 3.12 |
| ORM | Hibernate ORM Panache |
| Database | PostgreSQL 15 |
| Migrazioni | Flyway |
| Autenticazione | JWT (JJWT 0.12) + BCrypt |
| Frontend | React 18 + Vite 5 |
| Routing | React Router v6 |
| Data fetching | TanStack Query v5 |
| Form | React Hook Form |
| Stile | Tailwind CSS v3 |
| Containerizzazione | Docker + Docker Compose |

---

## Struttura del progetto

```
schedina/              ← Backend (Quarkus)
├── src/main/java/it/schedina/
│   ├── entity/        ← Entità JPA (User, Contest, Coupon, Match, …)
│   ├── dto/           ← Record request/response
│   ├── resource/
│   │   ├── admin/     ← Endpoint admin (/admin/*)
│   │   └── user/      ← Endpoint utente (/contests, /coupons, /notifications)
│   └── service/       ← AuthService, JwtService, CouponEngine
├── src/main/resources/
│   ├── application.properties
│   └── db/migration/  ← Script Flyway (V1…V4)
├── Dockerfile
└── docker-compose.yml

schedina-frontend/     ← Frontend (React)
├── src/
│   ├── api/client.js  ← Axios + tutti gli endpoint
│   ├── context/       ← AuthContext (token, user, login/logout)
│   ├── components/    ← Layout, AdminLayout, ProtectedRoute, UI primitivi
│   └── pages/
│       ├── auth/      ← Login, Register, ForgotPassword, ResetPassword
│       ├── user/      ← Contests, ContestDetail, MyCoupons, Notifications
│       └── admin/     ← Dashboard, Contests, Leagues, Teams, Rules, Users, Notifications
├── Dockerfile
└── nginx.conf
```

---

## Avvio in sviluppo locale

### Prerequisiti
- Docker Desktop installato e avviato
- Java 21 (JDK)
- Maven 3.9+
- Node.js 20+

### 1 — Avvia il database

```bash
cd schedina
docker compose up db -d
```

PostgreSQL sarà disponibile su `localhost:5432`. Il volume `pgdata` garantisce la persistenza dei dati tra i restart.

### 2 — Avvia il backend

```bash
cd schedina
mvn quarkus:dev
```

Al primo avvio Flyway esegue automaticamente le migrazioni (schema + utente admin di default).  
Il backend sarà disponibile su `http://localhost:8080`.

> In modalità `quarkus:dev` il backend si ricompila automaticamente ad ogni modifica al codice Java.

### 3 — Avvia il frontend

```bash
cd schedina-frontend
npm install      # solo la prima volta
npm run dev
```

Il frontend sarà disponibile su `http://localhost:5173`.

---

## Credenziali di default

| Campo | Valore |
|-------|--------|
| Email | `admin@schedina.it` |
| Password | `password` |
| Ruolo | `ADMIN` |

> Cambia la password al primo accesso tramite Admin → Utenti oppure usando il flusso *Password dimenticata*.

---

## Variabili d'ambiente (Backend)

| Variabile | Default | Descrizione |
|-----------|---------|-------------|
| `DB_URL` | `jdbc:postgresql://localhost:5432/schedina` | URL JDBC del database |
| `DB_USER` | `schedina` | Utente PostgreSQL |
| `DB_PASSWORD` | `schedina` | Password PostgreSQL |
| `JWT_SECRET` | `dev-secret-key-…` | Chiave HMAC per i JWT (min 32 caratteri) |

## Variabili d'ambiente (Frontend)

| Variabile | Default | Descrizione |
|-----------|---------|-------------|
| `VITE_API_URL` | `http://localhost:8080` | URL base del backend |

Copia `.env.example` in `.env` e modifica i valori prima del deploy.

---

## Deploy con Docker Compose (produzione)

```bash
# Backend + DB
cd schedina
JWT_SECRET=cambia-questo-segreto-sicuro docker compose up --build -d

# Frontend (da cartella separata)
cd schedina-frontend
docker build -t schedina-frontend .
docker run -d -p 3000:80 \
  -e VITE_API_URL=https://api.schedina.tuodominio.it \
  schedina-frontend
```

### Configurazione nginx (host)

Aggiungi due `server` block al tuo nginx esistente:

```nginx
server {
    server_name api.schedina.tuodominio.it;
    location / {
        proxy_pass http://localhost:8080;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
    }
}

server {
    server_name schedina.tuodominio.it;
    location / {
        proxy_pass http://localhost:3000;
        proxy_set_header Host $host;
    }
}
```

Poi abilita HTTPS con Certbot:

```bash
certbot --nginx -d api.schedina.tuodominio.it -d schedina.tuodominio.it
```

---

## API — Panoramica endpoint

### Autenticazione
| Metodo | Path | Descrizione |
|--------|------|-------------|
| POST | `/auth/register` | Registrazione |
| POST | `/auth/login` | Login → JWT |
| GET | `/auth/me` | Profilo utente corrente |
| POST | `/auth/forgot-password` | Genera token reset |
| POST | `/auth/reset-password` | Imposta nuova password |

### Utente
| Metodo | Path | Descrizione |
|--------|------|-------------|
| GET | `/contests` | Concorsi aperti |
| GET | `/contests/:id/matches` | Partite del concorso |
| GET/POST | `/coupons` | Lista / crea schedina |
| POST | `/coupons/:id/confirm` | Conferma schedina |
| DELETE | `/coupons/:id` | Cancella bozza |
| GET | `/notifications` | Notifiche utente |
| POST | `/notifications/:id/read` | Segna come letta |

### Admin
Tutti gli endpoint `/admin/*` richiedono un token con ruolo `ADMIN`.  
Includono CRUD completo per leghe, squadre, regole, concorsi, partite, utenti e notifiche.
