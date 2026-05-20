# -*- coding: utf-8 -*-
"""
Genera il manuale utente PDF di Schedina con screenshot reali.
Output: scripts/Manuale_Utente_Schedina.pdf
"""

import sys, os
sys.stdout.reconfigure(encoding="utf-8")

from pathlib import Path
from fpdf import FPDF
from PIL import Image as PILImage

SHOTS = Path(__file__).parent / "screenshots"
OUT   = Path(__file__).parent / "Manuale_Utente_Schedina.pdf"

# ─── palette ──────────────────────────────────────────────────────────────────
RED    = (196, 22,  46)
DARK   = (30,  30,  30)
LIGHT  = (245, 245, 245)
WHITE  = (255, 255, 255)
GRAY   = (120, 120, 120)

# ─── struttura del manuale ────────────────────────────────────────────────────
SECTIONS = [
    # (titolo_sezione, titolo_pagina, file_screenshot, testo_descrizione)

    # ── INTRODUZIONE ──────────────────────────────────────────────────────────
    ("ACCESSO AL SISTEMA", "Login", "01_login_vuoto.png",
     "La pagina di accesso è il punto di ingresso all'applicazione.\n\n"
     "- Inserire l'indirizzo email e la password nei campi corrispondenti\n"
     "- Premere il pulsante rosso «Accedi» per entrare\n"
     "- In caso di password dimenticata, usare il link «Password dimenticata?\"\n"
     "- Se non si possiede ancora un account, cliccare «Registrati»\n\n"
     "Credenziali di test disponibili:\n"
     "  Admin  →  admin@schedina.it  /  12345678\n"
     "  Mod    →  mod@schedina.it    /  mod1234\n"
     "  Utente →  mario@schedina.it  /  user1234\n\n"
     "Dopo l'accesso, il sistema reindirizza automaticamente all'area corretta "
     "in base al ruolo dell'utente (Admin, Mod o Utente)."),

    ("ACCESSO AL SISTEMA", "Registrazione nuovo utente", "03_registrazione.png",
     "Nuovi utenti possono registrarsi compilando il form di registrazione.\n\n"
     "- Nome, cognome, email e password sono obbligatori\n"
     "- La password deve rispettare i requisiti minimi di sicurezza\n"
     "- Dopo la registrazione l'account viene creato con ruolo «Utente»\n"
     "- Solo l'amministratore può promuovere un account a MOD o ADMIN"),

    # ── ADMIN ─────────────────────────────────────────────────────────────────
    ("AREA AMMINISTRATORE", "Dashboard", "04_admin_dashboard.png",
     "La dashboard amministrativa offre una panoramica immediata sullo stato "
     "dell'applicazione.\n\n"
     "I riquadri mostrano:\n"
     "- Utenti totali / attivi registrati nel sistema\n"
     "- Schedine totali compilate e quante sono vincenti\n"
     "- Concorsi aperti (ancora in corso) / processati (chiusi ed elaborati)\n"
     "- Partite senza risultato ancora da completare\n"
     "- Notifiche inviate e da inviare agli utenti\n\n"
     "La barra laterale consente la navigazione rapida a tutte le sezioni: "
     "Concorsi, Leghe, Squadre, Regole, Utenti e Notifiche."),

    ("AREA AMMINISTRATORE", "Gestione Leghe", "05_admin_leghe.png",
     "In questa sezione si gestiscono le leghe sportive dell'applicazione.\n\n"
     "Per ogni lega sono visualizzati: ID, Nome, Paese e stato Attivo/Inattivo.\n\n"
     "Operazioni disponibili:\n"
     "- «Nuova» - crea una nuova lega (vedi prossima pagina)\n"
     "- Icona matita - modifica nome, descrizione e paese della lega\n"
     "- Icona cestino - elimina la lega (solo se priva di squadre, regole "
     "e concorsi; in caso contrario vengono eliminati a cascata)\n"
     "- «Esporta» - scarica l'elenco in CSV\n"
     "- «Importa» - carica un CSV di leghe"),

    ("AREA AMMINISTRATORE", "Nuova Lega - form", "06_admin_leghe_modal.png",
     "Il pannello di creazione/modifica lega è un modal sovrapposto alla lista.\n\n"
     "Campi:\n"
     "- Nome - denominazione ufficiale della lega (obbligatorio)\n"
     "- Paese - nazione di riferimento\n"
     "- Descrizione - testo libero opzionale\n"
     "- Attiva - spunta per rendere la lega selezionabile nei concorsi\n\n"
     "Premere «Salva» per confermare o «Annulla» / tasto Esc per chiudere "
     "senza salvare."),

    ("AREA AMMINISTRATORE", "Gestione Squadre", "07_admin_squadre.png",
     "Elenco di tutte le squadre registrate nel sistema.\n\n"
     "- Il filtro «Per lega» consente di visualizzare solo le squadre di "
     "una specifica lega\n"
     "- Per ogni squadra sono mostrati: ID, Nome, Sigla e Lega di appartenenza\n\n"
     "Operazioni:\n"
     "- «Nuova» - aggiunge una squadra (nome, sigla, lega)\n"
     "- Matita - modifica i dati della squadra\n"
     "- Cestino - elimina la squadra (bloccato se la squadra è già presente "
     "in almeno una partita; viene mostrato un messaggio di errore 409)\n"
     "- «Template» - scarica un file CSV d'esempio per l'importazione massiva\n"
     "- «Importa» - carica un CSV di squadre"),

    ("AREA AMMINISTRATORE", "Gestione Regole", "08_admin_regole.png",
     "Le regole definiscono le modalità di gioco per ogni concorso.\n\n"
     "Una regola è associata a una lega e specifica:\n"
     "- Partite richieste - numero minimo di pronostici obbligatori\n"
     "- Soglie vincita - lista JSON di punteggi premiati "
     "(es. [10, 11, 12, 13] = vince chi ne indovina 10 o più)\n"
     "- Max schedine per utente - limite di partecipazione\n"
     "- Max doppie / triple - numero massimo di pronostici doppi o tripli\n"
     "- Completamento obbligatorio - se attivo, la schedina deve avere "
     "tutti i pronostici compilati per essere confermata"),

    ("AREA AMMINISTRATORE", "Nuova Regola - form", "09_admin_regole_modal.png",
     "Il form di creazione regola permette di configurare tutte le "
     "impostazioni di gioco.\n\n"
     "Esempio di configurazione classica:\n"
     "- Partite richieste: 13\n"
     "- Soglie vincita: [13] - vince solo chi indovina tutte\n"
     "- Max doppie: 3, Max triple: 1\n"
     "- Completamento obbligatorio: sì\n\n"
     "Le regole possono essere riutilizzate su più concorsi della stessa lega."),

    ("AREA AMMINISTRATORE", "Gestione Concorsi", "10_admin_concorsi.png",
     "La sezione Concorsi mostra tutti i concorsi creati, con relativo stato.\n\n"
     "Stati possibili:\n"
     "- DRAFT - bozza, non ancora visibile agli utenti\n"
     "- OPEN - aperto, gli utenti possono compilare la schedina\n"
     "- CLOSED - chiuso alle nuove iscrizioni, in attesa di risultati\n"
     "- PROCESSING - elaborazione vincitori in corso\n"
     "- PROCESSED - elaborato, vincitori determinati\n\n"
     "L'icona «>» accanto al nome apre il dettaglio con l'elenco delle partite."),

    ("AREA AMMINISTRATORE", "Dettaglio Concorso - partite", "11_admin_concorso_dettaglio.png",
     "Il dettaglio di un concorso mostra tutte le partite associate.\n\n"
     "Per ogni partita sono visibili: numero progressivo (#), squadra di casa, "
     "squadra ospite, data programmata, tipo di giocata (1X2 o U/O con soglia), "
     "risultato ufficiale e pulsanti di azione.\n\n"
     "Operazioni disponibili (in base allo stato del concorso):\n"
     "- «Nuova partita» - aggiunge una partita al concorso\n"
     "- Matita - modifica data, tipo di giocata o squadre\n"
     "- Cestino - rimuove la partita (bloccato se esistono schedine)\n"
     "- «Chiudi concorso» - chiude le iscrizioni\n"
     "- «Elabora / Ricalcola» - calcola i vincitori (visibile ai MOD)"),

    ("AREA AMMINISTRATORE", "Gestione Utenti", "12_admin_utenti.png",
     "L'area Utenti permette di visualizzare e gestire tutti gli account.\n\n"
     "Per ogni utente sono mostrati: ID, nome, cognome, email, ruolo e stato.\n\n"
     "Ruoli disponibili:\n"
     "- USER - utente normale, può compilare schedine\n"
     "- MOD - moderatore, può inserire risultati ed elaborare i concorsi\n"
     "- ADMIN - amministratore, accesso completo a tutte le sezioni\n\n"
     "L'amministratore può modificare il ruolo e attivare/disattivare gli account."),

    # ── MOD ───────────────────────────────────────────────────────────────────
    ("AREA MODERATORE", "Lista Concorsi", "13_mod_concorsi.png",
     "Il moderatore accede a un'area dedicata con una vista semplificata "
     "dei concorsi.\n\n"
     "I concorsi sono ordinati per rilevanza operativa:\n"
     "1. OPEN - concorsi aperti che richiedono attenzione\n"
     "2. CLOSED - concorsi chiusi in attesa di risultati\n"
     "3. PROCESSING - elaborazione in corso\n"
     "4. PROCESSED - già elaborati\n"
     "5. SCHEDULED / DRAFT - pianificati o in bozza\n\n"
     "Per ogni concorso sono visibili: nome, stato, date di apertura/chiusura, "
     "numero di partite e schedine ricevute.\n\n"
     "Cliccando su un concorso si accede al dettaglio."),

    ("AREA MODERATORE", "Dettaglio Concorso - inserimento risultati", "14_mod_concorso_dettaglio_top.png",
     "Il moderatore può inserire i risultati delle partite e gestire il ciclo "
     "di vita del concorso.\n\n"
     "Nella parte superiore del dettaglio sono mostrate:\n"
     "- Informazioni generali del concorso (nome, date, stato, regola)\n"
     "- Pulsante «Chiudi concorso» - disponibile se il concorso è OPEN\n"
     "- Pulsante «Elabora vincitori» / «Ricalcola vincitori» - disponibile "
     "se il concorso è CLOSED o PROCESSED\n\n"
     "Il pulsante di elaborazione è disabilitato finché non sono stati inseriti "
     "i risultati di tutte le partite."),

    ("AREA MODERATORE", "Inserimento Risultati", "15_mod_inserimento_risultati.png",
     "Per ogni partita il moderatore può inserire il punteggio finale.\n\n"
     "- I campi «Casa» e «Ospite» accettano valori numerici interi (es. 2 e 1)\n"
     "- A destra dei campi viene mostrata in tempo reale l'anteprima del "
     "risultato ufficiale calcolato:\n"
     "  - Partite 1X2: mostra automaticamente 1, X o 2\n"
     "  - Partite Under/Over: mostra U o O in base alla soglia configurata\n"
     "- Premere «Salva» per registrare il risultato della singola partita\n\n"
     "Una volta inseriti tutti i risultati, il pulsante «Elabora vincitori» "
     "si attiva e il moderatore può avviare il calcolo."),

    # ── USER ──────────────────────────────────────────────────────────────────
    ("AREA UTENTE", "Lista Concorsi disponibili", "16_user_concorsi.png",
     "L'utente registrato vede l'elenco dei concorsi aperti e disponibili.\n\n"
     "Per ogni concorso sono mostrati: nome, lega, date di apertura e chiusura, "
     "stato e un indicatore che segnala se l'utente ha già partecipato.\n\n"
     "I concorsi in stato OPEN accettano nuove schedine.\n"
     "I concorsi CLOSED o PROCESSED sono visibili ma non consentono più "
     "l'invio di nuove schedine (è però possibile consultare l'esito)."),

    ("AREA UTENTE", "Compilazione Schedina - testata", "17_user_schedina_top.png",
     "Cliccando su un concorso aperto si accede alla schermata di compilazione.\n\n"
     "La parte superiore mostra le informazioni del concorso:\n"
     "- Nome, lega e periodo di validità\n"
     "- Regola applicata (numero di pronostici richiesti, doppie/triple ammesse)\n\n"
     "Scorrendo verso il basso si trovano le partite da pronosticare."),

    ("AREA UTENTE", "Compilazione Schedina - pronostici", "18_user_schedina_pronostici.png",
     "Per ogni partita l'utente deve selezionare il proprio pronostico.\n\n"
     "Partite 1X2:\n"
     "- Tre pulsanti: «1» (vittoria casa), «X» (pareggio), «2» (vittoria ospite)\n"
     "- È possibile selezionare più opzioni (doppia o tripla) nei limiti "
     "stabiliti dalla regola\n\n"
     "Partite Under/Over:\n"
     "- Due pulsanti: «U» (Under) e «O» (Over) - selezione esclusiva\n"
     "- Accanto alla partita è visibile il badge con la soglia (es. U/O 3.5)\n\n"
     "Una volta completati tutti i pronostici obbligatori, premere «Conferma "
     "schedina» per inviare. Le schedine non possono essere modificate dopo "
     "la conferma."),

    ("AREA UTENTE", "Le mie Schedine", "19_user_mie_schedine.png",
     "In questa sezione l'utente può consultare lo storico di tutte "
     "le proprie schedine.\n\n"
     "Per ogni schedina sono mostrati: concorso, data di conferma, stato "
     "e - per le schedine elaborate - il punteggio ottenuto (es. 9/13).\n\n"
     "Cliccando su una riga si espande il dettaglio con i singoli pronostici:\n"
     "- Icona verde (spunta) - pronostico corretto\n"
     "- Icona rossa (X) - pronostico errato\n"
     "- Per ogni partita: squadre, punteggio finale e risultato ufficiale\n\n"
     "Le schedine vincenti vengono evidenziate con un badge apposito."),
]

