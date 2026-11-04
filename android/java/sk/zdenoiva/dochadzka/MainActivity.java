package sk.zdenoiva.dochadzka;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.NotificationManager;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.CookieManager;
import android.webkit.GeolocationPermissions;
import android.webkit.JsResult;
import android.webkit.PermissionRequest;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.Toast;

import org.json.JSONObject;
import org.json.JSONTokener;

/**
 * Dochádzka — prihlásenie do webBox (ELKO) a zobrazenie virtuálnej čítačky.
 *
 * Appka nemá prístup ku kamere, mikrofónu, GPS ani súborom. WebView dostane také
 * požiadavky rovno zamietnuté a nikdy sa na ne nepýta.
 *
 * Z oznámenia „Si v práci?“ sa dá príchod zapísať jedným ťuknutím — appka pritom
 * skontroluje stav a zapíše len vtedy, keď poslednou transakciou je odchod.
 */
public class MainActivity extends Activity {

    public static final String EXTRA_AKCIA = "akcia";
    public static final String AKCIA_PRICHOD = "prichod";

    private static final String START_URL = "https://webbox.elko.sk/";
    private static final String LOGIN_URL = "logindispatcher";
    private static final int MAX_POKUSOV_PRIHLASENIA = 3;
    private static final int ZIADOST_OZN = 11;

    private static final String JS_STAV =
            "(function(){"
            // Ak stránka zobrazí hlášku o kamere ako vlastné okno, klikni v ňom OK.
            + "try{"
            + "var telo=document.body;"
            + "if(telo&&/camera error|notallowederror/i.test(telo.innerText||'')){"
            + "var b=telo.querySelectorAll('button,[role=button],.btn,a');"
            + "for(var j=0;j<b.length;j++){var t=(b[j].innerText||'').trim().toLowerCase();"
            + "if(t==='ok'||t==='close'||t==='zavrieť'||t==='zavriet'){b[j].click();break;}}"
            + "}}catch(e){}"
            + "var t=document.getElementById('rdr-last-transaction');"
            + "if(!t)return 'cakam';"
            + "var s=(t.innerText||'').replace(/\\s+/g,' ');"
            + "if(s.indexOf('Príchod')>=0)return 'uz';"
            + "if(s.indexOf('Odchod')<0)return 'ine';"
            + "var a=document.querySelectorAll('button.rdr-make-transaction'),b=null,i,l;"
            + "for(i=0;i<a.length;i++){l=a[i].getAttribute('data-label')||a[i].innerText||'';"
            + "if(l.indexOf('Príchod')>=0){b=a[i];break;}}"
            + "if(!b)return 'netlacidlo';"
            + "b.click();return 'kliknute';"
            + "})()";

    private static final String JS_POTVRD =
            "(function(){var b=document.getElementById('rdr-confirm-btn');"
            + "if(!b)return 'nie';b.click();return 'ok';})()";

    private static final String JS_TEXTOVA_STAV =
            "(function(){var t=document.getElementById('rdr-last-transaction');"
            + "return t?(t.innerText||'').replace(/\\s+/g,' '):'';})()";

    /** Hlášky o kamere/mikrofóne od webu — appka ich odklikne sama, bez okna. */
    private static boolean jeChybaKamery(String sprava) {
        if (sprava == null) return false;
        String s = sprava.toLowerCase();
        return s.contains("camera") || s.contains("kamera")
                || s.contains("notallowederror") || s.contains("getusermedia")
                || s.contains("mediadevices") || s.contains("permission denied");
    }

    private SharedPreferences prefy;
    private WebView web;
    private final Handler hlavny = new Handler(Looper.getMainLooper());

    private int pokusovOPrihlasenie = 0;

    private boolean cakamNaPrichod = false;
    private int pokusyOPrichod = 0;

