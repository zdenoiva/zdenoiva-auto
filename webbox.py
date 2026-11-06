"""Spoločná práca s webBox — prihlásenie, čítanie stavu, zápis transakcie.

Používajú to obe automatizácie: auto.py (odchod) aj prichod.py (ranný príchod).
"""

import os
import time
from datetime import datetime
from zoneinfo import ZoneInfo

TZ = ZoneInfo("Europe/Bratislava")

PRICHOD = "Práca / Príchod"
ODCHOD = "Práca / Odchod"

# Adresa sa berie z premennej prostredia, aby v repozitári nebola natvrdo.
# V GitHub Actions je nastavená zo secretu WEBBOX_URL.
ZAKLAD = (os.environ.get("WEBBOX_URL") or "").strip().rstrip("/")

LOGIN_URL = ZAKLAD + "/logindispatcher"
LOGOUT_URL = ZAKLAD + "/logout"


def log(sprava):
    print(sprava, flush=True)


def zhrnutie(riadok):
    """Zápis do prehľadu behu v GitHub Actions (aby bol výsledok vidieť bez logov)."""
    cesta = os.environ.get("GITHUB_STEP_SUMMARY")
    if not cesta:
        return
    try:
        with open(cesta, "a", encoding="utf-8") as subor:
            subor.write(riadok + "\n")
    except OSError:
        pass


def vytvor_driver():
    from selenium import webdriver
    from selenium.webdriver.chrome.options import Options

    options = Options()
    options.add_argument("--headless=new")
    options.add_argument("--no-sandbox")
    options.add_argument("--disable-dev-shm-usage")
    options.add_argument("--disable-gpu")
    options.add_argument("--window-size=1920,1080")
    # Kamera/mikrofón/geolokáciu nechceme — stránka sa na ne nesmie pýtať.
    options.add_argument("--use-fake-ui-for-media-stream")
    options.add_argument("--use-fake-device-for-media-stream")
    options.add_argument("--deny-permission-prompts")
    options.add_experimental_option(
        "prefs",
        {
            "profile.default_content_setting_values.geolocation": 2,
            "profile.default_content_setting_values.media_stream_mic": 2,
            "profile.default_content_setting_values.media_stream_camera": 2,
            "profile.default_content_setting_values.media_stream": 2,
            "profile.default_content_setting_values.notifications": 2,
        },
    )
    return webdriver.Chrome(options=options)


def posledna_transakcia(driver, timeout=30):
    """Vráti (popis, 'HH:MM:SS / DD.MM.YYYY') poslednej transakcie z čítačky."""
    from selenium.webdriver.common.by import By
    from selenium.webdriver.support.ui import WebDriverWait

    WebDriverWait(driver, timeout).until(
        lambda d: d.find_elements(By.CSS_SELECTOR, "#rdr-last-transaction span.label")
    )
    riadky = [
        riadok.strip()
        for riadok in driver.find_element(By.ID, "rdr-last-transaction").text.splitlines()
        if riadok.strip()
    ]
    popis = riadky[0] if riadky else ""
    cas = next((riadok for riadok in riadky[1:] if "/" in riadok and ":" in riadok), "")
    return popis, cas


def datum_z(cas):
    """'20:32:57 / 30.10.2026' -> date(2026, 10, 30), pri neznámom formáte None."""
    try:
        return datetime.strptime(cas.split("/")[-1].strip(), "%d.%m.%Y").date()
    except ValueError:
        return None


def zavri_alerty(driver):
    from selenium.common.exceptions import NoAlertPresentException

    try:
        dialog = driver.switch_to.alert
        log(f"⚠️ Zatváram dialóg: {dialog.text}")
        dialog.dismiss()
    except NoAlertPresentException:
        pass


