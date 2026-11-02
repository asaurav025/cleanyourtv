package dev.asaurav.myhome;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.List;

/** Free, delayed Yahoo quotes, one symbol at a time, the way the trader skills fetch them. Blocking: off the main thread. */
final class Quotes {
    private static final String UA = "Mozilla/5.0 (compatible; my-home-tv/1.0)";
    private static final String[] HOSTS = {"query2", "query1"};

    private Quotes() {}

    static List<Market.Quote> fetch(List<Config.Symbol> symbols) {
        List<Market.Quote> out = new ArrayList<>();
        for (Config.Symbol s : symbols) {
            Market.Quote q = one(s);
            if (q != null) out.add(q);
        }
        return out;
    }

    private static Market.Quote one(Config.Symbol s) {
        for (String host : HOSTS) {
            HttpURLConnection c = null;
            try {
                String sym = URLEncoder.encode(s.yahoo, "UTF-8");
                c = (HttpURLConnection) new URL("https://" + host + ".finance.yahoo.com/v8/finance/chart/" + sym
                        + "?range=1d&interval=1d").openConnection();
                c.setConnectTimeout(4000);
                c.setReadTimeout(4000);
                c.setRequestProperty("User-Agent", UA);
                c.setRequestProperty("Accept", "application/json");
                if (c.getResponseCode() != 200) continue;
                try (InputStream in = c.getInputStream()) {
                    ByteArrayOutputStream b = new ByteArrayOutputStream();
                    byte[] buf = new byte[4096];
                    int n;
                    while ((n = in.read(buf)) > 0 && b.size() < 256 * 1024) b.write(buf, 0, n);
                    Market.Quote q = Market.parseChart(b.toString("UTF-8"), s.label);
                    if (q != null) return q;
                }
            } catch (Exception ignored) {
                // next host
            } finally {
                if (c != null) c.disconnect();
            }
        }
        return null;
    }
}
