package sk.zdn.dochadzka;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.net.Uri;
import android.os.Bundle;
import android.view.ViewGroup;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;
import android.widget.Toast;

/** Výber miesta práce na mape (OpenStreetMap). */
public class Mapa extends Activity {

    private WebView web;

    @SuppressLint("SetJavaScriptEnabled")
    @Override
    protected void onCreate(Bundle stav) {
        super.onCreate(stav);

        FrameLayout koren = new FrameLayout(this);
        web = new WebView(this);
        koren.addView(web, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        setContentView(koren);

        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setAllowFileAccess(true);
        s.setAllowContentAccess(true);
        s.setUseWideViewPort(true);
        s.setLoadWithOverviewMode(true);

        web.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView v, WebResourceRequest poziadavka) {
                return spracuj(poziadavka.getUrl());
            }

            @Override
            @SuppressWarnings("deprecation")
            public boolean shouldOverrideUrlLoading(WebView v, String adresa) {
                return spracuj(Uri.parse(adresa));
            }

            @Override
            public void onPageFinished(WebView v, String adresa) {
                if (Poloha.maMiesto(Mapa.this)) {
                    web.evaluateJavascript("nastavBod(" + Poloha.lat(Mapa.this) + ","
                            + Poloha.lon(Mapa.this) + ",16)", null);
                }
            }
        });

        web.loadUrl("file:///android_asset/mapa.html");
    }

    private boolean spracuj(Uri adresa) {
        if (adresa == null || !"vyber".equals(adresa.getScheme())) return false;
        try {
            double lat = Double.parseDouble(adresa.getQueryParameter("lat"));
            double lon = Double.parseDouble(adresa.getQueryParameter("lon"));
            Poloha.uloz(this, lat, lon);
            Toast.makeText(this, "Miesto práce uložené", Toast.LENGTH_SHORT).show();
            finish();
        } catch (Exception e) {
            Toast.makeText(this, "Súradnice sa nepodarilo prečítať", Toast.LENGTH_SHORT).show();
        }
        return true;
    }
}
