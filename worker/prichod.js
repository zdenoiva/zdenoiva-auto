// Cloudflare Worker — tlačidlo z mailu zapíše príchod vo webBox.
//
// Adresa do mailu:  https://<worker>.workers.dev/prichod?k=<SECRET>
//
// Priebeh:
//   1. /prichod  vráti stránku; skener odkazov v mailoch, ktorý nespúšťa
//      JavaScript, teda nič nezapíše.
//   2. Stránka zavolá /zapis, Worker spustí workflow prichod.yml (režim record).
//   3. Stránka sa dopytuje na /stav, kým beh neskončí, a ukáže výsledok.
//
// Potrebné premenné (Settings → Variables and secrets → Secrets):
//   GH_TOKEN  fine-grained PAT, len repo zdenoiva-auto, právo Actions: Read and write
//   SECRET    dlhý náhodný reťazec; bez neho Worker nič neurobí

const REPO = "zdenoiva/zdenoiva-auto";
const WORKFLOW = "prichod.yml";
const BRANCH = "main";

function text(sprava, status = 200) {
  return new Response(sprava, {
    status,
    headers: { "content-type": "text/plain; charset=utf-8", "cache-control": "no-store" },
  });
}

function json(objekt, status = 200) {
  return new Response(JSON.stringify(objekt), {
    status,
    headers: { "content-type": "application/json; charset=utf-8", "cache-control": "no-store" },
  });
}

async function gh(env, cesta, init = {}) {
  return fetch("https://api.github.com" + cesta, {
    ...init,
    headers: {
      authorization: `Bearer ${env.GH_TOKEN}`,
      accept: "application/vnd.github+json",
      "x-github-api-version": "2022-11-28",
      "user-agent": "webbox-prichod",
      "content-type": "application/json",
      ...(init.headers || {}),
    },
  });
}

function stranka() {
  return `<!doctype html>
<html lang="sk"><head><meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<title>Príchod</title>
<style>
  body{margin:0;min-height:100vh;display:flex;align-items:center;justify-content:center;
       font-family:system-ui,-apple-system,Arial,sans-serif;background:#f6f7f8;color:#1a1a1a}
  main{max-width:30rem;padding:2rem 1.5rem;text-align:center}
  .ikona{font-size:3.5rem;line-height:1}
  h1{font-size:1.35rem;margin:1rem 0 .5rem}
  p{margin:.4rem 0;color:#555;font-size:1rem}
  a{color:#0b7a3b}
</style></head>
<body><main>
  <div class="ikona" id="ikona">⏳</div>
  <h1 id="titul">Zapisujem príchod…</h1>
  <p id="popis">Chvíľku strpenie, trvá to do minúty.</p>
  <p id="odkaz"></p>
</main>
<script>
  const k = new URLSearchParams(location.search).get("k");
  const ikona = document.getElementById("ikona");
  const titul = document.getElementById("titul");
  const popis = document.getElementById("popis");
  const odkaz = document.getElementById("odkaz");

  function zlyhalo(text) {
    ikona.textContent = "⚠️";
    titul.textContent = "Nepodarilo sa";
    popis.textContent = text;
  }

  async function skontroluj(od) {
    const r = await fetch("/stav?k=" + encodeURIComponent(k) + "&t=" + od, { cache: "no-store" });
    const d = await r.json();
    if (d.stav === "cakanie") return false;
    if (["queued", "in_progress", "requested", "waiting", "pending"].includes(d.stav)) {
      titul.textContent = "Čakám na výsledok…";
      popis.textContent = "Beh na GitHube sa spustil.";
      return false;
    }
    if (d.vysledok === "success") {
      ikona.textContent = "✅";
      titul.textContent = "Príchod zapísaný";
      popis.textContent = "webBox transakciu potvrdil.";
    } else {
      ikona.textContent = "⚠️";
      titul.textContent = "Nepodarilo sa";
      popis.textContent = "Beh skončil s chybou (" + (d.vysledok || d.stav) + ").";
    }
    if (d.url) odkaz.innerHTML = '<a href="' + d.url + '">detail behu</a>';
    return true;
  }

  (async function () {
    let od;
    try {
      const r = await fetch("/zapis?k=" + encodeURIComponent(k), { method: "POST" });
      const d = await r.json();
      if (!d.ok) return zlyhalo(d.chyba || "GitHub požiadavku odmietol.");
      od = d.t;
    } catch (e) {
      return zlyhalo("Nepodarilo sa spojiť so serverom.");
    }

    let pokusov = 0;
    const casovac = setInterval(async () => {
      pokusov += 1;
      let hotovo = false;
      try { hotovo = await skontroluj(od); } catch (e) { /* skúsime o chvíľu */ }
      if (hotovo || pokusov > 30) {
        clearInterval(casovac);
        if (!hotovo) {
          titul.textContent = "Bez odpovede";
          popis.textContent = "Skontroluj si stav v webBoxe priamo.";
        }
      }
    }, 4000);
  })();
</script>
</body></html>`;
}

async function zapis(env) {
  const od = Date.now();
  const odpoved = await gh(env, `/repos/${REPO}/actions/workflows/${WORKFLOW}/dispatches`, {
    method: "POST",
    body: JSON.stringify({ ref: BRANCH, inputs: { mode: "record" } }),
  });
  if (odpoved.status !== 204) {
    return json({ ok: false, chyba: `GitHub požiadavku odmietol (HTTP ${odpoved.status}).` }, 502);
  }
  return json({ ok: true, t: od });
}

async function stav(env, url) {
  const od = Number(url.searchParams.get("t")) || 0;
  const odpoved = await gh(
    env,
    `/repos/${REPO}/actions/runs?event=workflow_dispatch&branch=${BRANCH}&per_page=20`
  );
  if (odpoved.status !== 200) {
    return json({ stav: "neznámy", chyba: `HTTP ${odpoved.status}` });
  }
  const data = await odpoved.json();
  const beh = (data.workflow_runs || []).find(
    (x) =>
      x.path === `.github/workflows/${WORKFLOW}` &&
      new Date(x.created_at).getTime() >= od - 15000
  );
  if (!beh) return json({ stav: "cakanie" });
  return json({ stav: beh.status, vysledok: beh.conclusion, url: beh.html_url });
}

export default {
  async fetch(request, env) {
    const url = new URL(request.url);
    if (!env.SECRET || url.searchParams.get("k") !== env.SECRET) {
      return text("Neplatný odkaz.", 403);
    }
    if (url.pathname === "/prichod") {
      return new Response(stranka(), {
        headers: { "content-type": "text/html; charset=utf-8", "cache-control": "no-store" },
      });
    }
    if (url.pathname === "/zapis" && request.method === "POST") return zapis(env);
    if (url.pathname === "/stav") return stav(env, url);
    return text("Neznáma adresa.", 404);
  },
};
