# zdenoiva-auto

Skript zapisuje vo webBox odchod z práce a ranný príchod. Beží na GitHub Actions,
bez servera.

## Čo robí

- Prihlási sa do webBoxu a prečíta poslednú transakciu z virtuálnej čítačky.
- Ak je poslednou transakciou **dnešný** `Práca / Príchod`, klikne na `Odchod`, potvrdí ho
  a overí, že ho systém naozaj zapísal.
- V každom inom prípade (odchod už zapísaný, príchod z iného dňa, žiadny príchod,
  víkend, sviatok, dovolenka) skončí úspešne a nič nezapíše. Zlyhanie znamená
  skutočný problém, nie „dnes sa nešlo do práce“.

## Kedy sa spúšťa

Cron je `*/30 10-16 * * *` (UTC) — 14 pokusov denne, pretože GitHub plánované behy
spúšťa oneskorene a meškanie sa mení (bežne 1–4 hodiny).

Zápis prebehne len vtedy, keď beh naštartuje medzi **16:30 a 22:45** bratislavského
času, plus náhodné oneskorenie 0–60 minút. Vďaka viacerým pokusom sa odchod zapíše
aj vtedy, keď sa prvý beh oneskorí; zároveň platí, že sa nezapíše dvakrát.

## Nastavenie

V *Settings → Secrets and variables → Actions* sú potrebné dva secrety:

| Secret | Význam |
| --- | --- |
| `USERNAME` | prihlasovacie meno do webBox |
| `PASSWORD` | heslo do webBox |

Manuálne spustenie: *Actions → automatizácia → Run workflow*, voliteľne s `dry_run`,
ktorý sa len prihlási a vypíše stav, ale nič nezapíše.

## Externý spúšťač (cron-job.org)

GitHub plánované behy spúšťa oneskorene (bežne 1–4 hodiny) a v public repe ich po
60 dňoch bez aktivity potichu vypne. Preto sa dá workflow spúšťať zvonku:

- `tools/nastav_cron.py` založí na cron-job.org tri úlohy — jednu, ktorá každý deň
  o 03:00 workflow zapne (proti 60-dňovému vypnutiu), druhú, ktorá ho o 16:31 a 17:31
  bratislavského času spustí cez API, a tretiu, ktorá v pracovné dni o 07:35 spustí
  rannú kontrolu príchodu.

Potrebné je vlastné fine-grained PAT (len tento repozitár, právo *Actions: Read and write*)
a API kľúč z cron-job.org (*Settings → API key*):

```powershell
python tools/nastav_cron.py
```

Skript si obe hodnoty vypýta sám a vstup sa nezobrazuje, takže sa nedostanú do histórie
príkazov ani do žiadneho logu. Dajú sa zadať aj cez premenné prostredia `GITHUB_TOKEN`
a `CRONJOB_API_KEY`.

Cron v `.github/workflows/auto.yml` zostáva ako záloha, keby externý spúšťač zlyhal.

## Ranný príchod

`prichod.py` v pracovný deň pred 7:45 skontroluje poslednú transakciu:

- Ak je to `Práca / Odchod` (príchod na dnes chýba), pošle mail s tlačidlom
  *„Som v práci — zapíš príchod“*.
- Ak je poslednou transakciou čokoľvek iné, nič nerobí a skončí.

Klik na tlačidlo otvorí stránku na Cloudflare Workeri, ktorá spustí workflow
`prichod.yml` v režime `record`; ten príchod zapíše a overí. Odkaz platí len do 9:30
a len vtedy, keď je poslednou transakciou stále odchod.

Potrebné secrety (okrem `USERNAME` a `PASSWORD`):

| Secret | Význam |
| --- | --- |
| `CONFIRM_URL` | adresa Workera vrátane `?k=...`, ktorá ide do mailu |
| `SMTP_USER` | adresa, z ktorej sa mail posiela (napr. Gmail) |
| `SMTP_PASSWORD` | app password k tej adrese |
| `MAIL_TO` | kam mail chodí (voliteľné, inak `SMTP_USER`) |
| `SMTP_HOST`, `SMTP_PORT` | voliteľné, inak `smtp.gmail.com:465` |

Samotný Worker je v `worker/prichod.js` — nasadenie a jeho dve premenné
(`GH_TOKEN`, `SECRET`) sú popísané priamo v hlavičke toho súboru.

## Android appka

`android/` obsahuje malú appku **Dochádzka**, ktorá zobrazí webBox v prehliadači priamo
v telefóne (na ručné zmeny stavov) a po prvom spustení si pamätá prihlasovacie údaje.
Má jediné povolenie — internet. Podrobnosti a stavba APK: `android/README.md`.

## História
Repo je verejné. Staršie verzie skriptu obsahovali prihlasovacie údaje natvrdo;
história bola prepísaná, aby v nej neboli. Ak máš starý klon, znova si ho naklonuj.
