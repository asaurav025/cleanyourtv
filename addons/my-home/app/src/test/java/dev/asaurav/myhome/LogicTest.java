package dev.asaurav.myhome;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZonedDateTime;
import java.util.Arrays;
import java.util.Collections;

public class LogicTest {
    private static ZonedDateTime ist(String s) {
        return java.time.LocalDateTime.parse(s).atZone(Market.IST);
    }

    private static final LocalTime FLAT = LocalTime.of(15, 10);

    @Test
    public void phases() {
        assertEquals(Market.Phase.CLOSED, Market.phase(ist("2026-11-02T08:59"), FLAT));
        assertEquals(Market.Phase.PRE, Market.phase(ist("2026-11-02T09:05"), FLAT));
        assertEquals(Market.Phase.OPEN, Market.phase(ist("2026-11-02T09:15"), FLAT));
        assertEquals(Market.Phase.CLOSING, Market.phase(ist("2026-11-02T15:10"), FLAT));
        assertEquals(Market.Phase.CLOSED, Market.phase(ist("2026-11-02T15:30"), FLAT));
        // Saturday, and Diwali-Balipratipada (Tuesday 10 Nov 2026)
        assertEquals(Market.Phase.CLOSED, Market.phase(ist("2026-11-07T11:00"), FLAT));
        assertEquals(Market.Phase.CLOSED, Market.phase(ist("2026-11-10T11:00"), FLAT));
    }

    @Test
    public void phaseUsesIstWhateverTheClockZone() {
        // 04:00 UTC is 09:30 IST
        assertEquals(Market.Phase.OPEN, Market.phase(ZonedDateTime.parse("2026-11-02T04:00Z"), FLAT));
    }

    @Test
    public void nextBellSkipsWeekendAndHoliday() {
        // Friday 6 Nov after the bell -> Monday 9 Nov (8th is a Sunday holiday anyway)
        assertEquals(ist("2026-11-09T09:15"), Market.next(ist("2026-11-06T10:00"), Market.OPEN));
        // Monday 9 Nov after the bell -> Wednesday 11 Nov (10th is a holiday)
        assertEquals(ist("2026-11-11T09:15"), Market.next(ist("2026-11-09T10:00"), Market.OPEN));
        assertEquals(ist("2026-11-02T09:15"), Market.next(ist("2026-11-02T07:00"), Market.OPEN));
    }

    @Test
    public void indianGrouping() {
        assertEquals("24,312", Market.price(24312.4));
        assertEquals("1,23,457", Market.price(123456.6));
        assertEquals("999.50", Market.price(999.5));
        assertEquals("1,000", Market.price(1000));
    }

    @Test
    public void line() {
        String l = Market.line(Arrays.asList(new Market.Quote("NIFTY", 24312, 24162),
                new Market.Quote("BANKNIFTY", 51000, 51051)), Market.Phase.OPEN, FLAT);
        assertEquals("NIFTY 24,312 ▲0.62%  ·  BANKNIFTY 51,000 ▼0.10%", l);
        assertEquals("", Market.line(Collections.emptyList(), Market.Phase.CLOSED, FLAT));
        assertEquals("Opening bell 9:15", Market.line(Collections.emptyList(), Market.Phase.PRE, FLAT));
        assertTrue(Market.line(Collections.emptyList(), Market.Phase.CLOSING, FLAT).endsWith("Flat by 15:10"));
    }

    @Test
    public void parseYahoo() {
        String body = "{\"chart\":{\"result\":[{\"meta\":{\"symbol\":\"^NSEI\",\"regularMarketPrice\":24312.4,"
                + "\"chartPreviousClose\":24100.0,\"previousClose\":24162.0}}],\"error\":null}}";
        Market.Quote q = Market.parseChart(body, "NIFTY");
        assertNotNull(q);
        assertEquals(24162.0, q.prev, 0.001);
        assertNull(Market.parseChart("{\"chart\":{\"result\":null}}", "X"));
        assertNull(Market.parseChart("not json", "X"));
    }

    @Test
    public void ledColours() {
        assertEquals(Scene.WARM_WHITE, Scene.ledColor(0x202020));   // grey has no hue
        assertEquals(Scene.WARM_WHITE, Scene.ledColor(0x000000));
        int netflix = Scene.ledColor(0x5A0A0E);                     // a dark red glow becomes a full red
        assertEquals(0xFF, (netflix >> 16) & 0xFF);
        assertTrue(((netflix >> 8) & 0xFF) < 0x40);
        int blue = Scene.ledColor(0x10306A);
        assertEquals(0xFF, blue & 0xFF);
    }

