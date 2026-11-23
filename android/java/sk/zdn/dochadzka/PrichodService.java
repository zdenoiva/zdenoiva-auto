package sk.zdn.dochadzka;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.location.Location;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.webkit.CookieManager;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import org.json.JSONObject;
import org.json.JSONTokener;

import java.util.Calendar;

/**
 * Ranná služba: zistí polohu. Ak je človek do 70 m od miesta práce, zapíše príchod
 * sám (prihlási sa na pozadí a klikne Príchod). Inak ukáže obyčajné oznámenie.
 */
public class PrichodService extends Service {

    private static final String START_URL = "https://webbox.elko.sk/";
    private static final String LOGIN_URL = "logindispatcher";

    private static final String JS_STAV =
            "(function(){"
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
            + "var d=new Date(),dnes=('0'+d.getDate()).slice(-2)+'.'+('0'+(d.getMonth()+1)).slice(-2)+'.'+d.getFullYear();"
            + "if(s.indexOf('Príchod')>=0 && s.indexOf(dnes)>=0)return 'uz';"
            + "if(s.indexOf('Odchod')<0 && s.indexOf('Príchod')<0)return 'ine';"
            + "var a=document.querySelectorAll('button.rdr-make-transaction'),b=null,i,l;"
            + "for(i=0;i<a.length;i++){l=a[i].getAttribute('data-label')||a[i].innerText||'';"
            + "if(l.indexOf('Príchod')>=0){b=a[i];break;}}"
            + "if(!b)return 'netlacidlo';"
            + "b.click();return 'kliknute';"
            + "})()";

    private static final String JS_POTVRD =
            "(function(){var b=document.getElementById('rdr-confirm-btn');"
            + "if(!b)return 'nie';b.click();return 'ok';})()";

    private static final String JS_TEXT =
            "(function(){var t=document.getElementById('rdr-last-transaction');"
            + "return t?(t.innerText||'').replace(/\\s+/g,' '):'';})()";

    private final Handler hlavny = new Handler(Looper.getMainLooper());
    private WebView web;
    private boolean cakam = false;
    private boolean hotovo = false;
    private int pokusov = 0;

    @Override
    public IBinder onBind(Intent zamer) {
        return null;
    }

    @Override
    public int onStartCommand(Intent zamer, int flags, int startId) {
        startForeground(Oznamy.OZN_SLUZBA, Oznamy.sluzba(this, "Zisťujem polohu…"));

        new Thread(new Runnable() {
            public void run() {
                final Location poloha = Poloha.zisti(PrichodService.this, 20000);
                final boolean priPraci = poloha != null
                        && Poloha.maMiesto(PrichodService.this)
                        && Poloha.vzdialenost(poloha.getLatitude(), poloha.getLongitude(),
                        Poloha.lat(PrichodService.this), Poloha.lon(PrichodService.this))
                        <= Poloha.OKRUH_METROV;

                hlavny.post(new Runnable() {
                    public void run() {
                        if (priPraci) {
                            automaticky();
                        } else {
                            Oznamy.siVPraci(PrichodService.this);
                            skonci();
                        }
                    }
                });
            }
        }).start();
        return START_NOT_STICKY;
    }

