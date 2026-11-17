package sk.zdn.dochadzka;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Build;
import android.widget.Toast;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;

/** Kontrola novej verzie appky cez GitHub releases. */
final class Aktualizacie {

    private static final String API =
            "https://api.github.com/repos/zdenoiva/zdenoiva-auto/releases/latest";
    private static final String K_CAS = "update_cas";
    private static final long PAUZA = 24L * 60 * 60 * 1000; // automaticky raz denne

    private Aktualizacie() {
    }

    static String verzia(Context kontext) {
        try {
            return kontext.getPackageManager()
                    .getPackageInfo(kontext.getPackageName(), 0).versionName;
        } catch (Exception e) {
            return "0";
        }
    }

    static void skontroluj(final Activity aktivita, final boolean ticho) {
        final SharedPreferences p = Planovac.prefs(aktivita);
        if (ticho && System.currentTimeMillis() - p.getLong(K_CAS, 0) < PAUZA) return;

        new Thread(new Runnable() {
            public void run() {
                String tag = null;
                String url = null;
                String chyba = null;
                HttpURLConnection spojenie = null;
                try {
                    spojenie = (HttpURLConnection) new URL(API).openConnection();
                    spojenie.setConnectTimeout(15000);
                    spojenie.setReadTimeout(15000);
                    spojenie.setRequestProperty("User-Agent", "Dochadzka");
                    spojenie.setRequestProperty("Accept", "application/vnd.github+json");

                    int stav = spojenie.getResponseCode();
                    if (stav != 200) {
                        chyba = "HTTP " + stav;
                    } else {
                        StringBuilder text = new StringBuilder();
                        BufferedReader citac = new BufferedReader(
                                new InputStreamReader(spojenie.getInputStream(), "UTF-8"));
                        String riadok;
                        while ((riadok = citac.readLine()) != null) text.append(riadok);
                        citac.close();

                        JSONObject vydanie = new JSONObject(text.toString());
                        tag = vydanie.optString("tag_name", "");
                        // Priamy odkaz na APK z vydania (appka ho stiahne a ponúkne inštaláciu).
                        org.json.JSONArray assety = vydanie.optJSONArray("assets");
                        if (assety != null) {
                            for (int i = 0; i < assety.length(); i++) {
                                JSONObject asset = assety.optJSONObject(i);
                                if (asset != null
                                        && "Dochadzka.apk".equals(asset.optString("name"))) {
                                    url = asset.optString("browser_download_url");
                                }
                            }
                        }
                        if (url == null || url.isEmpty()) url = vydanie.optString("html_url", "");
                        p.edit().putLong(K_CAS, System.currentTimeMillis()).apply();
                    }
                } catch (Exception e) {
                    chyba = e.getMessage();
                } finally {
                    if (spojenie != null) spojenie.disconnect();
                }

                final String fTag = tag;
                final String fUrl = url;
                final String fChyba = chyba;
                aktivita.runOnUiThread(new Runnable() {
                    public void run() {
                        ukaz(aktivita, fTag, fUrl, fChyba, ticho);
                    }
                });
            }
        }).start();
    }

