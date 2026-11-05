package sk.zdenoiva.dochadzka;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Calendar;
import java.util.Locale;

/**
 * Slovenské štátne sviatky a dni pracovného pokoja (zákon 241/1993).
 *
 * Zoznam sa raz za týždeň ťahá z GitHubu (súbor android/sviatky.json), takže keď sa
 * sviatky zmenia zákonom, stačí upraviť ten súbor — netreba novú verziu appky.
 * Kým zoznam pre daný rok nie je na GitHube, počíta sa záložným spôsobom vstavaným
 * v appke (pevné dátumy + Veľký piatok a Veľkonočný pondelok).
 */
final class Sviatky {

    private static final String ZOZNAM_URL =
            "https://raw.githubusercontent.com/zdenoiva/zdenoiva-auto/main/android/sviatky.json";
    private static final String K_JSON = "sviatky_json";
    private static final String K_CAS = "sviatky_cas";
    private static final long PLATNOST = 7L * 24 * 60 * 60 * 1000; // týždeň

    private static JSONObject cache;

    private Sviatky() {
    }

    /** Načíta uložený zoznam (volaj pri štarte appky, hneď je dostupný aj offline). */
    static synchronized void nacitaj(Context kontext) {
        String json = Planovac.prefs(kontext).getString(K_JSON, "");
        if (json.isEmpty()) return;
        try {
            cache = new JSONObject(json);
        } catch (Exception e) {
            cache = null;
        }
    }

    /** Stiahne novší zoznam z GitHubu, ak je uložený starší ako týždeň. */
    static void obnov(final Context kontext, boolean silou) {
        final SharedPreferences p = Planovac.prefs(kontext);
        long posledne = p.getLong(K_CAS, 0);
        if (!silou && System.currentTimeMillis() - posledne < PLATNOST) return;

        new Thread(new Runnable() {
            public void run() {
                HttpURLConnection spojenie = null;
                try {
                    spojenie = (HttpURLConnection) new URL(ZOZNAM_URL).openConnection();
                    spojenie.setConnectTimeout(15000);
                    spojenie.setReadTimeout(15000);
                    spojenie.setRequestProperty("User-Agent", "Dochadzka");
                    if (spojenie.getResponseCode() != 200) {
                        Log.i("Dochadzka", "sviatky: HTTP " + spojenie.getResponseCode());
                        return;
                    }
                    StringBuilder text = new StringBuilder();
                    BufferedReader citac = new BufferedReader(
                            new InputStreamReader(spojenie.getInputStream(), "UTF-8"));
                    String riadok;
                    while ((riadok = citac.readLine()) != null) text.append(riadok);
                    citac.close();

                    JSONObject nove = new JSONObject(text.toString());
                    synchronized (Sviatky.class) {
                        cache = nove;
                    }
                    p.edit().putString(K_JSON, text.toString())
                            .putLong(K_CAS, System.currentTimeMillis()).apply();
                    Log.i("Dochadzka", "sviatky: načítané roky " + nove.length());
                    // Nový zoznam môže zmeniť najbližší termín (napr. keď je deň sviatok).
                    Planovac.naplanuj(kontext);
                } catch (Exception chyba) {
                    Log.i("Dochadzka", "sviatky: nepodarilo sa načítať (" + chyba.getMessage() + ")");
                } finally {
                    if (spojenie != null) spojenie.disconnect();
                }
            }
        }).start();
    }

    static synchronized boolean jeSviatok(Calendar den) {
        if (cache != null) {
            JSONArray dni = cache.optJSONArray(String.valueOf(den.get(Calendar.YEAR)));
            if (dni != null) {
                String kluc = String.format(Locale.US, "%04d-%02d-%02d",
                        den.get(Calendar.YEAR), den.get(Calendar.MONTH) + 1,
                        den.get(Calendar.DAY_OF_MONTH));
                for (int i = 0; i < dni.length(); i++) {
                    if (kluc.equals(dni.optString(i))) return true;
                }
                return false; // rok je v zozname z GitHubu, takže tento deň sviatok nie je
            }
        }
        return jeSviatokVstaveny(den);
    }

    private static boolean jeSviatokVstaveny(Calendar den) {
        int d = den.get(Calendar.DAY_OF_MONTH);
        int m = den.get(Calendar.MONTH) + 1;
        int rok = den.get(Calendar.YEAR);

        if (m == 1 && d == 1) return true;    // Deň vzniku SR
        if (m == 1 && d == 6) return true;    // Zjavenie Pána
        if (m == 5 && d == 1) return true;    // Sviatok práce
        if (m == 5 && d == 8) return true;    // Deň víťazstva nad fašizmom
        if (m == 7 && d == 5) return true;    // Sv. Cyril a Metod
        if (m == 8 && d == 29) return true;   // Výročie SNP
        if (m == 9 && d == 1) return true;    // Deň Ústavy SR
        if (m == 9 && d == 15) return true;   // Sedembolestná Panna Mária
        if (m == 11 && d == 1) return true;   // Sviatok všetkých svätých
        if (m == 11 && d == 17) return true;  // Deň boja za slobodu a demokraciu
        if (m == 12 && d == 24) return true;  // Štedrý deň
        if (m == 12 && d == 25) return true;  // Prvý sviatok vianočný
        if (m == 12 && d == 26) return true;  // Druhý sviatok vianočný

        int[] velkaNoc = velkaNoc(rok);
        Calendar nedela = Calendar.getInstance();
        nedela.clear();
        nedela.set(rok, velkaNoc[0] - 1, velkaNoc[1]);

        Calendar piatok = (Calendar) nedela.clone();
        piatok.add(Calendar.DAY_OF_MONTH, -2);
        if (rovnakyDen(den, piatok)) return true;

        Calendar pondelok = (Calendar) nedela.clone();
        pondelok.add(Calendar.DAY_OF_MONTH, 1);
        return rovnakyDen(den, pondelok);
    }

    private static boolean rovnakyDen(Calendar a, Calendar b) {
        return a.get(Calendar.YEAR) == b.get(Calendar.YEAR)
                && a.get(Calendar.MONTH) == b.get(Calendar.MONTH)
                && a.get(Calendar.DAY_OF_MONTH) == b.get(Calendar.DAY_OF_MONTH);
    }

    /** Veľkonočná nedeľa (gregoriánsky kalendár) ako {mesiac, deň}. */
    static int[] velkaNoc(int rok) {
        int a = rok % 19;
        int b = rok / 100;
        int c = rok % 100;
        int d = b / 4;
        int e = b % 4;
        int f = (b + 8) / 25;
        int g = (b - f + 1) / 3;
        int h = (19 * a + b - d - g + 15) % 30;
        int i = c / 4;
        int k = c % 4;
        int l = (32 + 2 * e + 2 * i - h - k) % 7;
        int m = (a + 11 * h + 22 * l) / 451;
        int mesiac = (h + l - 7 * m + 114) / 31;
        int den = ((h + l - 7 * m + 114) % 31) + 1;
        return new int[]{mesiac, den};
    }
}
