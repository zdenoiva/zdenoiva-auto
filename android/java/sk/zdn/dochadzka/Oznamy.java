package sk.zdn.dochadzka;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;

/** Oznámenia appky na jednom mieste. */
final class Oznamy {

    static final String KANAL = "prichod";
    static final String KANAL_SLUZBA = "sluzba";
    static final int OZN_DEN = 1;
    static final int OZN_SLUZBA = 2;

    private Oznamy() {
    }

    static void pripravKanaly(Context kontext) {
        NotificationManager spravca =
                (NotificationManager) kontext.getSystemService(Context.NOTIFICATION_SERVICE);
        if (spravca == null) return;

        if (spravca.getNotificationChannel(KANAL) == null) {
            NotificationChannel kanal = new NotificationChannel(
                    KANAL, "Ranný príchod", NotificationManager.IMPORTANCE_HIGH);
            kanal.setDescription("Pripomenie zapísať príchod v pracovný deň ráno");
            kanal.enableVibration(true);
            spravca.createNotificationChannel(kanal);
        }
        if (spravca.getNotificationChannel(KANAL_SLUZBA) == null) {
            NotificationChannel kanal = new NotificationChannel(
                    KANAL_SLUZBA, "Kontrola polohy", NotificationManager.IMPORTANCE_LOW);
            kanal.setDescription("Krátka kontrola polohy pred zápisom príchodu");
            spravca.createNotificationChannel(kanal);
        }
    }

    static Notification sluzba(Context kontext, String text) {
        return new Notification.Builder(kontext, KANAL_SLUZBA)
                .setSmallIcon(R.drawable.ic_notifikacia)
                .setContentTitle("Dochádzka")
                .setContentText(text)
                .setOngoing(true)
                .build();
    }

    /** Obyčajné oznámenie „Si v práci?“ s ťuknutím, ktoré príchod zapíše. */
    static void siVPraci(Context kontext) {
        pripravKanaly(kontext);
        NotificationManager spravca =
                (NotificationManager) kontext.getSystemService(Context.NOTIFICATION_SERVICE);
        if (spravca == null) return;

        Intent otvor = new Intent(kontext, MainActivity.class);
        otvor.putExtra(MainActivity.EXTRA_AKCIA, MainActivity.AKCIA_PRICHOD);
        otvor.putExtra(MainActivity.EXTRA_CAS, System.currentTimeMillis());
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
    }

    /** Krátka informácia, napr. že príchod bol zapísaný sám. */
    static void sprava(Context kontext, String titul, String text) {
        pripravKanaly(kontext);
        NotificationManager spravca =
                (NotificationManager) kontext.getSystemService(Context.NOTIFICATION_SERVICE);
        if (spravca == null) return;

        Intent otvor = new Intent(kontext, MainActivity.class);
        otvor.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent pohlad = PendingIntent.getActivity(
                kontext, 3, otvor,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        Notification oznam = new Notification.Builder(kontext, KANAL)
                .setSmallIcon(R.drawable.ic_notifikacia)
                .setContentTitle(titul)
                .setContentText(text)
                .setStyle(new Notification.BigTextStyle().bigText(text))
                .setPriority(Notification.PRIORITY_HIGH)
                .setAutoCancel(true)
                .setContentIntent(pohlad)
                .build();

        spravca.notify(OZN_DEN, oznam);
    }
}
