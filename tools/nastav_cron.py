#!/usr/bin/env python3
"""Založí (alebo aktualizuje) dve úlohy na cron-job.org pre automatizáciu odchodu.

Úloha 1 - každý deň o 03:00 zapne workflow na GitHube. To obchádza pravidlo,
            ktoré vypne plánované workflow v public repe po 60 dňoch bez aktivity.
Úloha 2 - každý deň o 16:31 a 17:31 (bratislavský čas) spustí workflow cez API,
            takže odchod nepadá na čas, ktorý vyberie GitHub, ale na 16:31 + náhodu.

Potrebuje dve hodnoty: fine-grained PAT (len repo zdenoiva-auto, právo
Actions: Read and write) a API kľúč z cron-job.org (Settings -> API key).

Použitie (PowerShell):
  python tools/nastav_cron.py

Skript si obe hodnoty vypýta a vstup sa nezobrazuje, takže sa nikam neukladajú.
Dajú sa zadať aj cez premenné prostredia GITHUB_TOKEN a CRONJOB_API_KEY.

Skript je idempotentný - opakované spustenie úlohy upraví, nie zduplikuje.
"""

import getpass
import json
import os
import sys
import time
import urllib.error
import urllib.request
from datetime import datetime

# Konzola na Windows nemusí zvládnuť všetky znaky - nech to nespadne kvôli výpisu.
for stream in (sys.stdout, sys.stderr):
    try:
        stream.reconfigure(errors="replace")
    except (AttributeError, OSError):
        pass

GITHUB_API = "https://api.github.com"
REPO = "zdenoiva/zdenoiva-auto"
WORKFLOW = "auto.yml"
CRON_API = "https://api.cron-job.org"


def request(method, url, headers, payload=None):
    data = json.dumps(payload).encode("utf-8") if payload is not None else None
    req = urllib.request.Request(url, data=data, method=method, headers=headers)
    try:
        with urllib.request.urlopen(req, timeout=30) as odpoved:
            telo = odpoved.read().decode("utf-8") or "{}"
            return odpoved.status, json.loads(telo) if telo.strip() else {}
    except urllib.error.HTTPError as chyba:
        return chyba.code, {"error": chyba.read().decode("utf-8", "replace")[:300]}
    except urllib.error.URLError as chyba:
        return 0, {"error": str(chyba)}


def request_retry(method, url, headers, payload=None, pokusy=5):
    """To iste ako request, ale pri HTTP 429 (limit cron-job.org) pocka a skusi znova."""
    stav, telo = 0, {}
    for pokus in range(pokusy):
        stav, telo = request(method, url, headers, payload)
        if stav != 429 or pokus == pokusy - 1:
            return stav, telo
        pauza = 5 * (pokus + 1)
        print(f"  ... cron-job.org odmietol kvoli limitu (HTTP 429), cakam {pauza} s")
        time.sleep(pauza)
    return stav, telo


def ulohy(token):
    hlavicky = {
        "Authorization": f"Bearer {token}",
        "Accept": "application/vnd.github+json",
        "X-GitHub-Api-Version": "2022-11-28",
    }
    notifikacia = {
        "onFailure": True,
        "onFailureCount": 1,
        "onSuccess": True,
        "onDisable": True,
    }
    return [
        {
            "title": "webbox: zapni workflow (proti 60-dnovemu vypnutiu)",
            "url": f"{GITHUB_API}/repos/{REPO}/actions/workflows/{WORKFLOW}/enable",
            "requestMethod": 4,  # PUT
            "enabled": True,
            "saveResponses": True,
            "notification": notifikacia,
            "extendedData": {"headers": hlavicky, "body": ""},
            "schedule": {
                "timezone": "Europe/Bratislava",
                "hours": [3],
                "minutes": [0],
                "mdays": [-1],
                "months": [-1],
                "wdays": [-1],
            },
        },
        {
            "title": "webbox: odchod",
            "url": f"{GITHUB_API}/repos/{REPO}/actions/workflows/{WORKFLOW}/dispatches",
            "requestMethod": 1,  # POST
            "enabled": True,
            "saveResponses": True,
            "notification": notifikacia,
            "extendedData": {
                "headers": {**hlavicky, "Content-Type": "application/json"},
                "body": json.dumps({"ref": "main"}),
            },
            "schedule": {
                "timezone": "Europe/Bratislava",
                "hours": [16, 17],
                "minutes": [31],
                "mdays": [-1],
                "months": [-1],
                "wdays": [-1],
            },
        },
    ]


# Úlohy, ktoré už nepatria do nastavenia — skript ich pri spustení zmaže.
ZASTARANE = {"webbox: ranna kontrola prichodu"}


GITHUB_PREFIXES = ("ghp_", "github_pat_", "gho_", "ghu_", "ghs_", "ghr_")