def prihlas(driver, meno, heslo):
    from selenium.webdriver.common.by import By
    from selenium.webdriver.support.ui import WebDriverWait
    from selenium.webdriver.support import expected_conditions as EC

    if not ZAKLAD:
        raise RuntimeError("Chýba premenná WEBBOX_URL (adresa dochádzkového systému).")

    log("📂 Otváram prihlasovaciu stránku...")
    driver.get(LOGIN_URL)
    WebDriverWait(driver, 30).until(
        EC.presence_of_element_located((By.ID, "username"))
    ).send_keys(meno)
    driver.find_element(By.ID, "password").send_keys(heslo)

    tlacidlo = driver.find_element(
        By.CSS_SELECTOR, "button[type='submit'], input[type='submit']"
    )
    driver.execute_script("arguments[0].click();", tlacidlo)

    log("🔄 Čakám na načítanie virtuálnej čítačky...")
    # Keď sú zlé prihlasovacie údaje, čítačka sa neobjaví a čakanie vyprší.
    posledna_transakcia(driver, timeout=40)


def najdi_tlacidlo(driver, ciel):
    """Nájde tlačidlo transakcie s presne daným popisom.

    Vráti (element alebo None, zoznam popisov všetkých tlačidiel). Zoznam sa
    loguje, keď sa tlačidlo nenájde — aby bolo z logu vidieť, ako sa volá.
    """
    from selenium.webdriver.common.by import By

    kandidati = driver.find_elements(By.CSS_SELECTOR, "button.rdr-make-transaction")
    popisy = []
    for kandidat in kandidati:
        popis = (kandidat.get_attribute("data-label") or kandidat.text or "").strip()
        popisy.append(popis)
        if popis == ciel:
            return kandidat, popisy
    return None, popisy


def zapis_transakciu(driver, ciel):
    """Klikne na tlačidlo `ciel`, potvrdí dialóg a overí, že sa transakcia zapísala."""
    from selenium.common.exceptions import UnexpectedAlertPresentException
    from selenium.webdriver.common.by import By
    from selenium.webdriver.support.ui import WebDriverWait
    from selenium.webdriver.support import expected_conditions as EC

    log(f"✍️ Zapisujem: {ciel}...")
    WebDriverWait(driver, 20).until(
        lambda d: d.find_elements(By.CSS_SELECTOR, "button.rdr-make-transaction")
    )
    tlacidlo, popisy = najdi_tlacidlo(driver, ciel)
    if tlacidlo is None:
        raise RuntimeError(
            f"Tlačidlo '{ciel}' sa nenašlo. Na stránke sú tlačidlá: {popisy}"
        )

    driver.execute_script("arguments[0].scrollIntoView({block: 'center'});", tlacidlo)
    time.sleep(0.5)

    try:
        tlacidlo.click()
    except Exception:
        log("⚠️ Bežný klik zlyhal, skúšam JS klik.")
        driver.execute_script("arguments[0].click();", tlacidlo)

    potvrdenie = WebDriverWait(driver, 15).until(
        EC.element_to_be_clickable((By.ID, "rdr-confirm-btn"))
    )
    potvrdenie.click()
    log("✅ Potvrdené, čakám na zápis v systéme...")

    dnes = datetime.now(TZ).date()
    koniec = time.time() + 30
    while time.time() < koniec:
        try:
            popis, cas = posledna_transakcia(driver, timeout=3)
            if popis == ciel and datum_z(cas) == dnes:
                log(f"✅ Systém potvrdil: {popis} ({cas})")
                return
        except UnexpectedAlertPresentException:
            zavri_alerty(driver)
        except Exception:
            pass
        time.sleep(1)
    raise RuntimeError(f"{ciel} sa v systéme neobjavil — zápis sa nepotvrdil.")


def uloz_diagnostiku(driver):
    try:
        driver.save_screenshot("chyba_screenshot.png")
        log("📸 Screenshot chyby uložený")
    except Exception:
        pass
    try:
        with open("page_source_error.html", "w", encoding="utf-8") as subor:
            subor.write(driver.page_source)
        log("📄 HTML zdroj uložený")
    except Exception:
        pass