# ─── PDF builder ──────────────────────────────────────────────────────────────

FONTS = "C:/Windows/Fonts"

class ManualePDF(FPDF):

    def __init__(self, *args, **kwargs):
        super().__init__(*args, **kwargs)
        # Registra Arial Unicode (supporta tutti i caratteri italiani + simboli)
        self.add_font("Arial",  "",  f"{FONTS}/arial.ttf")
        self.add_font("Arial",  "B", f"{FONTS}/arialbd.ttf")
        self.add_font("Arial",  "I", f"{FONTS}/ariali.ttf")
        self.add_font("Arial",  "BI",f"{FONTS}/arialbi.ttf")

    def _f(self, style="", size=10):
        """Imposta font Arial."""
        self.set_font("Arial", style, size)

    def header(self):
        self.set_fill_color(*RED)
        self.rect(0, 0, 210, 12, "F")
        self.set_text_color(*WHITE)
        self._f("B", 9)
        self.set_xy(8, 3)
        self.cell(0, 6, "SCHEDINA  -  Manuale Utente")
        self.set_text_color(*DARK)

    def footer(self):
        self.set_y(-12)
        self.set_text_color(*GRAY)
        self._f("", 8)
        self.cell(0, 8, f"Pagina {self.page_no()}", align="C")
        self.set_text_color(*DARK)

    def cover_page(self):
        self.add_page()
        self.set_fill_color(*RED)
        self.rect(0, 0, 210, 297, "F")
        self.set_text_color(*WHITE)
        self._f("B", 52)
        self.set_y(90)
        self.cell(0, 20, "SCHEDINA", align="C", new_x="LMARGIN", new_y="NEXT")
        self._f("", 22)
        self.cell(0, 12, "Manuale Utente", align="C", new_x="LMARGIN", new_y="NEXT")
        self.ln(10)
        self._f("", 14)
        self.cell(0, 8, "Guida completa per Amministratori, Moderatori e Utenti",
                  align="C", new_x="LMARGIN", new_y="NEXT")
        self.ln(40)
        self._f("", 11)
        self.cell(0, 8, "Versione 1.0", align="C", new_x="LMARGIN", new_y="NEXT")

    def section_divider(self, title: str):
        self.add_page()
        self.ln(30)
        self.set_fill_color(*RED)
        self.set_draw_color(*RED)
        self.rect(15, 50, 4, 60, "F")
        self.set_text_color(*RED)
        self._f("B", 28)
        self.set_xy(25, 65)
        self.multi_cell(160, 14, title, align="L")
        self.set_text_color(*DARK)

    def content_page(self, section: str, title: str, img_file: str, description: str):
        self.add_page()
        self.set_y(15)
        self._f("", 8)
        self.set_text_color(*GRAY)
        self.cell(0, 5, section, new_x="LMARGIN", new_y="NEXT")
        self.set_text_color(*DARK)
        self._f("B", 16)
        self.cell(0, 9, title, new_x="LMARGIN", new_y="NEXT")
        self.set_draw_color(*RED)
        self.set_line_width(0.6)
        self.line(15, self.get_y() + 2, 195, self.get_y() + 2)
        self.ln(6)

        # immagine
        img_path = SHOTS / img_file
        if img_path.exists():
            try:
                with PILImage.open(img_path) as im:
                    iw, ih = im.size
                max_w, max_h = 170, 115
                ratio = min(max_w / iw, max_h / ih)
                disp_w, disp_h = iw * ratio, ih * ratio
                x = (210 - disp_w) / 2
                y0 = self.get_y()
                self.image(str(img_path), x=x, y=y0, w=disp_w, h=disp_h)
                # bordo grigio sottile
                self.set_draw_color(200, 200, 200)
                self.set_line_width(0.3)
                self.rect(x, y0, disp_w, disp_h)
                self.set_y(y0 + disp_h + 5)
            except Exception as e:
                self.set_text_color(200, 50, 50)
                self._f("I", 9)
                self.cell(0, 6, f"[Immagine non disponibile: {e}]",
                          new_x="LMARGIN", new_y="NEXT")
                self.set_text_color(*DARK)
        else:
            self.set_text_color(200, 50, 50)
            self._f("I", 9)
            self.cell(0, 6, f"[Screenshot non trovato: {img_file}]",
                      new_x="LMARGIN", new_y="NEXT")
            self.set_text_color(*DARK)

        # testo descrittivo
        self._f("", 10)
        self.set_text_color(*DARK)
        for line in description.split("\n"):
            stripped = line.strip()
            if stripped.startswith("-") or stripped.startswith("*"):
                self.set_x(22)
                self.multi_cell(168, 5.5, stripped, align="L")
            elif stripped == "":
                self.ln(2)
            else:
                self.set_x(15)
                self.multi_cell(180, 5.5, stripped, align="L")

    def toc_page(self, sections):
        self.add_page()
        self.set_y(15)
        self._f("B", 18)
        self.cell(0, 10, "Indice", new_x="LMARGIN", new_y="NEXT")
        self.set_draw_color(*RED)
        self.set_line_width(0.6)
        self.line(15, self.get_y() + 1, 195, self.get_y() + 1)
        self.ln(8)

        current_section = None
        page_num = 4
        for sec, title, _, _ in sections:
            if sec != current_section:
                current_section = sec
                self._f("B", 11)
                self.set_text_color(*RED)
                self.cell(0, 7, sec, new_x="LMARGIN", new_y="NEXT")
                self.set_text_color(*DARK)
            self._f("", 10)
            self.set_x(20)
            self.cell(155, 6, title)
            self.cell(15, 6, str(page_num), align="R", new_x="LMARGIN", new_y="NEXT")
            page_num += 1


# ─── main ─────────────────────────────────────────────────────────────────────

def build():
    pdf = ManualePDF(orientation="P", unit="mm", format="A4")
    pdf.set_auto_page_break(auto=True, margin=15)
    pdf.set_margins(15, 15, 15)

    # Copertina
    pdf.cover_page()

    # Indice (numeri pagina approssimativi)
    pdf.toc_page(SECTIONS)

    # Contenuto
    current_section = None
    for section, title, img, desc in SECTIONS:
        if section != current_section:
            pdf.section_divider(section)
            current_section = section
        pdf.content_page(section, title, img, desc)

    pdf.output(str(OUT))
    print(f"PDF generato: {OUT}")
    print(f"Pagine totali: {pdf.page}")


if __name__ == "__main__":
    build()
