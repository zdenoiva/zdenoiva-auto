package sk.zdn.dochadzka;

import android.app.Activity;
import android.app.NotificationManager;
import android.content.Context;
import android.content.Intent;
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
    private CheckBox sviatky;
    private TextView najblizsie;
    private TextView presne;
    private Button povolitPresne;
    private TextView poloha;
    private Button povolitPolohu;

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
        pracovne.setText("Len v pracovné dni (vynechať víkendy)");
        pracovne.setTextSize(15);
        pracovne.setChecked(Planovac.lenPracovne(this));
        pracovne.setPadding(0, (int) (12 * hustota), 0, (int) (2 * hustota));
        box.addView(pracovne);

        sviatky = new CheckBox(this);
        sviatky.setText("Vynechať aj slovenské sviatky");
        sviatky.setTextSize(15);
        sviatky.setChecked(Planovac.vynechatSviatky(this));
        sviatky.setPadding((int) (24 * hustota), 0, 0, (int) (12 * hustota));
        box.addView(sviatky);

        najblizsie = new TextView(this);
        najblizsie.setTextSize(14);
        najblizsie.setPadding(0, 0, 0, (int) (12 * hustota));
        box.addView(najblizsie);

        presne = new TextView(this);
        presne.setTextSize(13);
        presne.setPadding(0, 0, 0, (int) (4 * hustota));
        box.addView(presne);

        povolitPresne = new Button(this);
        povolitPresne.setText("Povoliť presné budíky");
        povolitPresne.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                Planovac.otvorPresneBudiky(Nastavenia.this);
            }
        });
        box.addView(povolitPresne);

        poloha = new TextView(this);
        poloha.setTextSize(13);
        poloha.setPadding(0, (int) (16 * hustota), 0, (int) (4 * hustota));
        box.addView(poloha);

        Button povolitPolohu = new Button(this);
        povolitPolohu.setText("Povoliť polohu");
        povolitPolohu.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                if (!Poloha.maPovolenie(Nastavenia.this)) {
                    requestPermissions(new String[]{"android.permission.ACCESS_FINE_LOCATION"}, 21);
                } else {
                    // poloha „vždy“ sa v Androide 11+ zapína len v nastaveniach appky
                    try {
                        startActivity(new Intent(
                                android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                android.net.Uri.parse("package:" + getPackageName())));
                    } catch (Exception e) {
                        Toast.makeText(Nastavenia.this, "Nastavenie sa nepodarilo otvoriť",
                                Toast.LENGTH_SHORT).show();
                    }
                }
            }
        });
        box.addView(povolitPolohu);
        this.povolitPolohu = povolitPolohu;

        Button mapa = new Button(this);
        mapa.setText("Vybrať miesto práce na mape");
        mapa.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                startActivity(new Intent(Nastavenia.this, Mapa.class));
            }
        });
        box.addView(mapa);

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
        sviatky.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
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
        sviatky.setEnabled(zap && pracovne.isChecked());
        najblizsie.setText(zap ? popisNajblizsieho() : "Oznámenie je vypnuté.");

        boolean presneOk = Planovac.presnePovolene(this);
        presne.setText(presneOk
                ? "Presné budíky: povolené"
                : "Presné budíky: nepovolené — oznámenie môže prísť neskôr");
        povolitPresne.setVisibility(presneOk ? View.GONE : View.VISIBLE);

        boolean polohaOk = Poloha.maPovolenie(this);
        boolean miestoOk = Poloha.maMiesto(this);
        boolean vzdyOk = Poloha.maPolohuVzdy(this);
        if (!polohaOk) {
            poloha.setText("Poloha: nepovolená — appka nemôže overiť, či si v práci.");
        } else if (!vzdyOk) {
            poloha.setText("Poloha: povolená len pri používaní. Pre rannú kontrolu "
                    + "ju nastav na „Vždy“.");
        } else if (!miestoOk) {
            poloha.setText("Poloha: povolená. Miesto práce ešte nie je vybrané.");
        } else {
            poloha.setText("Poloha: povolená vždy. Miesto práce uložené, okruh "
                    + (int) Poloha.OKRUH_METROV + " m.");
        }
        povolitPolohu.setVisibility(vzdyOk ? View.GONE : View.VISIBLE);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (najblizsie != null) obnov();
    }

    private String popisNajblizsieho() {
        long kedy = Planovac.najblizsiCas(cas.getHour(), cas.getMinute(),
                pracovne.isChecked(), sviatky.isChecked());
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
                .putBoolean(Planovac.K_SVIATKY, sviatky.isChecked())
                .apply();

        Planovac.naplanuj(this);

        if (!zap) {
            NotificationManager spravca =
                    (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
            if (spravca != null) spravca.cancel(Oznamy.OZN_DEN);
        }

        Toast.makeText(this,
                zap ? popisNajblizsieho() : "Oznámenie vypnuté",
                Toast.LENGTH_LONG).show();
        finish();
    }
}