    @Test
    public void windows() {
        LocalTime from = LocalTime.of(23, 0), to = LocalTime.of(6, 0);
        assertTrue(Scene.inWindow(LocalTime.of(23, 30), from, to));
        assertTrue(Scene.inWindow(LocalTime.of(2, 0), from, to));
        assertFalse(Scene.inWindow(LocalTime.of(6, 0), from, to));
        assertFalse(Scene.inWindow(LocalTime.of(12, 0), from, to));
        assertTrue(Scene.inWindow(LocalTime.of(12, 0), LocalTime.of(9, 0), LocalTime.of(17, 0)));
        assertTrue(Scene.inRange(LocalDate.parse("2026-11-08"), LocalDate.parse("2026-11-06"), LocalDate.parse("2026-11-11")));
        assertFalse(Scene.inRange(LocalDate.parse("2026-11-12"), LocalDate.parse("2026-11-06"), LocalDate.parse("2026-11-11")));
        assertFalse(Scene.inRange(LocalDate.parse("2026-11-08"), null, null));
    }

    @Test
    public void scene() {
        assertEquals("dawn", Scene.phase(6 * 60 + 50, 6 * 60 + 41, 17 * 60 + 58));
        assertEquals("day", Scene.phase(12 * 60, 6 * 60 + 41, 17 * 60 + 58));
        assertEquals("dusk", Scene.phase(18 * 60 + 30, 6 * 60 + 41, 17 * 60 + 58));
        assertEquals("night", Scene.phase(23 * 60, 6 * 60 + 41, 17 * 60 + 58));
        assertEquals("rain", Scene.weather(61));
        assertEquals("storm", Scene.weather(95));
        assertEquals("fog", Scene.weather(45));
        assertEquals("partly", Scene.weather(2));
        assertEquals("clear", Scene.weather(-1));
        assertEquals(6 * 60 + 41, Scene.minutes("2026-11-03T06:41"));
        Weather.Report r = Weather.parse("{\"current\":{\"weather_code\":63},\"daily\":{\"sunrise\":[\"2026-11-03T06:41\"],"
                + "\"sunset\":[\"2026-11-03T17:58\"]}}");
        assertNotNull(r);
        assertEquals(63, r.code);
        assertEquals(17 * 60 + 58, r.sunset);
        assertNull(Weather.parse("{}"));
        Config c = Config.parse("{\"weather\":{\"latitude\":19.07,\"longitude\":72.88}}");
        assertEquals(19.07, c.latitude, 1e-9);
        assertTrue(Double.isNaN(Config.parse("{\"weather\":{\"latitude\":200}}").latitude));
    }

    @Test
    public void bellWindow() {
        assertTrue(Bell.justAfter(LocalTime.of(9, 24), Market.OPEN));
        assertTrue(Bell.justAfter(LocalTime.of(9, 14, 30), Market.OPEN));
        assertFalse(Bell.justAfter(LocalTime.of(9, 30), Market.OPEN));
        assertFalse(Bell.justAfter(LocalTime.of(9, 10), Market.OPEN));
    }

    @Test
    public void colours() {
        assertEquals(0xFFB46B, Scene.parseColor("#FFB46B"));
        assertEquals(0x112233, Scene.parseColor("#FF112233"));
        assertEquals(-1, Scene.parseColor("red"));
        assertEquals(-1, Scene.parseColor(null));
        assertEquals(153, Scene.brightness(60));
        assertEquals(255, Scene.brightness(140));
    }

    @Test
    public void config() {
        Config c = Config.parse("{\"lights\":[{\"name\":\"Fairy\",\"host\":\"192.168.1.60\",\"entity\":\"Fairy Lights\","
                + "\"effects\":[\"Slow Breathe\"]},{\"host\":\"192.168.1.61\",\"entity\":\"MELK Color\",\"kind\":\"rgb\","
                + "\"power\":\"MELK Power\"},{\"entity\":\"no host\"}],\"night\":{\"from\":\"22:30\",\"greeting\":\"Late night\"},"
                + "\"market\":{\"symbols\":[{\"yahoo\":\"^NSEI\",\"label\":\"NIFTY\"},\"RELIANCE.NS\"]},"
                + "\"sync\":{\"brightness\":400}}");
        assertEquals(2, c.lights.size());
        assertEquals(1, c.rgbLights().size());
        assertEquals("MELK Power", c.rgbLights().get(0).power);
        assertEquals(LocalTime.of(22, 30), c.nightFrom);
        assertEquals(LocalTime.of(6, 0), c.nightTo);
        assertEquals("Late night", c.nightGreeting);
        assertEquals(60, c.syncBrightness);                         // out of range keeps the default
        assertEquals("RELIANCE", c.symbols.get(1).label);
        assertEquals(2, Config.parse("{broken").symbols.size());     // a broken file is the defaults
    }

    @Test
    public void espPaths() {
        assertEquals("fairy_lights", Esp.objectId("Fairy Lights"));
        assertEquals("leddmx_pattern_1-210", Esp.objectId("LEDDMX Pattern (1-210)"));
        assertEquals("/light/Fairy%20Lights/turn_on?brightness=10", Esp.path("light", "Fairy Lights", "turn_on", "brightness=10"));
        assertEquals("r=255&g=180&b=107&brightness=153&transition=0", Esp.rgbQuery(0xFFB46B, 153));
    }
}
