#!/usr/bin/env python3
"""Ranná kontrola príchodu vo webBox.

Režimy (prvý argument):
  check   — v pracovný deň ráno zistí, či je poslednou transakciou "Práca / Odchod".
            Ak áno, pošle mail s tlačidlom; klik naň príchod zapíše.
            Ak je poslednou transakciou čokoľvek iné, nič nerobí a skončí.
  record  — zapíše "Práca / Príchod", ale len keď je poslednou transakciou stále
            "Práca / Odchod" a nie je príliš neskoro (proti starému odkazu z mailu).

Spustenie:  USERNAME=... PASSWORD=... python -u prichod.py check
            USERNAME=... PASSWORD=... python -u prichod.py record
"""

import os
import smtplib
import ssl
import sys
from contextlib import contextmanager
from datetime import datetime, time as dtime
from email.message import EmailMessage
from email.utils import formatdate, make_msgid

from webbox import (
    LOGOUT_URL,
    ODCHOD,
    PRICHOD,
    TZ,
    ZAKLAD,
    datum_z,
    log,
    najdi_tlacidlo,
    posledna_transakcia,
    prihlas,
    uloz_diagnostiku,
    vytvor_driver,
    zapis_transakciu,
    zhrnutie,
)

# Kedy smie kontrola prebehnúť (ráno, pracovný deň).
CHECK_OD = dtime(5, 30)
CHECK_DO = dtime(8, 0)

# Dokedy smie platiť odkaz z mailu. Neskorší klik sa už nezapíše.
RECORD_OD = dtime(5, 30)
RECORD_DO = dtime(9, 30)


def v_okne(teraz, od, do):
    return od <= teraz.time() <= do


def pracovny_den(teraz):
    return teraz.weekday() < 5


def brana(teraz, od, do):
    """Vráti dôvod, prečo sa nemá nič robiť, alebo None."""
    if not pracovny_den(teraz):
        return f"víkend ({teraz:%d.%m.})"
    if not v_okne(teraz, od, do):
        return f"mimo okna {od:%H:%M}–{do:%H:%M} ({teraz:%H:%M})"
    return None


@contextmanager
def prehliadac():
    """Prehliadač, ktorý sa vždy zavrie; pri chybe uloží diagnostiku."""
    driver = vytvor_driver()
    try:
        yield driver
    except Exception:
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