def je_github_token(hodnota):
    return hodnota.startswith(GITHUB_PREFIXES) and hodnota != "github_pat_..."


def je_cronjob_kluc(hodnota):
    return not hodnota.startswith(GITHUB_PREFIXES) and len(hodnota) >= 8


def nacitaj(nazov, popis, kontrola, co_je_spravne):
    """Vezme hodnotu z prostredia, inak sa na nu skryto spyta.

    Hodnota, ktora nevyzera spravne (napr. zaseknuta ukazka z minuleho pokusu),
    sa ignoruje a skript sa spyta nanovo.
    """
    hodnota = os.environ.get(nazov, "").strip()
    if hodnota and not kontrola(hodnota):
        print(f"Pozn.: v premennej {nazov} je neplatna hodnota z minula, ignorujem ju.")
        hodnota = ""

    while not hodnota:
        if not sys.stdin.isatty():
            print(f"CHYBA: chyba {nazov} a konzola nie je interaktivna.")
            return ""
        print(f"{popis} (vstup sa nezobrazuje):")
        hodnota = getpass.getpass("  > ").strip()
        if hodnota and not kontrola(hodnota):
            print(f"  {co_je_spravne}, skus znova.")
            hodnota = ""
    return hodnota


def main():
    token = nacitaj(
        "GITHUB_TOKEN",
        "GitHub PAT",
        je_github_token,
        "ma zacinat 'github_pat_' alebo 'ghp_' a nesmie to byt ukazka 'github_pat_...'",
    )
    if not token:
        print("       Vytvor PAT: https://github.com/settings/personal-access-tokens/new")
        return 1

    kluc = nacitaj(
        "CRONJOB_API_KEY",
        "cron-job.org API kluc",
        je_cronjob_kluc,
        "toto je GitHub token, potrebujem kluc z console.cron-job.org -> Settings -> API key",
    )
    if not kluc:
        return 1

    gh = {
        "Authorization": f"Bearer {token}",
        "Accept": "application/vnd.github+json",
        "X-GitHub-Api-Version": "2022-11-28",
    }
    cron = {"Authorization": f"Bearer {kluc}", "Content-Type": "application/json"}

    stav, _ = request(
        "PUT", f"{GITHUB_API}/repos/{REPO}/actions/workflows/{WORKFLOW}/enable", gh
    )
    if stav not in (200, 204):
        print(f"CHYBA: GitHub token nefunguje (HTTP {stav}) - chyba pravo Actions: Read and write na repo zdenoiva-auto?")
        return 1
    print("OK: GitHub token funguje a workflow je aktivny.")

    stav, zoznam = request("GET", f"{CRON_API}/jobs", cron)
    if stav != 200:
        print(f"CHYBA: cron-job.org API odmietlo kluc (HTTP {stav}): {zoznam}")
        return 1
    existujuce = {u.get("title"): u.get("jobId") for u in zoznam.get("jobs", [])}

    for uloha in ulohy(token):
        nazov = uloha["title"]
        if nazov in existujuce:
            cislo = existujuce[nazov]
            stav, _ = request_retry("PATCH", f"{CRON_API}/jobs/{cislo}", cron, {"job": uloha})
            print(f"{'OK' if stav == 200 else 'CHYBA'}: upravena uloha '{nazov}' (id {cislo}, HTTP {stav})")
        else:
            stav, odpoved = request_retry("PUT", f"{CRON_API}/jobs", cron, {"job": uloha})
            ok = stav == 200
            print(
                f"{'OK' if ok else 'CHYBA'}: zalozene uloha '{nazov}' "
                f"(HTTP {stav}{'' if ok else ': ' + str(odpoved)})"
            )

    # zrušené úlohy (napr. ranný mail) zmazať
    for nazov, cislo in existujuce.items():
        if nazov in ZASTARANE:
            stav, _ = request_retry("DELETE", f"{CRON_API}/jobs/{cislo}", cron)
            print(f"{'OK' if stav == 200 else 'CHYBA'}: zmazana stara uloha '{nazov}' (HTTP {stav})")

    stav, zoznam = request("GET", f"{CRON_API}/jobs", cron)
    if stav == 200:
        print("\nPrehlad uloh:")
        for u in zoznam.get("jobs", []):
            dalsie = u.get("nextExecution") or 0
            kedy = (
                datetime.fromtimestamp(dalsie).strftime("%d.%m.%Y %H:%M")
                if dalsie
                else "-"
            )
            print(f"  * {u.get('title')} (id {u.get('jobId')}) - najblizsie: {kedy}")

    print(
        "\nSkontroluj to v konzole cron-job.org: otvor ulohu 'webbox: odchod' a klikni "
        "na Test run. Ocakavana odpoved je HTTP 204 s prazdnym telom."
    )
    return 0


if __name__ == "__main__":
    sys.exit(main())
