package sk.zdn.dochadzka;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.os.Looper;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/** Uložené miesto práce a zisťovanie polohy. */
final class Poloha {

    static final String K_LAT = "praca_lat";
    static final String K_LON = "praca_lon";
    static final double OKRUH_METROV = 70.0;

    private Poloha() {
    }

    private static SharedPreferences prefs(Context kontext) {
        return kontext.getSharedPreferences("dochadzka", Context.MODE_PRIVATE);
    }

    static boolean maMiesto(Context kontext) {
        return prefs(kontext).contains(K_LAT) && prefs(kontext).contains(K_LON);
    }

    static double lat(Context kontext) {
        return Double.longBitsToDouble(prefs(kontext).getLong(K_LAT, 0));
    }

    static double lon(Context kontext) {
        return Double.longBitsToDouble(prefs(kontext).getLong(K_LON, 0));
    }

    static void uloz(Context kontext, double sirka, double dlzka) {
        prefs(kontext).edit()
                .putLong(K_LAT, Double.doubleToRawLongBits(sirka))
                .putLong(K_LON, Double.doubleToRawLongBits(dlzka))
                .apply();
    }

    static boolean maPovolenie(Context kontext) {
        return kontext.checkSelfPermission("android.permission.ACCESS_FINE_LOCATION")
                == PackageManager.PERMISSION_GRANTED;
    }

    /** Poloha „vždy“ — bez nej Android v čase budíka polohu na pozadí nedovolí. */
    static boolean maPolohuVzdy(Context kontext) {
        if (android.os.Build.VERSION.SDK_INT < 29) return maPovolenie(kontext);
        return maPovolenie(kontext)
                && kontext.checkSelfPermission("android.permission.ACCESS_BACKGROUND_LOCATION")
                == PackageManager.PERMISSION_GRANTED;
    }

    /** Vzdialenosť dvoch súradníc v metroch. */
    static double vzdialenost(double lat1, double lon1, double lat2, double lon2) {
        double r = 6371000.0;
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLon / 2) * Math.sin(dLon / 2);
        return 2 * r * Math.asin(Math.sqrt(a));
    }

    /**
     * Zistí polohu: najprv použije čerstvú poslednú známu, inak čaká na nové určenie.
     * Volaj z vedľajšieho vlákna — metóda blokuje.
     */
    static Location zisti(Context kontext, long maxCakanieMs) {
        LocationManager spravca =
                (LocationManager) kontext.getSystemService(Context.LOCATION_SERVICE);
        if (spravca == null) return null;
        if (!maPovolenie(kontext)) return null;

        Location posledna = najcerstvejsia(spravca);
        if (posledna != null
                && System.currentTimeMillis() - posledna.getTime() < 10 * 60 * 1000) {
            return posledna;
        }

        final CountDownLatch cakanie = new CountDownLatch(1);
        final Location[] vysledok = {posledna};

        LocationListener odposluch = new LocationListener() {
            public void onLocationChanged(Location nova) {
                if (nova == null) return;
                if (vysledok[0] == null || nova.getTime() > vysledok[0].getTime()) {
                    vysledok[0] = nova;
                }
                if (nova.getAccuracy() <= 100) cakanie.countDown();
            }

            public void onStatusChanged(String provider, int stav, Bundle extra) {
            }

            public void onProviderEnabled(String provider) {
            }

            public void onProviderDisabled(String provider) {
            }
        };

        try {
            for (String poskytovatel : new String[]{LocationManager.GPS_PROVIDER,
                    LocationManager.NETWORK_PROVIDER}) {
                if (spravca.isProviderEnabled(poskytovatel)) {
                    spravca.requestLocationUpdates(poskytovatel, 1000, 0, odposluch,
                            Looper.getMainLooper());
                }
            }
            cakanie.await(maxCakanieMs, TimeUnit.MILLISECONDS);
        } catch (SecurityException e) {
            return posledna;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } finally {
            try {
                spravca.removeUpdates(odposluch);
            } catch (Exception ignorovane) {
            }
        }
        return vysledok[0];
    }

    private static Location najcerstvejsia(LocationManager spravca) {
        Location najlepsia = null;
        try {
            List<String> poskytovatelia = spravca.getProviders(true);
            for (String poskytovatel : poskytovatelia) {
                Location l = spravca.getLastKnownLocation(poskytovatel);
                if (l != null && (najlepsia == null || l.getTime() > najlepsia.getTime())) {
                    najlepsia = l;
                }
            }
        } catch (SecurityException ignorovane) {
        }
        return najlepsia;
    }
}
