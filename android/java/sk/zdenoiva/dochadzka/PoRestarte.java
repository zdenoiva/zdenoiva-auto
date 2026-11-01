package sk.zdenoiva.dochadzka;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** Po reštarte telefónu naplánuje ranné oznámenie znova. */
public class PoRestarte extends BroadcastReceiver {

    @Override
    public void onReceive(Context kontext, Intent zamer) {
        if (!Intent.ACTION_BOOT_COMPLETED.equals(zamer.getAction())) return;
        boolean maUdaje = !kontext.getSharedPreferences("dochadzka", Context.MODE_PRIVATE)
                .getString("meno", "").isEmpty();
        if (maUdaje) Planovac.naplanuj(kontext);
    }
}
