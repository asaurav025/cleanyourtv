package dev.asaurav.myhome;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.Locale;

/**
 * ESPHome's web_server REST API (version 3) on the home network: GET /light/<name> for the state, POST
 * /light/<name>/turn_on?... to change it. URLs use the entity's name; firmware from before name URLs answers 404 to
 * those, so each call falls back to the old object_id form once. Every call is short and blocking: run off the main
 * thread.
 */
final class Esp {
    private static final int TIMEOUT_MS = 2000;

    private Esp() {}

    /** ESPHome's object_id: lower case, spaces to underscores, anything but [a-z0-9_-] dropped. */
    static String objectId(String name) {
        StringBuilder b = new StringBuilder();
        for (char ch : name.toLowerCase(Locale.ROOT).toCharArray()) {
            if (ch == ' ') b.append('_');
            else if ((ch >= 'a' && ch <= 'z') || (ch >= '0' && ch <= '9') || ch == '_' || ch == '-') b.append(ch);
        }
        return b.toString();
    }

    static String path(String domain, String id, String action, String query) {
        String enc;
        try {
            enc = URLEncoder.encode(id, "UTF-8").replace("+", "%20");
        } catch (Exception e) {
            enc = id;
        }
        return "/" + domain + "/" + enc + (action == null ? "" : "/" + action) + (query == null || query.isEmpty() ? "" : "?" + query);
    }

    /** The entity's state JSON, or null when the device does not answer. */
    static JSONObject get(String host, String domain, String name) {
        String body = request(host, "GET", path(domain, name, null, null));
        if (body == null) body = request(host, "GET", path(domain, objectId(name), null, null));
        try {
            return body == null ? null : new JSONObject(body);
        } catch (Exception e) {
            return null;
        }
    }

    static boolean post(String host, String domain, String name, String action, String query) {
        if (request(host, "POST", path(domain, name, action, query)) != null) return true;
        return request(host, "POST", path(domain, objectId(name), action, query)) != null;
    }

    static boolean isOn(String host, String domain, String name) {
        JSONObject s = get(host, domain, name);
        return s != null && ("ON".equalsIgnoreCase(s.optString("state")) || s.optBoolean("value", false));
    }

    /** "r=255&g=180&b=107&brightness=153&transition=0" */
    static String rgbQuery(int rgb, int brightness255) {
        return "r=" + ((rgb >> 16) & 0xFF) + "&g=" + ((rgb >> 8) & 0xFF) + "&b=" + (rgb & 0xFF)
                + "&brightness=" + brightness255 + "&transition=0";
    }

    /** The body on a 2xx answer, otherwise null. */
    private static String request(String host, String method, String path) {
        HttpURLConnection c = null;
        try {
            c = (HttpURLConnection) new URL("http://" + host + path).openConnection();
            c.setRequestMethod(method);
            c.setConnectTimeout(TIMEOUT_MS);
            c.setReadTimeout(TIMEOUT_MS);
            c.setUseCaches(false);
            if ("POST".equals(method)) {
                c.setDoOutput(true);
                c.setFixedLengthStreamingMode(0);
                c.getOutputStream().close();
            }
            int code = c.getResponseCode();
            if (code < 200 || code >= 300) return null;
            try (InputStream in = c.getInputStream()) {
                ByteArrayOutputStream out = new ByteArrayOutputStream();
                byte[] buf = new byte[2048];
                int n;
                while ((n = in.read(buf)) > 0 && out.size() < 64 * 1024) out.write(buf, 0, n);
                return out.toString("UTF-8");
            }
        } catch (Exception e) {
            return null;
        } finally {
            if (c != null) c.disconnect();
        }
    }
}
