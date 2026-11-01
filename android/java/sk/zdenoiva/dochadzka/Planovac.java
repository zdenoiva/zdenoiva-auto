package sk.zdenoiva.dochadzka;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

import java.util.Calendar;

/** Naplánuje ranné oznámenie na najbližší pracovný deň o 07:35. */
final class Planovac {

    static final int HODINA = 7;
    static final int MINUTA = 35;

    private Planovac() {
    }

    static void naplanuj(Context kontext) {
        AlarmManager budik = (AlarmManager) kontext.getSystemService(Context.ALARM_SERVICE);
        if (budik == null) return;

        Intent zamer = new Intent(kontext, Budik.class);
        PendingIntent cakanie = PendingIntent.getBroadcast(
                kontext, 1, zamer,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        long kedy = najblizsiCas();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && budik.canScheduleExactAlarms()) {
            budik.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, kedy, cakanie);
        } else {
            // Bez presnej výnimky — systém to môže o pár minút posunúť, ale oznámenie príde.
            budik.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, kedy, cakanie);
        }
    }

    static long najblizsiCas() {
        Calendar cas = Calendar.getInstance();
        cas.set(Calendar.HOUR_OF_DAY, HODINA);
        cas.set(Calendar.MINUTE, MINUTA);
        cas.set(Calendar.SECOND, 0);
        cas.set(Calendar.MILLISECOND, 0);
        if (cas.getTimeInMillis() <= System.currentTimeMillis()) {
            cas.add(Calendar.DAY_OF_YEAR, 1);
        }
        while (cas.get(Calendar.DAY_OF_WEEK) == Calendar.SATURDAY
                || cas.get(Calendar.DAY_OF_WEEK) == Calendar.SUNDAY) {
            cas.add(Calendar.DAY_OF_YEAR, 1);
        }
        return cas.getTimeInMillis();
    }
}
