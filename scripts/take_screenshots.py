"""
Script Playwright per raccogliere gli screenshot del manuale utente Schedina.
Salva le immagini in scripts/screenshots/ suddivise per ruolo.
"""

import os, time
from pathlib import Path
from playwright.sync_api import sync_playwright, Page

BASE_URL = "http://localhost:5173"
OUT_DIR  = Path(__file__).parent / "screenshots"
OUT_DIR.mkdir(exist_ok=True)

VIEWPORT = {"width": 1280, "height": 800}

# ─── helper ───────────────────────────────────────────────────────────────────

def shot(page: Page, name: str):
    time.sleep(0.6)   # lascia il tempo al render
    path = OUT_DIR / name
    page.screenshot(path=str(path), full_page=False)
    print(f"  OK {name}")

def login(page: Page, email: str, password: str):
    page.goto(f"{BASE_URL}/login")
    page.wait_for_load_state("networkidle")
    page.fill("input[type='email']", email)
    page.fill("input[type='password']", password)
    page.click("button[type='submit']")
    page.wait_for_load_state("networkidle")
    time.sleep(0.8)

def logout(page: Page):
    try:
        page.click("button:has-text('Esci')", timeout=3000)
        page.wait_for_load_state("networkidle")
        time.sleep(0.5)
    except Exception:
        page.goto(f"{BASE_URL}/login")
        page.evaluate("localStorage.clear()")
        page.reload()
        time.sleep(0.5)

# ─── sezioni ──────────────────────────────────────────────────────────────────

def screenshots_login(page: Page):
    print("\n[LOGIN]")
    page.goto(f"{BASE_URL}/login")
    page.wait_for_load_state("networkidle")
    shot(page, "01_login_vuoto.png")

    page.fill("input[type='email']", "admin@schedina.it")
    page.fill("input[type='password']", "12345678")
    shot(page, "02_login_compilato.png")

    # Registrazione
    page.click("a:has-text('Registrati')")
    page.wait_for_load_state("networkidle")
    shot(page, "03_registrazione.png")


def screenshots_admin(page: Page):
    print("\n[ADMIN]")
    login(page, "admin@schedina.it", "12345678")

    # Dashboard
    page.goto(f"{BASE_URL}/admin")
    page.wait_for_load_state("networkidle")
    shot(page, "04_admin_dashboard.png")

    # Leghe
    page.goto(f"{BASE_URL}/admin/leagues")
    page.wait_for_load_state("networkidle")
    shot(page, "05_admin_leghe.png")

    # Modal nuova lega
    try:
        page.click("button:has-text('Nuova')")
        time.sleep(0.5)
        shot(page, "06_admin_leghe_modal.png")
        page.keyboard.press("Escape")
        time.sleep(0.3)
    except Exception:
        print("  ! modal lega non trovato, skip")

    # Squadre
    page.goto(f"{BASE_URL}/admin/teams")
    page.wait_for_load_state("networkidle")
    shot(page, "07_admin_squadre.png")

    # Regole
    page.goto(f"{BASE_URL}/admin/rules")
    page.wait_for_load_state("networkidle")
    shot(page, "08_admin_regole.png")

    # Modal nuova regola
    try:
        page.click("button:has-text('Nuova')")
        time.sleep(0.5)
        shot(page, "09_admin_regole_modal.png")
        page.keyboard.press("Escape")
        time.sleep(0.3)
    except Exception:
        print("  ! modal regola non trovato, skip")

    # Concorsi
    page.goto(f"{BASE_URL}/admin/contests")
    page.wait_for_load_state("networkidle")
    shot(page, "10_admin_concorsi.png")

    # Dettaglio concorso (Schedina #1 = id 3)
    try:
        page.click("tr:has-text('Schedina #1') a, tr:has-text('Schedina') td a")
        page.wait_for_load_state("networkidle")
        shot(page, "11_admin_concorso_dettaglio.png")
    except Exception:
        try:
            page.goto(f"{BASE_URL}/admin/contests/3")
            page.wait_for_load_state("networkidle")
            shot(page, "11_admin_concorso_dettaglio.png")
        except Exception as e:
            print(f"  ! dettaglio concorso: {e}")

    # Utenti
    page.goto(f"{BASE_URL}/admin/users")
    page.wait_for_load_state("networkidle")
    shot(page, "12_admin_utenti.png")

    logout(page)


def screenshots_mod(page: Page):
    print("\n[MOD]")
    login(page, "mod@schedina.it", "mod1234")

    # Lista concorsi MOD
    page.wait_for_load_state("networkidle")
    shot(page, "13_mod_concorsi.png")

    # Dettaglio concorso MOD
    try:
        page.click("table tbody tr:first-child a, table tbody tr:first-child td:first-child")
        page.wait_for_load_state("networkidle")
        shot(page, "14_mod_concorso_dettaglio_top.png")
        # Scroll giù per vedere inserimento risultati
        page.evaluate("window.scrollTo(0, 400)")
        time.sleep(0.4)
        shot(page, "15_mod_inserimento_risultati.png")
    except Exception as e:
        print(f"  ! dettaglio mod: {e}")
        try:
            page.goto(f"{BASE_URL}/mod/contests/3")
            page.wait_for_load_state("networkidle")
            shot(page, "14_mod_concorso_dettaglio_top.png")
            page.evaluate("window.scrollTo(0, 400)")
            time.sleep(0.4)
            shot(page, "15_mod_inserimento_risultati.png")
        except Exception as e2:
            print(f"  ! fallback mod: {e2}")

    logout(page)


def screenshots_user(page: Page):
    print("\n[USER]")
    login(page, "mario@schedina.it", "user1234")

    # Lista concorsi utente
    page.wait_for_load_state("networkidle")
    shot(page, "16_user_concorsi.png")

    # Dettaglio concorso / compilazione schedina
    try:
        page.click("a:has-text('Partecipa'), button:has-text('Partecipa'), table tbody tr:first-child a")
        page.wait_for_load_state("networkidle")
        shot(page, "17_user_schedina_top.png")
        page.evaluate("window.scrollTo(0, 400)")
        time.sleep(0.4)
        shot(page, "18_user_schedina_pronostici.png")
    except Exception as e:
        print(f"  ! partecipa: {e}")
        try:
            page.goto(f"{BASE_URL}/contests/3")
            page.wait_for_load_state("networkidle")
            shot(page, "17_user_schedina_top.png")
            page.evaluate("window.scrollTo(0, 400)")
            time.sleep(0.4)
            shot(page, "18_user_schedina_pronostici.png")
        except Exception as e2:
            print(f"  ! fallback user contest: {e2}")

    # Le mie schedine
    try:
        page.goto(f"{BASE_URL}/my-coupons")
        page.wait_for_load_state("networkidle")
        shot(page, "19_user_mie_schedine.png")
    except Exception as e:
        print(f"  ! mie schedine: {e}")

    logout(page)

# ─── main ─────────────────────────────────────────────────────────────────────

def main():
    with sync_playwright() as pw:
        browser = pw.chromium.launch(headless=True)
        ctx = browser.new_context(viewport=VIEWPORT)
        page = ctx.new_page()

        screenshots_login(page)
        screenshots_admin(page)
        screenshots_mod(page)
        screenshots_user(page)

        browser.close()

    print(f"\nDone! Screenshots in: {OUT_DIR}")
    for f in sorted(OUT_DIR.iterdir()):
        print(f"  {f.name}")

if __name__ == "__main__":
    main()
