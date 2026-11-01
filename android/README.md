# Dochádzka (Android)

Malá appka, ktorá zobrazí `https://webbox.elko.sk/` a po prvom spustení si zapamätá
prihlasovacie údaje, aby sa prihlásila sama.

## Vlastnosti

- Jediné povolenie v manifeste je `INTERNET`. Žiadna kamera, poloha, súbory, notifikácie.
- WebView dostane požiadavky na kameru, mikrofón, GPS aj výber súborov **rovno zamietnuté
  a potichu** — nikdy sa nezobrazí systémové okno ani hláška.
- Pustí len `elko.sk` a jeho subdomény; `tel:`, `mailto:` a cudzie odkazy potichu zablokuje.
- Prihlásenie sa ukladá do súkromného úložiska appky (`SharedPreferences`), zálohovanie
  appky je vypnuté (`allowBackup=false`), takže sa nedostane do cloudu.
- Menu (⋮): *Obnoviť* a *Zmeniť prihlásenie* (zmaže údaje aj cookies).

## Stavba APK

Netreba Gradle ani Android Studio — stačí Android SDK (build-tools, platforma 34) a JDK:

```powershell
powershell -ExecutionPolicy Bypass -File .\build-apk.ps1
```

Vznikne `Dochadzka.apk` (podpísaný vlastným kľúčom `dochadzka.jks`, ktorý sa do repa
necommituje). Ikony sa dajú vygenerovať znova cez `ikony.ps1`.
