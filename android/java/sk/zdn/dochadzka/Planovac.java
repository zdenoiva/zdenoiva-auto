package sk.zdn.dochadzka;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;

import java.util.Calendar;

/** Naplánuje ranné oznámenie podľa nastavení (čas, pracovné dni, sviatky). */
final class Planovac {

    static final String PREFS = "dochadzka";
    static final String K_ZAPNUTE = "oznam_zapnute";
    static final String K_HODINA = "oznam_hodina";
    static final String K_MINUTA = "oznam_minuta";
    static final String K_PRACOVNE = "oznam_pracovne";

    static final int HODINA = 7;
    static final int MINUTA = 35;

    private Planovac() {
    }

    static SharedPreferences prefs(Context kontext) {
        return kontext.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    static boolean zapnute(Context kontext) {
        return prefs(kontext).getBoolean(K_ZAPNUTE, true);
    }

    static int hodina(Context kontext) {
        return prefs(kontext).getInt(K_HODINA, HODINA);
    }

    static int minuta(Context kontext) {
        return prefs(kontext).getInt(K_MINUTA, MINUTA);
    }

    static boolean lenPracovne(Context kontext) {
        return prefs(kontext).getBoolean(K_PRACOVNE, true);
    }

    private static PendingIntent cakanie(Context kontext) {
        Intent zamer = new Intent(kontext, Budik.class);
        return PendingIntent.getBroadcast(
                kontext, 1, zamer,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    static void naplanuj(Context kontext) {
        AlarmManager budik = (AlarmManager) kontext.getSystemService(Context.ALARM_SERVICE);
        if (budik == null) return;

        PendingIntent poziadavka = cakanie(kontext);
        budik.cancel(poziadavka);

        if (!zapnute(kontext)) return;

        long kedy = najblizsiCas(hodina(kontext), minuta(kontext), lenPracovne(kontext));
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && budik.canScheduleExactAlarms()) {
            budik.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, kedy, poziadavka);
        } else {
            // Bez presnej výnimky — systém to môže o pár minút posunúť, ale oznámenie príde.
            budik.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, kedy, poziadavka);
        }
    }

    static long najblizsiCas(int hodina, int minuta, boolean lenPracovne) {
        Calendar cas = Calendar.getInstance();
        cas.set(Calendar.HOUR_OF_DAY, hodina);
        cas.set(Calendar.MINUTE, minuta);
        cas.set(Calendar.SECOND, 0);
        cas.set(Calendar.MILLISECOND, 0);
        if (cas.getTimeInMillis() <= System.currentTimeMillis()) {
            cas.add(Calendar.DAY_OF_YEAR, 1);
        }
        int poistka = 0;
        while (!vhodnyDen(cas, lenPracovne) && poistka++ < 400) {
            cas.add(Calendar.DAY_OF_YEAR, 1);
        }
        return cas.getTimeInMillis();
    }

    static boolean vhodnyDen(Calendar cas, boolean lenPracovne) {
        if (!lenPracovne) return true;
        int den = cas.get(Calendar.DAY_OF_WEEK);
        if (den == Calendar.SATURDAY || den == Calendar.SUNDAY) return false;
        return !Sviatky.jeSviatok(cas);
    }
}