    @Override
    protected void onCreate(Bundle stav) {
        super.onCreate(stav);

        prefy = getSharedPreferences("dochadzka", MODE_PRIVATE);

        FrameLayout koren = new FrameLayout(this);
        web = new WebView(this);
        koren.addView(web, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        setContentView(koren);
        getWindow().setBackgroundDrawable(new ColorDrawable(Color.WHITE));

        nastavWeb();
        zvysPovolenieOznameni();

        if (prefy.getString("meno", "").isEmpty()) {
            vyziadajUdaje();
        } else {
            web.loadUrl(START_URL);
            Planovac.naplanuj(this);
        }

        spracujZamer(getIntent());
    }

    @Override
    protected void onNewIntent(Intent zamer) {
        super.onNewIntent(zamer);
        setIntent(zamer);
        spracujZamer(zamer);
    }

    private void spracujZamer(Intent zamer) {
        if (zamer == null) return;
        if (AKCIA_PRICHOD.equals(zamer.getStringExtra(EXTRA_AKCIA))) {
            zamer.removeExtra(EXTRA_AKCIA);
            spustiPrichod();
        }
    }

    private void zvysPovolenieOznameni() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                && checkSelfPermission("android.permission.POST_NOTIFICATIONS")
                != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{"android.permission.POST_NOTIFICATIONS"},
                    ZIADOST_OZN);
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private void nastavWeb() {
        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setDatabaseEnabled(true);
        s.setGeolocationEnabled(false);
        s.setAllowFileAccess(false);
        s.setAllowContentAccess(false);
        s.setSaveFormData(false);
        s.setSupportZoom(true);
        s.setBuiltInZoomControls(false);
        s.setLoadWithOverviewMode(true);
        s.setUseWideViewPort(true);
        s.setMediaPlaybackRequiresUserGesture(true);
        s.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);

        CookieManager cookies = CookieManager.getInstance();
        cookies.setAcceptCookie(true);
        cookies.setAcceptThirdPartyCookies(web, false);

