package sk.zdenoiva.dochadzka;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.net.Uri;
import android.os.Bundle;
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

/**
 * Dochádzka — prihlásenie do webBox (ELKO) a zobrazenie virtuálnej čítačky.
 *
 * Appka nemá žiadne povolenia okrem internetu. WebView dostane všetky požiadavky
 * na kameru, mikrofón, GPS aj výber súborov rovno zamietnuté a nikdy sa na ne
 * nepýta — žiadne systémové okná, žiadne hlášky.
 */
public class MainActivity extends Activity {

    private static final String START_URL = "https://webbox.elko.sk/";
    private static final String LOGIN_URL = "logindispatcher";
    private static final int MAX_POKUSOV = 3;

    private SharedPreferences prefy;
    private WebView web;
    private int pokusovOPrihlasenie = 0;

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

        if (prefy.getString("meno", "").isEmpty()) {
            vyziadajUdaje();
        } else {
            web.loadUrl(START_URL);
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

            // Potvrdenia samotného webu (napr. "Zapísať odchod?") majú fungovať.
            @Override
            public boolean onJsAlert(WebView v, String url, String sprava, final JsResult vysledok) {
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
        if (pokusovOPrihlasenie >= MAX_POKUSOV) return;
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