def posli_mail(popis, cas, test=False):
    link = os.environ.get("CONFIRM_URL", "").strip()
    if not link:
        raise RuntimeError("Chýba CONFIRM_URL — adresa Worker-a, ktorý zapisuje príchod.")

    host = os.environ.get("SMTP_HOST", "").strip()
    port = int(os.environ.get("SMTP_PORT", "").strip() or "0")
    pouzivatel = os.environ.get("SMTP_USER", "").strip()
    # Gmail app password sa pri kopírovaní z webu vie preniesť aj s nezlomiteľnými
    # medzerami (\xa0), ktoré SMTP odmietne. Zmiznúť musia všetky medzery.
    heslo = "".join(os.environ.get("SMTP_PASSWORD", "").split())
    if not pouzivatel or not heslo:
        raise RuntimeError("Chýba SMTP_USER / SMTP_PASSWORD — mail sa nedá odoslať.")
    komu = os.environ.get("MAIL_TO", "").strip() or pouzivatel

    dnes = datetime.now(TZ)
    predmet = f"{'[TEST] ' if test else ''}Príchod {dnes:%d.%m.} — si v práci?"

    text = (
        f"Posledná transakcia v webBox: {popis} ({cas}).\n\n"
        "Príchod na dnes zatiaľ zapísaný nie je.\n"
        f"Ak si v práci: {link}\n\n"
        f"Odkaz funguje len dnes do {RECORD_DO:%H:%M}. Ak nie si v práci, mail ignoruj.\n"
    )
    html = f"""\
<div style="font-family:Arial,Helvetica,sans-serif;font-size:16px;color:#222;line-height:1.5">
  <p>Posledná transakcia v webBox: <b>{popis}</b> ({cas}).<br>
     Príchod na dnes zatiaľ zapísaný nie je.</p>
  <p style="margin:28px 0">
    <a href="{link}" style="background:#0b7a3b;color:#ffffff;padding:16px 28px;
       border-radius:8px;text-decoration:none;font-size:18px;font-weight:bold;
       display:inline-block">Som v práci — zapíš príchod</a>
  </p>
  <p style="color:#666;font-size:13px;margin-top:32px">
    Odkaz funguje len dnes do {RECORD_DO:%H:%M}.<br>
    Ak nie si v práci, mail ignoruj — nič sa nezapíše.
  </p>
</div>
"""

    sprava = EmailMessage()
    sprava["From"] = pouzivatel
    sprava["To"] = komu
    sprava["Subject"] = predmet
    sprava["Date"] = formatdate(localtime=True)
    sprava["Message-ID"] = make_msgid()
    sprava.set_content(text)
    sprava.add_alternative(html, subtype="html")

    domena = pouzivatel.rsplit("@", 1)[-1].lower()
    if not host:
        if domena in ("gmail.com", "googlemail.com"):
            host = "smtp.gmail.com"
        else:
            raise RuntimeError(
                f"Chýba SMTP_HOST pre adresu @{domena} (napr. smtp.seznam.cz)."
            )

    porty = [port] if port else [465, 587]
    chyby = []
    log(f"📮 SMTP {host}, účet @{domena}, port(y) {porty}")
    for pokus_port in porty:
        try:
            if pokus_port == 465:
                with smtplib.SMTP_SSL(host, pokus_port, timeout=30,
                                      context=ssl.create_default_context()) as server:
                    server.login(pouzivatel, heslo)
                    server.send_message(sprava)
            else:
                with smtplib.SMTP(host, pokus_port, timeout=30) as server:
                    server.starttls(context=ssl.create_default_context())
                    server.login(pouzivatel, heslo)
                    server.send_message(sprava)
            log(f"📧 Mail odoslaný cez {host}:{pokus_port}.")
            return
        except Exception as chyba:
            chyby.append(f"{pokus_port}: {type(chyba).__name__}: {chyba}")
            log(f"⚠️ Port {pokus_port} zlyhal: {type(chyba).__name__}: {chyba}")

    raise RuntimeError("Mail sa nepodarilo odoslať — " + " | ".join(chyby))


def kontrola(teraz):
    """Zistí stav a prípadne pošle mail. Nič nezapisuje."""
    dovod = brana(teraz, CHECK_OD, CHECK_DO)
    if dovod:
        log(f"⏭ {dovod} — nič nerobím.")
        if not os.environ.get("DRY_RUN"):
            zhrnutie(f"- ⏭ {dovod}, bez akcie")
            return 0
        log("🧪 DRY_RUN — kontrolujem aj tak, nič sa nezapíše.")

    with prehliadac() as driver:
        prihlas(driver, os.environ["USERNAME"], os.environ["PASSWORD"])
        popis, cas = posledna_transakcia(driver)
        log(f"📋 Posledná transakcia: {popis} ({cas})")

        if popis != ODCHOD:
            log("ℹ️ Poslednou transakciou nie je odchod — príchod netreba riešiť.")
            zhrnutie(f"- ℹ️ bez akcie (posledná transakcia: `{popis}`)")
            return 0

        if os.environ.get("DRY_RUN"):
            log("🧪 DRY_RUN — mail by sa odoslal, ale nič neposielam.")
            zhrnutie(f"- 🧪 dry-run: mail by sa poslal (posledná transakcia `{popis}`)")
            return 0

        if not os.environ.get("SMTP_PASSWORD", "").strip():
            log("ℹ️ Mail nie je nastavený (chýba SMTP_PASSWORD) — preskakujem.")
            zhrnutie("- ℹ️ mail nie je nastavený, preskakujem")
            return 0

        try:
            posli_mail(popis, cas)
        except Exception as chyba:
            # Stav je prečítaný správne; zlyhalo len odoslanie. Nech to nezhodí celý beh.
            log(f"⚠️ Mail sa nepodarilo odoslať: {chyba}")
            zhrnutie(f"- ⚠️ mail zlyhal: `{chyba}`")
            return 0

        zhrnutie(f"- 📧 mail odoslaný (posledná transakcia: `{popis}` z `{cas}`)")
        return 0