        web.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView v, WebResourceRequest poziadavka) {
                return !povolenaAdresa(poziadavka.getUrl());
            }

            @Override
            @SuppressWarnings("deprecation")
            public boolean shouldOverrideUrlLoading(WebView v, String adresa) {
                return !povolenaAdresa(Uri.parse(adresa));
            }

            @Override
            public void onPageFinished(WebView v, String adresa) {
                prihlasAkTreba(adresa);
                if (cakamNaPrichod && adresa != null && !adresa.contains(LOGIN_URL)) {
                    skusPrichod();
                }
            }
        });

        web.setWebChromeClient(new WebChromeClient() {
            // Kamera, mikrofón a čokoľvek ďalšie: potichu zamietnuť.
            @Override
            public void onPermissionRequest(PermissionRequest request) {
                request.deny();
            }

            // GPS: zamietnuť a nezobrazovať otázku.
            @Override
            public void onGeolocationPermissionsShowPrompt(
                    String origin, GeolocationPermissions.Callback callback) {
                callback.invoke(origin, false, false);
            }

            // Výber súborov: žiadne okno, potichu nič.
            @Override
            public boolean onShowFileChooser(WebView v, ValueCallback<Uri[]> callback,
                                             FileChooserParams params) {
                return false;
            }

            // Potvrdenia samotného webu (napr. „Zapísať odchod?“) majú fungovať.
            @Override
            public boolean onJsAlert(WebView v, String url, String sprava, final JsResult vysledok) {
                if (jeChybaKamery(sprava)) {
                    // Web sa sťažuje, že nemá kameru. Odfajkneme potichu — inak by
                    // hláška blokovala stránku a stav by sa nenačítal.
                    vysledok.confirm();
                    if (cakamNaPrichod) {
                        hlavny.postDelayed(new Runnable() {
                            public void run() { skusPrichod(); }
                        }, 800);
                    }
                    return true;
                }
                new AlertDialog.Builder(MainActivity.this)
                        .setMessage(sprava)
                        .setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface d, int k) { vysledok.confirm(); }
                        })
                        .setOnCancelListener(new DialogInterface.OnCancelListener() {
                            public void onCancel(DialogInterface d) { vysledok.cancel(); }
                        })
                        .show();
                return true;
            }

            @Override
            public boolean onJsConfirm(WebView v, String url, String sprava, final JsResult vysledok) {
                if (jeChybaKamery(sprava)) {
                    vysledok.confirm();
                    return true;
                }
                new AlertDialog.Builder(MainActivity.this)
                        .setMessage(sprava)
                        .setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface d, int k) { vysledok.confirm(); }
                        })
                        .setNegativeButton(android.R.string.cancel, new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface d, int k) { vysledok.cancel(); }
                        })
                        .setOnCancelListener(new DialogInterface.OnCancelListener() {
                            public void onCancel(DialogInterface d) { vysledok.cancel(); }
                        })
                        .show();
                return true;
            }
        });
    }

    /** Pustí len webbox.elko.sk; všetko ostatné (tel:, mailto:, cudzie weby) potichu zablokuje. */
    private boolean povolenaAdresa(Uri url) {
        if (url == null) return false;
        String schema = url.getScheme();
        if (schema == null || !(schema.equals("https") || schema.equals("http"))) return false;
        String host = url.getHost();
        if (host == null) return false;
        host = host.toLowerCase();
        return host.equals("elko.sk") || host.endsWith(".elko.sk");
    }

    /** Na prihlasovacej stránke vyplní meno a heslo a odošle formulár. */
    private void prihlasAkTreba(String adresa) {
        if (adresa == null || !adresa.contains(LOGIN_URL)) return;

        String meno = prefy.getString("meno", "");
        String heslo = prefy.getString("heslo", "");
        if (meno.isEmpty() || heslo.isEmpty()) return;
        if (pokusovOPrihlasenie >= MAX_POKUSOV_PRIHLASENIA) return;
        pokusovOPrihlasenie++;

        String js = "(function(){"
                + "var u=document.getElementById('username'),p=document.getElementById('password');"
                + "if(!u||!p)return 0;"
                + "u.value=" + JSONObject.quote(meno) + ";"
                + "p.value=" + JSONObject.quote(heslo) + ";"
                + "u.dispatchEvent(new Event('input',{bubbles:true}));"
                + "p.dispatchEvent(new Event('input',{bubbles:true}));"
                + "var b=document.querySelector(\"button[type='submit'],input[type='submit']\");"
                + "if(b){b.click();return 1}return 0;"
                + "})()";
        web.evaluateJavascript(js, null);
    }

    // ---------- zápis príchodu z oznámenia ----------

    private void spustiPrichod() {
        if (prefy.getString("meno", "").isEmpty()) {
            cakamNaPrichod = true;
            return;
        }
        cakamNaPrichod = true;
        pokusyOPrichod = 0;
        Toast.makeText(this, "Kontrolujem stav v webBoxe…", Toast.LENGTH_SHORT).show();

        String adresa = web.getUrl();
        if (adresa == null || !adresa.contains("elko.sk") || adresa.contains(LOGIN_URL)) {
            web.loadUrl(START_URL);
        } else {
            skusPrichod();
        }
    }

    private void skusPrichod() {
        if (!cakamNaPrichod) return;
        if (pokusyOPrichod >= 10) {
            cakamNaPrichod = false;
            oznam("Nepodarilo sa prečítať stav stránky.\nSkús to ručne v appke.");
            return;
        }
        pokusyOPrichod++;

        web.evaluateJavascript(JS_STAV, new ValueCallback<String>() {
            public void onReceiveValue(String hodnota) {
                String stav = dekoduj(hodnota);
                if (stav.equals("kliknute")) {
                    hlavny.postDelayed(new Runnable() {
                        public void run() { potvrd(3); }
                    }, 700);
                } else if (stav.equals("uz")) {
                    cakamNaPrichod = false;
                    zrusOznam();
                    oznam("Príchod už je zapísaný — nič netreba.");
                } else if (stav.equals("ine") && pokusyOPrichod >= 4) {
                    cakamNaPrichod = false;
                    oznam("Posledná transakcia nie je odchod — nič som nezapísal.");
                } else {
                    // Stránka sa ešte načítava (alebo sa práve zatvorila hláška o kamere).
                    hlavny.postDelayed(new Runnable() {
                        public void run() { skusPrichod(); }
                    }, 1500);
                }
            }
        });
    }

    private void potvrd(final int zostava) {
        web.evaluateJavascript(JS_POTVRD, new ValueCallback<String>() {
            public void onReceiveValue(String hodnota) {
                if (dekoduj(hodnota).equals("ok")) {
                    hlavny.postDelayed(new Runnable() {
                        public void run() { overPrichod(4); }
                    }, 3000);
                } else if (zostava > 0) {
                    hlavny.postDelayed(new Runnable() {
                        public void run() { potvrd(zostava - 1); }
                    }, 800);
                } else {
                    cakamNaPrichod = false;
                    oznam("Potvrdzovacie tlačidlo sa nenašlo.\nSkús to ručne v appke.");
                }
            }
        });
    }

    private void overPrichod(final int zostava) {
        web.evaluateJavascript(JS_TEXTOVA_STAV, new ValueCallback<String>() {
            public void onReceiveValue(String hodnota) {
                String text = dekoduj(hodnota);
                if (text.contains("Príchod")) {
                    cakamNaPrichod = false;
                    zrusOznam();
                    oznam("✅ Príchod zapísaný.\n" + text);
                } else if (zostava > 0) {
                    hlavny.postDelayed(new Runnable() {
                        public void run() { overPrichod(zostava - 1); }
                    }, 2500);
                } else {
                    cakamNaPrichod = false;
                    oznam("Zápis sa nepotvrdil.\nPosledná transakcia: " + text);
                }
            }
        });
    }

    private static String dekoduj(String json) {
        if (json == null || json.equals("null")) return "";
        try {
            Object hodnota = new JSONTokener(json).nextValue();
            return hodnota == null ? "" : hodnota.toString();
        } catch (Exception e) {
            return json.replace("\"", "");
        }
    }

    private void zrusOznam() {
        NotificationManager spravca =
                (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (spravca != null) spravca.cancel(Budik.OZN_DEN);
    }

    private void oznam(String sprava) {
        new AlertDialog.Builder(this)
                .setMessage(sprava)
                .setPositiveButton("OK", null)
                .show();
    }

    // ---------- prihlásenie a menu ----------

    private void vyziadajUdaje() {
        final float hustota = getResources().getDisplayMetrics().density;
        int ods = (int) (20 * hustota);

        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(ods, (int) (8 * hustota), ods, 0);

        final EditText meno = new EditText(this);
        meno.setHint("Meno");
        meno.setSingleLine(true);
        meno.setInputType(InputType.TYPE_CLASS_TEXT);
        meno.setText(prefy.getString("meno", ""));

        final EditText heslo = new EditText(this);
        heslo.setHint("Heslo");
        heslo.setSingleLine(true);
        heslo.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);

        box.addView(meno);
        box.addView(heslo);

        final AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("Prihlásenie do webBox")
                .setView(box)
                .setPositiveButton("Prihlásiť", null)
                .setNegativeButton("Zrušiť", new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface d, int k) {
                        if (prefy.getString("meno", "").isEmpty()) finish();
                    }
                })
                .create();

        dialog.setCancelable(false);
        dialog.setOnShowListener(new DialogInterface.OnShowListener() {
            public void onShow(DialogInterface d) {
                ((AlertDialog) d).getButton(AlertDialog.BUTTON_POSITIVE)
                        .setOnClickListener(new View.OnClickListener() {
                            public void onClick(View v) {
                                String m = meno.getText().toString().trim();
                                String h = heslo.getText().toString();
                                if (m.isEmpty() || h.isEmpty()) {
                                    Toast.makeText(MainActivity.this,
                                            "Vyplň meno aj heslo", Toast.LENGTH_SHORT).show();
                                    return;
                                }
                                prefy.edit().putString("meno", m).putString("heslo", h).apply();
                                pokusovOPrihlasenie = 0;
                                dialog.dismiss();
                                Planovac.naplanuj(MainActivity.this);
                                web.loadUrl(START_URL);
                            }
                        });
            }
        });
        dialog.show();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem polozka) {
        if (polozka.getItemId() == R.id.obnovit) {
            web.reload();
            return true;
        }
        if (polozka.getItemId() == R.id.prihlasenie) {
            pokusovOPrihlasenie = 0;
            CookieManager.getInstance().removeAllCookies(null);
            web.clearCache(true);
            vyziadajUdaje();
            return true;
        }
        return super.onOptionsItemSelected(polozka);
    }

    @Override
    @SuppressWarnings("deprecation")
    public void onBackPressed() {
        if (web != null && web.canGoBack()) {
            web.goBack();
        } else {
            super.onBackPressed();
        }
    }

    @Override
    protected void onDestroy() {
        if (web != null) {
            web.loadUrl("about:blank");
            web.destroy();
        }
        super.onDestroy();
    }
}