    private static void ukaz(Activity aktivita, String tag, String url, String chyba,
                             boolean ticho) {
        if (chyba != null) {
            if (!ticho) {
                Toast.makeText(aktivita, "Nepodarilo sa spojiť s GitHubom (" + chyba + ")",
                        Toast.LENGTH_LONG).show();
            }
            return;
        }

        String najnovsia = tag == null ? "" : tag.replaceFirst("^[vV]", "");
        String teraz = verzia(aktivita);

        if (!jeNovsia(najnovsia, teraz)) {
            if (!ticho) {
                Toast.makeText(aktivita, "Máš najnovšiu verziu (" + teraz + ")",
                        Toast.LENGTH_LONG).show();
            }
            return;
        }

        final String ciel = url;
        new AlertDialog.Builder(aktivita)
                .setTitle("Dostupná verzia " + najnovsia)
                .setMessage("V appke máš " + teraz + ". Appka ju stiahne a hneď ponúkne "
                        + "inštaláciu; údaje aj nastavenia ostanú.")
                .setPositiveButton("Stiahnuť a nainštalovať", new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int ktore) {
                        if (ciel != null && !ciel.isEmpty()) {
                            stiahniAInstaluj(aktivita, ciel);
                        }
                    }
                })
                .setNegativeButton("Neskôr", null)
                .show();
    }

    /** Stiahne APK a po dokončení ponúkne systémovú inštaláciu. */
    static void stiahniAInstaluj(final Activity aktivita, final String adresa) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                && !aktivita.getPackageManager().canRequestPackageInstalls()) {
            new AlertDialog.Builder(aktivita)
                    .setTitle("Povoliť inštaláciu")
                    .setMessage("Android potrebuje povolenie, aby appka mohla nainštalovať "
                            + "svoju novú verziu. Zapni „Povoliť z tohto zdroja“ a skús to znova.")
                    .setPositiveButton("Povoliť", new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int ktore) {
                            try {
                                aktivita.startActivity(new Intent(
                                        android.provider.Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                                        Uri.parse("package:" + aktivita.getPackageName())));
                            } catch (Exception e) {
                                Toast.makeText(aktivita, "Nastavenie sa nepodarilo otvoriť",
                                        Toast.LENGTH_LONG).show();
                            }
                        }
                    })
                    .setNegativeButton("Zrušiť", null)
                    .show();
            return;
        }

        final android.app.DownloadManager spravca =
                (android.app.DownloadManager) aktivita.getSystemService(Context.DOWNLOAD_SERVICE);
        if (spravca == null) return;

        android.app.DownloadManager.Request poziadavka =
                new android.app.DownloadManager.Request(Uri.parse(adresa));
        poziadavka.setTitle("Dochádzka");
        poziadavka.setDescription("Nová verzia appky");
        poziadavka.setNotificationVisibility(
                android.app.DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
        poziadavka.setDestinationInExternalFilesDir(aktivita,
                android.os.Environment.DIRECTORY_DOWNLOADS, "Dochadzka.apk");

        final long id;
        try {
            id = spravca.enqueue(poziadavka);
        } catch (Exception e) {
            Toast.makeText(aktivita, "Stiahnutie sa nepodarilo spustiť", Toast.LENGTH_LONG).show();
            return;
        }
        Toast.makeText(aktivita, "Sťahujem novú verziu…", Toast.LENGTH_SHORT).show();

        final android.os.Handler hlavny = new android.os.Handler(
                android.os.Looper.getMainLooper());
        final Runnable[] krok = new Runnable[1];
        krok[0] = new Runnable() {
            public void run() {
                android.database.Cursor kurzor = spravca.query(
                        new android.app.DownloadManager.Query().setFilterById(id));
                int stav = -1;
                if (kurzor != null) {
                    if (kurzor.moveToFirst()) {
                        stav = kurzor.getInt(kurzor.getColumnIndex(
                                android.app.DownloadManager.COLUMN_STATUS));
                    }
                    kurzor.close();
                }

                if (stav == android.app.DownloadManager.STATUS_SUCCESSFUL) {
                    Uri subor = spravca.getUriForDownloadedFile(id);
                    Intent instalacia = new Intent(Intent.ACTION_VIEW)
                            .setDataAndType(subor, "application/vnd.android.package-archive")
                            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION
                                    | Intent.FLAG_ACTIVITY_NEW_TASK);
                    try {
                        aktivita.startActivity(instalacia);
                    } catch (Exception e) {
                        Toast.makeText(aktivita, "Inštaláciu sa nepodarilo otvoriť",
                                Toast.LENGTH_LONG).show();
                    }
                    return;
                }
                if (stav == android.app.DownloadManager.STATUS_FAILED) {
                    Toast.makeText(aktivita, "Stiahnutie zlyhalo", Toast.LENGTH_LONG).show();
                    return;
                }
                hlavny.postDelayed(krok[0], 1500);
            }
        };
        hlavny.postDelayed(krok[0], 1500);
    }

    private static boolean jeNovsia(String nova, String teraz) {
        String[] a = nova.split("\\.");
        String[] b = teraz.split("\\.");
        int dlzka = Math.max(a.length, b.length);
        for (int i = 0; i < dlzka; i++) {
            int x = i < a.length ? cislo(a[i]) : 0;
            int y = i < b.length ? cislo(b[i]) : 0;
            if (x != y) return x > y;
        }
        return false;
    }

    private static int cislo(String text) {
        StringBuilder cislice = new StringBuilder();
        for (int i = 0; i < text.length(); i++) {
            char znak = text.charAt(i);
            if (Character.isDigit(znak)) {
                cislice.append(znak);
            } else {
                break;
            }
        }
        try {
            return Integer.parseInt(cislice.toString());
        } catch (Exception e) {
            return 0;
        }
    }
}
