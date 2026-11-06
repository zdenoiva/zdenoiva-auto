package sk.zdn.dochadzka;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
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
                        // Odkaz na stránku vydania — appka sama nič nestahuje ani neinštaluje.
                        url = vydanie.optString("html_url", "");
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
                .setMessage("V appke máš " + teraz + ". Po stiahnutí sa Android spýta "
                        + "na potvrdenie inštalácie; údaje aj nastavenia ostanú.")
                .setPositiveButton("Otvoriť GitHub", new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int ktore) {
                        if (ciel != null && !ciel.isEmpty()) {
                            aktivita.startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(ciel)));
                        }
                    }
                })
                .setNegativeButton("Neskôr", null)
                .show();
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
