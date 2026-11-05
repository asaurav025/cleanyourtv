package dev.asaurav.myhome;

import java.io.ByteArrayOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

/**
 * The phone's page, on the home network with no login: GET / is the page, POST /note (image/png) replaces the note,
 * DELETE /note clears it. One request at a time, each bounded in size and time. POST insists on image/png and nothing
 * answers OPTIONS, so a web page on some other site cannot send a note from a browser in the house.
 */
final class NoteServer {
    static final int PORT = 8765;
    private static final int MAX_HEADER = 8 * 1024;
    private static final int TIMEOUT_MS = 5000;

    private final Note note;
    private final byte[] page;
    private final Runnable changed;
    private ServerSocket server;

    NoteServer(Note note, byte[] page, Runnable changed) {
        this.note = note;
        this.page = page;
        this.changed = changed;
    }

    /** Binds now, so the caller knows; port 0 takes any free port (the tests). */
    int start(int port) throws IOException {
        server = new ServerSocket(port);
        Thread t = new Thread(this::loop, "note-server");
        t.setDaemon(true);
        t.start();
        return server.getLocalPort();
    }

    void stop() throws IOException {
        server.close();
    }

    private void loop() {
        while (!server.isClosed()) {
            try (Socket s = server.accept()) {
                s.setSoTimeout(TIMEOUT_MS);
                handle(s.getInputStream(), s.getOutputStream());
            } catch (IOException e) {
                // a client that went away or stalled; the next one is unaffected
            }
        }
    }

    private void handle(InputStream in, OutputStream out) throws IOException {
        String head = readHead(in);
        if (head == null) {
            respond(out, "431 Too Large", "text/plain", new byte[0]);
            return;
        }
        String[] lines = head.split("\r\n");
        String[] request = lines[0].split(" ");
        if (request.length < 2) {
            respond(out, "400 Bad Request", "text/plain", new byte[0]);
            return;
        }
        String method = request[0], path = request[1];
        int query = path.indexOf('?');
        if (query >= 0) path = path.substring(0, query);
        if ("GET".equals(method) && "/".equals(path)) {
            respond(out, "200 OK", "text/html; charset=utf-8", page);
        } else if ("DELETE".equals(method) && "/note".equals(path)) {
            note.clear();
            changed.run();
            respond(out, "204 No Content", "text/plain", new byte[0]);
        } else if ("POST".equals(method) && "/note".equals(path)) {
            post(lines, in, out);
        } else {
            respond(out, "404 Not Found", "text/plain", new byte[0]);
        }
    }

    private void post(String[] lines, InputStream in, OutputStream out) throws IOException {
        long length = -1;
        String type = "";
        for (String line : lines) {
            int colon = line.indexOf(':');
            if (colon < 0) continue;
            String key = line.substring(0, colon).trim().toLowerCase(Locale.ROOT);
            String value = line.substring(colon + 1).trim();
            if ("content-type".equals(key)) type = value.toLowerCase(Locale.ROOT);
            if ("content-length".equals(key)) {
                try {
                    length = Long.parseLong(value);
                } catch (NumberFormatException e) {
                    length = -1;
                }
            }
        }
        if (!type.startsWith("image/png")) {
            respond(out, "415 Unsupported Media Type", "text/plain", new byte[0]);
        } else if (length < 0) {
            respond(out, "411 Length Required", "text/plain", new byte[0]);
        } else if (length > Note.MAX_BYTES) {
            respond(out, "413 Too Large", "text/plain", new byte[0]);
        } else if (!note.save(readBody(in, (int) length))) {
            respond(out, "400 Bad Request", "text/plain", new byte[0]);
        } else {
            changed.run();
            respond(out, "204 No Content", "text/plain", new byte[0]);
        }
    }

    /** Up to and without the blank line, or null when the header is too long. */
    private static String readHead(InputStream in) throws IOException {
        ByteArrayOutputStream b = new ByteArrayOutputStream();
        int state = 0;
        while (state < 4) {
            int c = in.read();
            if (c < 0) throw new EOFException();
            if (b.size() >= MAX_HEADER) return null;
            b.write(c);
            state = (c == '\r' && state % 2 == 0) || (c == '\n' && state % 2 == 1) ? state + 1 : (c == '\r' ? 1 : 0);
        }
        return b.toString(StandardCharsets.ISO_8859_1.name());
    }

    private static byte[] readBody(InputStream in, int length) throws IOException {
        byte[] body = new byte[length];
        int at = 0;
        while (at < length) {
            int n = in.read(body, at, length - at);
            if (n < 0) throw new EOFException();
            at += n;
        }
        return body;
    }

    private static void respond(OutputStream out, String status, String type, byte[] body) throws IOException {
        String head = "HTTP/1.1 " + status + "\r\nContent-Type: " + type + "\r\nContent-Length: " + body.length
                + "\r\nCache-Control: no-store\r\nConnection: close\r\n\r\n";
        out.write(head.getBytes(StandardCharsets.ISO_8859_1));
        out.write(body);
        out.flush();
    }
}
