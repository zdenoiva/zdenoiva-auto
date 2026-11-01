# zdenoiva-auto

Skript zapíše vo webBox (ELKO) odchod z práce. Beží na GitHub Actions, bez servera.

## Čo robí

- Prihlási sa do `webbox.elko.sk` a prečíta poslednú transakciu z virtuálnej čítačky.
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

## História

Repo je verejné. Staršie verzie skriptu obsahovali prihlasovacie údaje natvrdo;
história bola prepísaná, aby v nej neboli. Ak máš starý klon, znova si ho naklonuj.
