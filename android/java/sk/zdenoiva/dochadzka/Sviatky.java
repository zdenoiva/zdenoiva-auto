package sk.zdenoiva.dochadzka;

import java.util.Calendar;

/** Slovenské štátne sviatky a dni pracovného pokoja (zákon 241/1993). */
final class Sviatky {

    private Sviatky() {
    }

    static boolean jeSviatok(Calendar den) {
        int d = den.get(Calendar.DAY_OF_MONTH);
        int m = den.get(Calendar.MONTH) + 1;
        int rok = den.get(Calendar.YEAR);

        // pevné sviatky
        if (m == 1 && d == 1) return true;    // Deň vzniku SR
        if (m == 1 && d == 6) return true;    // Zjavenie Pána
        if (m == 5 && d == 1) return true;    // Sviatok práce
        if (m == 5 && d == 8) return true;    // Deň víťazstva nad fašizmom
        if (m == 7 && d == 5) return true;    // Sv. Cyril a Metod
        if (m == 8 && d == 29) return true;   // Výročie SNP
        if (m == 9 && d == 1) return true;    // Deň Ústavy SR
        if (m == 9 && d == 15) return true;   // Sedembolestná Panna Mária
        if (m == 11 && d == 1) return true;   // Sviatok všetkých svätých
        if (m == 11 && d == 17) return true;  // Deň boja za slobodu a demokraciu
        if (m == 12 && d == 24) return true;  // Štedrý deň
        if (m == 12 && d == 25) return true;  // Prvý sviatok vianočný
        if (m == 12 && d == 26) return true;  // Druhý sviatok vianočný

        // pohyblivé: Veľký piatok a Veľkonočný pondelok
        int[] velkaNoc = velkaNoc(rok);
        Calendar nedeľa = Calendar.getInstance();
        nedeľa.clear();
        nedeľa.set(rok, velkaNoc[0] - 1, velkaNoc[1]);

        Calendar piatok = (Calendar) nedeľa.clone();
        piatok.add(Calendar.DAY_OF_MONTH, -2);
        if (rovnakyDen(den, piatok)) return true;

        Calendar pondelok = (Calendar) nedeľa.clone();
        pondelok.add(Calendar.DAY_OF_MONTH, 1);
        return rovnakyDen(den, pondelok);
    }

    private static boolean rovnakyDen(Calendar a, Calendar b) {
        return a.get(Calendar.YEAR) == b.get(Calendar.YEAR)
                && a.get(Calendar.MONTH) == b.get(Calendar.MONTH)
                && a.get(Calendar.DAY_OF_MONTH) == b.get(Calendar.DAY_OF_MONTH);
    }

    /** Veľkonočná nedeľa (gregoriánsky kalendár) ako {mesiac, deň}. */
    static int[] velkaNoc(int rok) {
        int a = rok % 19;
        int b = rok / 100;
        int c = rok % 100;
        int d = b / 4;
        int e = b % 4;
        int f = (b + 8) / 25;
        int g = (b - f + 1) / 3;
        int h = (19 * a + b - d - g + 15) % 30;
        int i = c / 4;
        int k = c % 4;
        int l = (32 + 2 * e + 2 * i - h - k) % 7;
        int m = (a + 11 * h + 22 * l) / 451;
        int mesiac = (h + l - 7 * m + 114) / 31;
        int den = ((h + l - 7 * m + 114) % 31) + 1;
        return new int[]{mesiac, den};
    }
}