    private void automaticky() {
        SharedPreferences p = getSharedPreferences("dochadzka", MODE_PRIVATE);
        final String meno = p.getString("meno", "");
        final String heslo = p.getString("heslo", "");
        if (meno.isEmpty() || heslo.isEmpty()) {
            Oznamy.siVPraci(this);
            skonci();
            return;
        }

        web = new WebView(getApplicationContext());
        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        CookieManager.getInstance().setAcceptCookie(true);

        web.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageFinished(WebView v, String adresa) {
                if (hotovo) return;
                if (adresa != null && adresa.contains(LOGIN_URL)) {
                    prihlas(meno, heslo);
                } else if (cakam) {
                    skusStav();
                }
            }
        });

        cakam = true;
        web.loadUrl(START_URL);

        // poistka: keď sa do dvoch minút nič nezapíše, ponúkneme obyčajné oznámenie
        hlavny.postDelayed(new Runnable() {
            public void run() {
                if (!hotovo) padlo();
            }
        }, 120000);
    }

    private void prihlas(String meno, String heslo) {
        String js = "(function(){"
                + "var u=document.getElementById('username'),p=document.getElementById('password');"
                + "if(!u||!p)return 0;"
                + "var r=document.querySelector(\"input[name='_loginto'][value='webreader']\");"
                + "if(r&&!r.checked){r.checked=true;r.dispatchEvent(new Event('change',{bubbles:true}));}"
                + "u.value=" + JSONObject.quote(meno) + ";"
                + "p.value=" + JSONObject.quote(heslo) + ";"
                + "u.dispatchEvent(new Event('input',{bubbles:true}));"
                + "p.dispatchEvent(new Event('input',{bubbles:true}));"
                + "var b=document.querySelector(\"button[type='submit'],input[type='submit']\");"
                + "if(b){b.click();return 1}return 0;"
                + "})()";
        web.evaluateJavascript(js, null);
    }

    private void skusStav() {
        if (hotovo) return;
        if (pokusov++ > 25) {
            padlo();
            return;
        }
        web.evaluateJavascript(JS_STAV, new android.webkit.ValueCallback<String>() {
            public void onReceiveValue(String hodnota) {
                String stav = dekoduj(hodnota);
                if ("kliknute".equals(stav)) {
                    hlavny.postDelayed(new Runnable() {
                        public void run() {
                            potvrd(3);
                        }
                    }, 900);
                } else if ("uz".equals(stav)) {
                    uspech("Príchod už bol zapísaný.");
                } else if ("ine".equals(stav) && pokusov > 8) {
                    padlo();
                } else {
                    hlavny.postDelayed(new Runnable() {
                        public void run() {
                            skusStav();
                        }
                    }, 2500);
                }
            }
        });
    }

    private void potvrd(final int zostava) {
        web.evaluateJavascript(JS_POTVRD, new android.webkit.ValueCallback<String>() {
            public void onReceiveValue(String hodnota) {
                if ("ok".equals(dekoduj(hodnota))) {
                    hlavny.postDelayed(new Runnable() {
                        public void run() {
                            over(5);
                        }
                    }, 3000);
                } else if (zostava > 0) {
                    hlavny.postDelayed(new Runnable() {
                        public void run() {
                            potvrd(zostava - 1);
                        }
                    }, 900);
                } else {
                    padlo();
                }
            }
        });
    }

    private void over(final int zostava) {
        web.evaluateJavascript(JS_TEXT, new android.webkit.ValueCallback<String>() {
            public void onReceiveValue(String hodnota) {
                String text = dekoduj(hodnota);
                if (text.contains("Príchod")) {
                    uspech(text);
                } else if (zostava > 0) {
                    hlavny.postDelayed(new Runnable() {
                        public void run() {
                            over(zostava - 1);
                        }
                    }, 2500);
                } else {
                    padlo();
                }
            }
        });
    }

    private void uspech(String detail) {
        if (hotovo) return;
        hotovo = true;
        Calendar teraz = Calendar.getInstance();
        String cas = String.format("%02d:%02d", teraz.get(Calendar.HOUR_OF_DAY),
                teraz.get(Calendar.MINUTE));
        Oznamy.sprava(this, "✅ Príchod do práce zapísaný",
                "webBox potvrdil príchod o " + cas + ".");
        skonci();
    }

    private void padlo() {
        if (hotovo) return;
        hotovo = true;
        Oznamy.siVPraci(this);
        skonci();
    }

    private void skonci() {
        if (web != null) {
            web.destroy();
            web = null;
        }
        stopForeground(true);
        stopSelf();
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
}
