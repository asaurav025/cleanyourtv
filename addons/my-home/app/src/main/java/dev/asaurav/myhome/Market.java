package dev.asaurav.myhome;

import org.json.JSONArray;
import org.json.JSONObject;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * The NSE cash-market clock and the one line Home shows under its clock. Pure Java, no Android, so it is unit tested.
 * Same rules as the trader skills' trader_common/clock.py: 09:15 to 15:30 IST, the 2026 CM holidays, flat by 15:10.
 */
final class Market {
    static final ZoneId IST = ZoneId.of("Asia/Kolkata");
    static final LocalTime PRE_OPEN = LocalTime.of(9, 0);
    static final LocalTime OPEN = LocalTime.of(9, 15);
    static final LocalTime CLOSE = LocalTime.of(15, 30);

    enum Phase { CLOSED, PRE, OPEN, CLOSING }

    /** NSE capital-market trading holidays, calendar 2026 (copied from trader_common/clock.py). */
    static final Set<LocalDate> HOLIDAYS = new HashSet<>();
    static {
        for (String d : new String[] {
                "2026-01-15", "2026-01-26", "2026-02-15", "2026-03-03", "2026-03-21", "2026-03-26", "2026-03-31",
                "2026-04-03", "2026-04-14", "2026-05-01", "2026-05-28", "2026-06-26", "2026-08-15", "2026-09-14",
                "2026-10-02", "2026-10-20", "2026-11-08", "2026-11-10", "2026-11-24", "2026-12-25"}) {
            HOLIDAYS.add(LocalDate.parse(d));
        }
    }

    private Market() {}

    static boolean tradingDay(LocalDate d) {
        DayOfWeek w = d.getDayOfWeek();
        return w != DayOfWeek.SATURDAY && w != DayOfWeek.SUNDAY && !HOLIDAYS.contains(d);
    }

    static Phase phase(ZonedDateTime now, LocalTime flatBy) {
        ZonedDateTime ist = now.withZoneSameInstant(IST);
        if (!tradingDay(ist.toLocalDate())) return Phase.CLOSED;
        LocalTime t = ist.toLocalTime();
        if (t.isBefore(PRE_OPEN) || !t.isBefore(CLOSE)) return Phase.CLOSED;
        if (t.isBefore(OPEN)) return Phase.PRE;
        if (t.isBefore(flatBy)) return Phase.OPEN;
        return Phase.CLOSING;
    }

    /** The next time {@code at} IST on a trading day, strictly after now. */
    static ZonedDateTime next(ZonedDateTime now, LocalTime at) {
        ZonedDateTime ist = now.withZoneSameInstant(IST);
        LocalDate d = ist.toLocalDate();
        if (!ist.toLocalTime().isBefore(at)) d = d.plusDays(1);
        for (int i = 0; i < 30 && !tradingDay(d); i++) d = d.plusDays(1);
        return d.atTime(at).atZone(IST);
    }

    static final class Quote {
        final String label;
        final double price;
        final double prev;

        Quote(String label, double price, double prev) {
            this.label = label;
            this.price = price;
            this.prev = prev;
        }

        double changePct() {
            return prev > 0 ? (price - prev) / prev * 100.0 : 0.0;
        }
    }

    /** One quote from Yahoo's /v8/finance/chart answer, or null when it has no price. */
    static Quote parseChart(String body, String label) {
        try {
            JSONArray result = new JSONObject(body).getJSONObject("chart").getJSONArray("result");
            JSONObject meta = result.getJSONObject(0).getJSONObject("meta");
            double price = meta.optDouble("regularMarketPrice", Double.NaN);
            double prev = meta.optDouble("previousClose", Double.NaN);
            if (Double.isNaN(prev)) prev = meta.optDouble("chartPreviousClose", Double.NaN);
            if (Double.isNaN(price) || Double.isNaN(prev)) return null;
            return new Quote(label, price, prev);
        } catch (Exception e) {
            return null;
        }
    }

    /** Indian digit grouping (24,312 and 1,23,456), no decimals at 1,000 and over, two below. */
    static String price(double v) {
        DecimalFormatSymbols sym = DecimalFormatSymbols.getInstance(Locale.ENGLISH);
        if (v < 1000) return new DecimalFormat("0.00", sym).format(v);
        long whole = Math.round(v);
        String s = Long.toString(whole);
        if (s.length() <= 3) return s;
        String last3 = s.substring(s.length() - 3);
        String rest = s.substring(0, s.length() - 3);
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < rest.length(); i++) {
            if (i > 0 && (rest.length() - i) % 2 == 0) out.append(',');
            out.append(rest.charAt(i));
        }
        return out.append(',').append(last3).toString();
    }

    /**
     * "NIFTY 24,312 ▲0.62%  ·  BANKNIFTY 51,020 ▼0.10%", with "Opening bell 9:15" before the open and
     * "Flat by 15:10" from flat-by to the close. Empty when the market is closed. The launcher colours ▲ and ▼.
     */
    static String line(List<Quote> quotes, Phase phase, LocalTime flatBy) {
        if (phase == Phase.CLOSED) return "";
        StringBuilder b = new StringBuilder();
        if (phase == Phase.PRE) b.append("Opening bell 9:15");
        for (Quote q : quotes) {
            if (b.length() > 0) b.append("  ·  ");
            double pct = q.changePct();
            b.append(q.label).append(' ').append(price(q.price)).append(' ')
                    .append(pct >= 0 ? '▲' : '▼')
                    .append(String.format(Locale.ENGLISH, "%.2f%%", Math.abs(pct)));
        }
        if (phase == Phase.CLOSING) {
            if (b.length() > 0) b.append("  ·  ");
            b.append(String.format(Locale.ENGLISH, "Flat by %d:%02d", flatBy.getHour(), flatBy.getMinute()));
        }
        return b.toString();
    }
}
