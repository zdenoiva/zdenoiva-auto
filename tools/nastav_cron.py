#!/usr/bin/env python3
"""Založí (alebo aktualizuje) dve úlohy na cron-job.org pre automatizáciu odchodu.

Úloha 1 — každý deň o 03:00 zapne workflow na GitHube. To obchádza pravidlo,
            ktoré vypne plánované workflow v public repe po 60 dňoch bez aktivity.
Úloha 2 — každý deň o 16:31 a 17:31 (bratislavský čas) spustí workflow cez API,
            takže odchod nepadá na čas, ktorý vyberie GitHub, ale na 16:31 + náhodu.

Potrebuje dve premenné prostredia:
  GITHUB_TOKEN     fine-grained PAT, len repo zdenoiva-auto, právo Actions: Read and write
  CRONJOB_API_KEY  API kľúč z cron-job.org (Settings → API key)

Použitie (PowerShell):
  $env:GITHUB_TOKEN    = "github_pat_..."
  $env:CRONJOB_API_KEY = "..."
  python tools/nastav_cron.py

Skript je idempotentný — opakované spustenie úlohy upraví, nie zduplikuje.
"""

import json
import os
import sys
import urllib.error
import urllib.request
from datetime import datetime
from zoneinfo import ZoneInfo

GITHUB_API = "https://api.github.com"
REPO = "zdenoiva/zdenoiva-auto"
WORKFLOW = "auto.yml"
CRON_API = "https://api.cron-job.org"
TZ = ZoneInfo("Europe/Bratislava")


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
            "title": "webbox: zapni workflow (proti 60-dňovému vypnutiu)",
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
                "headers": {
                    **hlavicky,
                    "Content-Type": "application/json",
                },
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


def main():
    token = os.environ.get("GITHUB_TOKEN", "").strip()
    kluc = os.environ.get("CRONJOB_API_KEY", "").strip()
    if not token or not kluc:
        print("❌ Nastav GITHUB_TOKEN a CRONJOB_API_KEY.")
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
        print(f"❌ GitHub token nefunguje (HTTP {stav}) — chýba právo Actions: Read and write?")
        return 1
    print("✅ GitHub token funguje a workflow je aktívny.")

    stav, zoznam = request("GET", f"{CRON_API}/jobs", cron)
    if stav != 200:
        print(f"❌ cron-job.org API odmietlo kľúč (HTTP {stav}): {zoznam}")
        return 1
    existujuce = {u.get("title"): u.get("jobId") for u in zoznam.get("jobs", [])}

    for uloha in ulohy(token):
        nazov = uloha["title"]
        if nazov in existujuce:
            cislo = existujuce[nazov]
            stav, _ = request("PATCH", f"{CRON_API}/jobs/{cislo}", cron, {"job": uloha})
            print(f"{'✅' if stav == 200 else '❌'} upravená úloha „{nazov}“ (id {cislo}, HTTP {stav})")
        else:
            stav, odpoved = request("PUT", f"{CRON_API}/jobs", cron, {"job": uloha})
            ok = stav == 200
            print(
                f"{'✅' if ok else '❌'} založená úloha „{nazov}“ "
                f"(HTTP {stav}{'' if ok else ': ' + str(odpoved)})"
            )

    stav, zoznam = request("GET", f"{CRON_API}/jobs", cron)
    if stav == 200:
        print("\nPrehľad úloh:")
        for u in zoznam.get("jobs", []):
            dalsie = u.get("nextExecution") or 0
            kedy = (
                datetime.fromtimestamp(dalsie, TZ).strftime("%d.%m.%Y %H:%M")
                if dalsie
                else "—"
            )
            print(f"  • {u.get('title')} (id {u.get('jobId')}) — najbližšie: {kedy}")

    print(
        "\nSkontroluj to v konzole cron-job.org: otvor úlohu „webbox: odchod“ a klikni "
        "na Test run. Očakávaná odpoveď je HTTP 204 (prázdne telo)."
    )
    return 0


if __name__ == "__main__":
    sys.exit(main())
