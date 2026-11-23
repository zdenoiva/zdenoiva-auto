package sk.zdn.dochadzka;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

/** Budík: spustí službu, ktorá podľa polohy buď zapíše príchod, alebo ukáže oznámenie. */
public class Budik extends BroadcastReceiver {

    @Override
    public void onReceive(Context kontext, Intent zamer) {
        if (!Planovac.zapnute(kontext)) return;

        try {
            Intent sluzba = new Intent(kontext, PrichodService.class);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                kontext.startForegroundService(sluzba);
            } else {
                kontext.startService(sluzba);
            }
        } catch (Exception chyba) {
            // Android 12+ niekedy nedovolí spustiť službu na pozadí — vtedy aspoň oznámenie.
            Oznamy.siVPraci(kontext);
        }

        // Ďalší pracovný deň.
        Planovac.naplanuj(kontext);
    }
}
