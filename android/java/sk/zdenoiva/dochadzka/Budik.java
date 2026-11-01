package sk.zdenoiva.dochadzka;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** Ranné oznámenie „Si v práci?Ťuknutím zapíšem príchod.“ a naplánovanie ďalšieho. */
public class Budik extends BroadcastReceiver {

    static final String KANAL = "prichod";
    static final int OZN_DEN = 1;

    @Override
    public void onReceive(Context kontext, Intent zamer) {
        NotificationManager spravca =
                (NotificationManager) kontext.getSystemService(Context.NOTIFICATION_SERVICE);
        if (spravca == null) return;

        int dovod = spravca.getNotificationChannel(KANAL) == null ? 0 : 1;
        if (dovod == 0) {
            NotificationChannel kanal = new NotificationChannel(
                    KANAL, "Ranný príchod", NotificationManager.IMPORTANCE_HIGH);
            kanal.setDescription("Pripomenie zapísať príchod v pracovný deň ráno");
            kanal.enableVibration(true);
            spravca.createNotificationChannel(kanal);
        }

        Intent otvor = new Intent(kontext, MainActivity.class);
        otvor.putExtra(MainActivity.EXTRA_AKCIA, MainActivity.AKCIA_PRICHOD);
        otvor.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent pohlad = PendingIntent.getActivity(
                kontext, 2, otvor,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        Notification oznam = new Notification.Builder(kontext, KANAL)
                .setSmallIcon(R.drawable.ic_notifikacia)
                .setContentTitle("Si v práci?")
                .setContentText("Ťuknutím zapíšem príchod. Ak nie si, oznámenie ignoruj.")
                .setStyle(new Notification.BigTextStyle().bigText(
                        "Ťuknutím zapíšem príchod do webBoxu. Ak nie si v práci, "
                                + "oznámenie ignoruj — nič sa nezapíše."))
                .setPriority(Notification.PRIORITY_HIGH)
                .setCategory(Notification.CATEGORY_REMINDER)
                .setAutoCancel(true)
                .setContentIntent(pohlad)
                .addAction(new Notification.Action.Builder(
                        android.graphics.drawable.Icon.createWithResource(
                                kontext, R.drawable.ic_notifikacia),
                        "Zapíš príchod", pohlad).build())
                .build();

        spravca.notify(OZN_DEN, oznam);

        // Ďalší pracovný deň.
        Planovac.naplanuj(kontext);
    }
}
