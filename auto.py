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

from webbox import (
    LOGOUT_URL,
    ODCHOD,
    PRICHOD,
    TZ,
    datum_z,
    log,
    posledna_transakcia,
    prihlas,
    uloz_diagnostiku,
    vytvor_driver,
    zapis_transakciu,
    zhrnutie,
)

# Okno, v ktorom smie skript zapísať odchod. Mimo neho sa nič nedeje.
OKNO_OD = dtime(16, 30)
OKNO_DO = dtime(22, 45)


def v_okne(teraz):
    return OKNO_OD <= teraz.time() <= OKNO_DO


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
        cakanie = min(random.randint(0, 3600), max(0, rezerva))
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

        zapis_transakciu(driver, ODCHOD)
        popis, cas = posledna_transakcia(driver)
        zhrnutie(f"- ✅ odchod zapísaný: `{cas}`")
        driver.save_screenshot("po_odchode.png")
        return 0

    except TimeoutException:
        log("❌ Niečo sa nenačítalo včas (prihlásenie alebo čítačka).")
        zhrnutie("- ❌ timeout pri načítavaní stránky")
        uloz_diagnostiku(driver)
        raise
    except Exception as chyba:
        log(f"❌ Chyba: {chyba}")
        zhrnutie(f"- ❌ chyba: `{type(chyba).__name__}: {chyba}`")
        uloz_diagnostiku(driver)
        raise
    finally:
        try:
            driver.get(LOGOUT_URL)
        except Exception:
            pass
        finally:
            driver.quit()
            log("🔒 Prehliadač zatvorený")


if __name__ == "__main__":
    sys.exit(main())
