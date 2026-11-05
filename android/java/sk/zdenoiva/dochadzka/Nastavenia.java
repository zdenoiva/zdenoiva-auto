package sk.zdenoiva.dochadzka;

import android.app.Activity;
import android.app.NotificationManager;
import android.content.Context;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.TimePicker;
import android.widget.Toast;

import java.util.Calendar;

/** Nastavenia ranného oznámenia: zapnuté/vypnuté, čas, len pracovné dni. */
public class Nastavenia extends Activity {

    private static final String[] DNI = {"ne", "po", "ut", "st", "št", "pi", "so"};

    private Switch zapnute;
    private TimePicker cas;
    private CheckBox pracovne;
    private TextView najblizsie;

    @Override
    protected void onCreate(Bundle stav) {
        super.onCreate(stav);

        float hustota = getResources().getDisplayMetrics().density;
        int okraj = (int) (20 * hustota);

        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(okraj, okraj, okraj, okraj);

        zapnute = new Switch(this);
        zapnute.setText("Ranné oznámenie");
        zapnute.setTextSize(17);
        zapnute.setChecked(Planovac.zapnute(this));
        box.addView(zapnute);

        TextView popis = new TextView(this);
        popis.setText("V pracovný deň ráno príde oznámenie „Si v práci?“ s tlačidlom, "
                + "ktoré zapíše príchod.");
        popis.setTextSize(13);
        popis.setAlpha(0.7f);
        popis.setPadding(0, (int) (6 * hustota), 0, (int) (16 * hustota));
        box.addView(popis);

        cas = new TimePicker(this);
        cas.setIs24HourView(true);
        cas.setHour(Planovac.hodina(this));
        cas.setMinute(Planovac.minuta(this));
        box.addView(cas);

        pracovne = new CheckBox(this);
        pracovne.setText("Len v pracovné dni (vynechať víkendy a slovenské sviatky)");
        pracovne.setTextSize(15);
        pracovne.setChecked(Planovac.lenPracovne(this));
        pracovne.setPadding(0, (int) (12 * hustota), 0, (int) (12 * hustota));
        box.addView(pracovne);

        najblizsie = new TextView(this);
        najblizsie.setTextSize(14);
        najblizsie.setPadding(0, 0, 0, (int) (20 * hustota));
        box.addView(najblizsie);

        Button ulozit = new Button(this);
        ulozit.setText("Uložiť");
        ulozit.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                ulozit();
            }
        });
        box.addView(ulozit);

        ScrollView posuv = new ScrollView(this);
        posuv.addView(box);
        setContentView(posuv);

        zapnute.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            public void onCheckedChanged(CompoundButton b, boolean zap) {
                obnov();
            }
        });
        pracovne.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            public void onCheckedChanged(CompoundButton b, boolean zap) {
                obnov();
            }
        });
        cas.setOnTimeChangedListener(new TimePicker.OnTimeChangedListener() {
            public void onTimeChanged(TimePicker p, int hodina, int minuta) {
                obnov();
            }
        });

        obnov();
    }

    private void obnov() {
        boolean zap = zapnute.isChecked();
        cas.setEnabled(zap);
        pracovne.setEnabled(zap);
        najblizsie.setText(zap ? popisNajblizsieho() : "Oznámenie je vypnuté.");
    }

    private String popisNajblizsieho() {
        long kedy = Planovac.najblizsiCas(cas.getHour(), cas.getMinute(), pracovne.isChecked());
        Calendar c = Calendar.getInstance();
        c.setTimeInMillis(kedy);
        int den = c.get(Calendar.DAY_OF_WEEK) - 1;
        return "Najbližšie oznámenie: " + DNI[den] + " "
                + c.get(Calendar.DAY_OF_MONTH) + "." + (c.get(Calendar.MONTH) + 1) + ". o "
                + String.format("%02d:%02d", c.get(Calendar.HOUR_OF_DAY), c.get(Calendar.MINUTE));
    }

    private void ulozit() {
        boolean zap = zapnute.isChecked();
        Planovac.prefs(this).edit()
                .putBoolean(Planovac.K_ZAPNUTE, zap)
                .putInt(Planovac.K_HODINA, cas.getHour())
                .putInt(Planovac.K_MINUTA, cas.getMinute())
                .putBoolean(Planovac.K_PRACOVNE, pracovne.isChecked())
                .apply();

        Planovac.naplanuj(this);

        if (!zap) {
            NotificationManager spravca =
                    (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
            if (spravca != null) spravca.cancel(Budik.OZN_DEN);
        }

        Toast.makeText(this,
                zap ? popisNajblizsieho() : "Oznámenie vypnuté",
                Toast.LENGTH_LONG).show();
        finish();
    }
}