def zapis(teraz):
    """Zapíše príchod, ak je to ešte zmysluplné."""
    dovod = brana(teraz, RECORD_OD, RECORD_DO)
    if dovod:
        log(f"⏭ {dovod} — nič nerobím.")
        if not os.environ.get("DRY_RUN"):
            zhrnutie(f"- ⏭ {dovod}, bez akcie")
            return 0
        log("🧪 DRY_RUN — kontrolujem aj tak, nič sa nezapíše.")

    with prehliadac() as driver:
        prihlas(driver, os.environ["USERNAME"], os.environ["PASSWORD"])
        popis, cas = posledna_transakcia(driver)
        log(f"📋 Posledná transakcia: {popis} ({cas})")

        dnes = teraz.date()
        if popis == PRICHOD and datum_z(cas) == dnes:
            log("ℹ️ Príchod už je zapísaný — nič nerobím.")
            zhrnutie(f"- ℹ️ príchod už zapísaný (`{cas}`)")
            return 0

        if popis != ODCHOD:
            log("ℹ️ Poslednou transakciou nie je odchod — nič nezapisujem.")
            zhrnutie(f"- ℹ️ bez akcie (posledná transakcia: `{popis}`)")
            return 0

        if os.environ.get("DRY_RUN"):
            _, popisy = najdi_tlacidlo(driver, PRICHOD)
            log(f"🧪 DRY_RUN — tlačidlá na stránke: {popisy}")
            zhrnutie(f"- 🧪 dry-run: tlačidlá `{popisy}`, príchod by sa zapísal")
            return 0

        zapis_transakciu(driver, PRICHOD)
        popis, cas = posledna_transakcia(driver)
        zhrnutie(f"- ✅ príchod zapísaný: `{cas}`")
        driver.save_screenshot("po_prichode.png")
        return 0


def test_mailu(teraz):
    """Pošle ukážkový mail — na overenie SMTP a tlačidla, bez zásahu do webBoxu."""
    posli_mail("Práca / Odchod", f"20:32:57 / {teraz:%d.%m.%Y}", test=True)
    zhrnutie("- 🧪 testovací mail odoslaný")
    return 0


def smtp_diagnostika():
    """Vypíše, čo presne server odpovie pri spojení a prihlásení (bez hesla)."""
    pouzivatel = os.environ.get("SMTP_USER", "").strip()
    heslo = "".join(os.environ.get("SMTP_PASSWORD", "").split())
    if not pouzivatel:
        log("CHYBA: chýba SMTP_USER")
        return 1
    domena = pouzivatel.rsplit("@", 1)[-1].lower()
    host = os.environ.get("SMTP_HOST", "").strip()
    if not host:
        host = "smtp.gmail.com" if domena in ("gmail.com", "googlemail.com") else ""

    log(f"účet: @{domena}, host: {host or '?'}, heslo: {'nastavené' if heslo else 'chýba'}, "
        f"dĺžka hesla: {len(heslo)}")

    for port in (465, 587):
        server = None
        try:
            if port == 465:
                server = smtplib.SMTP_SSL(host, port, timeout=20,
                                          context=ssl.create_default_context())
            else:
                server = smtplib.SMTP(host, port, timeout=20)
                server.starttls(context=ssl.create_default_context())
            kod, sprava = server.ehlo()
            log(f"[{port}] EHLO {kod}: {sprava.decode('utf-8', 'replace')[:150]}")
            log(f"[{port}] AUTH ponuky: {server.esmtp_features.get('auth', 'žiadne')}")
            try:
                server.login(pouzivatel, heslo)
                log(f"[{port}] prihlásenie OK")
            except Exception as chyba:
                log(f"[{port}] prihlásenie ZLYHALO: {type(chyba).__name__}: {chyba}")
        except Exception as chyba:
            log(f"[{port}] spojenie ZLYHALO: {type(chyba).__name__}: {chyba}")
        finally:
            if server is not None:
                try:
                    server.quit()
                except Exception:
                    pass
    return 0



