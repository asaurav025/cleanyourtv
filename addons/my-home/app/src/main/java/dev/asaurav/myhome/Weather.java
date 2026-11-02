package dev.asaurav.myhome;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Locale;

/** Open-Meteo's current weather code and today's sunrise and sunset: no account, no key, only the coordinates sent. */
final class Weather {
    static final class Report {
        final int code, sunrise, sunset;

        Report(int code, int sunrise, int sunset) {
            this.code = code;
            this.sunrise = sunrise;
            this.sunset = sunset;
        }
    }

    private Weather() {}

    static String url(double lat, double lon) {
        return String.format(Locale.ROOT, "https://api.open-meteo.com/v1/forecast?latitude=%.3f&longitude=%.3f"
                + "&current=weather_code&daily=sunrise,sunset&timezone=auto&forecast_days=1", lat, lon);
    }

    /** Null when Open-Meteo does not answer or answers something else. */
    static Report parse(String body) {
        try {
            JSONObject o = new JSONObject(body);
            int code = o.getJSONObject("current").getInt("weather_code");
            JSONObject daily = o.optJSONObject("daily");
            int rise = -1, set = -1;
            if (daily != null) {
                rise = Scene.minutes(daily.getJSONArray("sunrise").optString(0, null));
                set = Scene.minutes(daily.getJSONArray("sunset").optString(0, null));
            }
            return new Report(code, rise, set);
        } catch (Exception e) {
            return null;
        }
    }

    static Report fetch(double lat, double lon) {
        HttpURLConnection c = null;
        try {
            c = (HttpURLConnection) new URL(url(lat, lon)).openConnection();
            c.setConnectTimeout(5000);
            c.setReadTimeout(5000);
            if (c.getResponseCode() != 200) return null;
            try (InputStream in = c.getInputStream()) {
                ByteArrayOutputStream b = new ByteArrayOutputStream();
                byte[] buf = new byte[4096];
                int n;
                while ((n = in.read(buf)) > 0 && b.size() < 64 * 1024) b.write(buf, 0, n);
                return parse(b.toString("UTF-8"));
            }
        } catch (Exception e) {
            return null;
        } finally {
            if (c != null) c.disconnect();
        }
    }
}
