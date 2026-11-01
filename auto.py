#!/usr/bin/env python3
"""Zapíše odchod vo webBox (ELKO) — najviac raz denne a len keď je človek v práci.

Pravidlá:
  * Akcia sa vykoná len medzi 16:30 a 22:45 miestneho času (Europe/Bratislava).
    Mimo tohto okna skript nič nerobí a skončí úspešne.
  * Ak poslednou transakciou nie je dnešný "Práca / Príchod", skript nič nerobí.
  * Skript zapisuje výhradne "Práca / Odchod", nikdy nič iné.

Spustenie:  USERNAME=... PASSWORD=... python -u auto.py
Diagnostika: DRY_RUN=1 python -u auto.py   (len načíta stav, nič nezapíše)
"""

import os
import random
import sys
import time
from datetime import datetime, time as dtime
from zoneinfo import ZoneInfo

TZ = ZoneInfo("Europe/Bratislava")

# Okno, v ktorom smie skript zapísať odchod. Mimo neho sa nič nedeje.
OKNO_OD = dtime(16, 30)
OKNO_DO = dtime(22, 45)

PRICHOD = "Práca / Príchod"
ODCHOD = "Práca / Odchod"

LOGIN_URL = "https://webbox.elko.sk/logindispatcher"
LOGOUT_URL = "https://webbox.elko.sk/logout"

SELEKTOR_ODCHOD = "button.rdr-make-transaction[data-label='Práca / Odchod']"


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


def v_okne(teraz):
    return OKNO_OD <= teraz.time() <= OKNO_DO


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


def zapis_odchod(driver):
    from selenium.common.exceptions import UnexpectedAlertPresentException
    from selenium.webdriver.common.by import By
    from selenium.webdriver.support.ui import WebDriverWait
    from selenium.webdriver.support import expected_conditions as EC

    log("🚪 Zapisujem odchod...")
    tlacidlo = WebDriverWait(driver, 20).until(
        EC.presence_of_element_located((By.CSS_SELECTOR, SELEKTOR_ODCHOD))
    )
    driver.execute_script("arguments[0].scrollIntoView({block: 'center'});", tlacidlo)
    time.sleep(0.5)

    try:
        WebDriverWait(driver, 10).until(
            EC.element_to_be_clickable((By.CSS_SELECTOR, SELEKTOR_ODCHOD))
        ).click()
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
            if popis == ODCHOD and datum_z(cas) == dnes:
                log(f"✅ Systém potvrdil odchod: {popis} ({cas})")
                return
        except UnexpectedAlertPresentException:
            zavri_alerty(driver)
        except Exception:
            pass
        time.sleep(1)
    raise RuntimeError("Odchod sa v systéme neobjavil — zápis sa nepotvrdil.")


def main():
    from selenium.common.exceptions import TimeoutException

    dry_run = os.environ.get("DRY_RUN", "").strip().lower() not in ("", "0", "false", "no")
    teraz = datetime.now(TZ)

    if not dry_run and not v_okne(teraz):
        log(f"⏭ {teraz:%d.%m.%Y %H:%M} — mimo okna "
            f"{OKNO_OD:%H:%M}–{OKNO_DO:%H:%M}, nič nerobím.")
        zhrnutie(f"- ⏭ mimo okna ({teraz:%H:%M}), bez akcie")
        return 0

    # Náhodné oneskorenie, aby odchod nepadol stále na tú istú minútu.
    if not dry_run:
        koniec_okna = teraz.replace(
            hour=OKNO_DO.hour, minute=OKNO_DO.minute, second=0, microsecond=0
        )
        rezerva = int((koniec_okna - teraz).total_seconds()) - 120
        cakanie = min(random.randint(0, 900), max(0, rezerva))
        if cakanie > 0:
            log(f"⏱ Čakám {cakanie // 60} min {cakanie % 60} s (náhodné oneskorenie).")
            time.sleep(cakanie)

    meno = os.environ["USERNAME"]
    heslo = os.environ["PASSWORD"]

    driver = vytvor_driver()
    try:
        prihlas(driver, meno, heslo)

        popis, cas = posledna_transakcia(driver)
        log(f"📋 Posledná transakcia: {popis} ({cas})")

        if popis != PRICHOD:
            log("ℹ️ Poslednou transakciou nie je príchod — nič nerobím.")
            zhrnutie(f"- ℹ️ bez akcie (posledná transakcia: `{popis}`)")
            return 0

        dnes = datetime.now(TZ).date()
        if datum_z(cas) != dnes:
            log(f"ℹ️ Príchod je z {cas.split('/')[-1].strip()}, nie z dneška — nič nerobím.")
            zhrnutie(f"- ℹ️ bez akcie (príchod z iného dňa: `{cas}`)")
            return 0

        if dry_run:
            log("🧪 DRY_RUN — odchod by sa zapísal, ale nič nezapisujem.")
            zhrnutie(f"- 🧪 dry-run: stav `{popis}`, odchod by sa zapísal")
            return 0

        zapis_odchod(driver)
        popis, cas = posledna_transakcia(driver)
        zhrnutie(f"- ✅ odchod zapísaný: `{cas}`")
        driver.save_screenshot("po_odchode.png")
        return 0

    except TimeoutException:
        log("❌ Niečo sa nenačítalo včas (prihlásenie alebo čítačka).")
        zhrnutie("- ❌ timeout pri načítavaní stránky")
        _uloz_diagnostiku(driver)
        raise
    except Exception as chyba:
        log(f"❌ Chyba: {chyba}")
        zhrnutie(f"- ❌ chyba: `{type(chyba).__name__}: {chyba}`")
        _uloz_diagnostiku(driver)
        raise
    finally:
        try:
            driver.get(LOGOUT_URL)
        except Exception:
            pass
        finally:
            driver.quit()
            log("🔒 Prehliadač zatvorený")


def _uloz_diagnostiku(driver):
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


if __name__ == "__main__":
    sys.exit(main())