def login_test():
    """Skúsi prihlásenie obyčajným HTTP POST-om (rovnako ako appka)."""
    import http.cookiejar
    import urllib.parse
    import urllib.request

    meno = os.environ.get("USERNAME", "")
    heslo = os.environ.get("PASSWORD", "")
    if not meno or not heslo:
        log("CHYBA: chýba USERNAME / PASSWORD")
        return 1

    klient = urllib.request.build_opener(
        urllib.request.HTTPCookieProcessor(http.cookiejar.CookieJar()),
        urllib.request.HTTPRedirectHandler(),
    )
    klient.addheaders = [("User-Agent", "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 "
                                        "(KHTML, like Gecko) Chrome/126 Mobile Safari/537.36")]
    try:
        klient.open(ZAKLAD + "/logindispatcher", timeout=30).read()
        data = urllib.parse.urlencode({
            "_username": meno,
            "_password": heslo,
            "_loginto": "webbox",
            "_target_path": "",
            "email_check25863": "",
            "_authenticationConnectorId": "",
        }).encode()
        odpoved = klient.open(ZAKLAD + "/login_check", data, timeout=30)
        html = odpoved.read().decode("utf-8", "replace")
        log(f"po POST: {odpoved.geturl()} | čítačka v stránke: {'rdr-last-transaction' in html}")
        if "rdr-last-transaction" not in html:
            druha = klient.open(ZAKLAD + "/", timeout=30)
            html2 = druha.read().decode("utf-8", "replace")
            log(f"skúška /: {druha.geturl()} | čítačka: {'rdr-last-transaction' in html2}")
            if "rdr-last-transaction" in html2:
                log("HTTP prihlásenie FUNGUJE (session cookie stačí).")
                return 0
            log("HTTP prihlásenie NEFUNGUJE — treba ostať pri prihlásení v prehliadači.")
            return 1
        log("HTTP prihlásenie FUNGUJE.")
        return 0
    except Exception as chyba:
        log(f"CHYBA pri HTTP prihlásení: {type(chyba).__name__}: {chyba}")
        return 1
def main():
    from selenium.common.exceptions import TimeoutException

    rezim = (sys.argv[1] if len(sys.argv) > 1 else os.environ.get("REZIM", "check")).strip()
    if rezim not in ("check", "record", "test", "smtp", "login"):
        print(f"Neznámy režim: {rezim} (čakám check, record, test alebo smtp)", file=sys.stderr)
        return 2

    teraz = datetime.now(TZ)
    try:
        if rezim == "test":
            return test_mailu(teraz)
        if rezim == "smtp":
            return smtp_diagnostika()
        if rezim == "login":
            return login_test()
        return kontrola(teraz) if rezim == "check" else zapis(teraz)
    except TimeoutException:
        log("❌ Niečo sa nenačítalo včas (prihlásenie alebo čítačka).")
        zhrnutie("- ❌ timeout pri načítavaní stránky")
        raise
    except Exception as chyba:
        log(f"❌ Chyba: {chyba}")
        zhrnutie(f"- ❌ chyba: `{type(chyba).__name__}: {chyba}`")
        raise


if __name__ == "__main__":
    sys.exit(main())
