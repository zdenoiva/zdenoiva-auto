# Dochádzka (Android)

Malá appka, ktorá zobrazí `https://webbox.elko.sk/` a po prvom spustení si zapamätá
prihlasovacie údaje, aby sa prihlásila sama.

## Vlastnosti

- Zobrazí webBox v prehliadači priamo v telefóne — stavy sa dajú meniť ručne.
- **Ranné oznámenie**: v pracovný deň o 07:35 príde oznámenie *„Si v práci?“* s tlačidlom
  *Zapíš príchod*. Appka pri ťuknutí skontroluje poslednú transakciu a zapíše príchod len
  vtedy, keď je poslednou transakciou odchod. Ak už príchod máš, len to oznámi.
- **Nastavenia** (menu ⋮): oznámenie zapnúť/vypnúť, nastaviť čas a zapnúť
  *„Len v pracovné dni“* — vtedy sa vynechajú víkendy aj slovenské štátne sviatky
  vrátane Veľkého piatku a Veľkonočného pondelka. Predvolene 07:35, len pracovné dni.
- Hlášky webu o kamere („Camera error: NotAllowedError“) appka odklikne sama, inak by
  blokovali stránku a stav by sa nenačítal.
- Prihlasovacie údaje si pamätá, prihlási sa samo vrátane obnovenia vypršanej session.
- Pustí len `elko.sk` a jeho subdomény; `tel:`, `mailto:` a cudzie odkazy potichu zablokuje.
- Prihlásenie je v súkromnom úložisku appky (`SharedPreferences`), zálohovanie vypnuté.

## Povolenia

- `INTERNET` — jediné, ktoré appka naozaj potrebuje.
- `POST_NOTIFICATIONS` — kvôli rannému oznámeniu (Android 13+ sa naň raz spýta).
- `RECEIVE_BOOT_COMPLETED` — aby sa oznámenie po reštarte naplánovalo znova (bez otázky).

Kameru, mikrofón, polohu ani súbory appka nemá. WebView dostane také požiadavky **rovno
zamietnuté a potichu** — nikdy sa nezobrazí systémové okno ani hláška.

## Stavba APK

Netreba Gradle ani Android Studio — stačí Android SDK (build-tools, platforma 34) a JDK:

```powershell
powershell -ExecutionPolicy Bypass -File .\build-apk.ps1
```

Vznikne `Dochadzka.apk` (podpísaný vlastným kľúčom `dochadzka.jks`, ktorý sa do repa
necommituje). Ikony sa dajú vygenerovať znova cez `ikony.ps1`.
