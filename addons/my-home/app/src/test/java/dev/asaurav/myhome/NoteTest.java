package dev.asaurav.myhome;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.Socket;
import java.net.URL;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;

public class NoteTest {
    private static final long DAY = 24 * 60 * 60 * 1000L;

    @Rule
    public final TemporaryFolder dir = new TemporaryFolder();

    private Note note;
    private NoteServer server;
    private int port;
    private final AtomicInteger changes = new AtomicInteger();

    private static byte[] png(int w, int h, int extra) {
        ByteBuffer b = ByteBuffer.allocate(24 + extra);
        b.put(new byte[] {(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n', 0, 0, 0, 13, 'I', 'H', 'D', 'R'});
        b.putInt(w).putInt(h);
        return b.array();
    }

    @Before
    public void up() throws IOException {
        note = new Note(dir.getRoot());
        server = new NoteServer(note, "PAGE".getBytes(StandardCharsets.UTF_8), changes::incrementAndGet);
        port = server.start(0);
    }

    @After
    public void down() throws IOException {
        server.stop();
    }

    private HttpURLConnection open(String method, String path) throws IOException {
        HttpURLConnection c = (HttpURLConnection) new URL("http://127.0.0.1:" + port + path).openConnection();
        c.setRequestMethod(method);
        return c;
    }

    private int post(String type, byte[] body) throws IOException {
        HttpURLConnection c = open("POST", "/note");
        c.setDoOutput(true);
        c.setRequestProperty("Content-Type", type);
        c.setFixedLengthStreamingMode(body.length);
        try (OutputStream o = c.getOutputStream()) {
            o.write(body);
        } catch (IOException refused) {
            // a refusal sent before the whole body was read still has a status
        }
        return c.getResponseCode();
    }

    @Test
    public void staysFullUntilTheLastDayThenFades() {
        assertEquals(1f, Note.alpha(0, 15), 0f);
        assertEquals(1f, Note.alpha(14 * DAY, 15), 0f);
        assertEquals(0.5f, Note.alpha(14 * DAY + DAY / 2, 15), 0.001f);
        assertEquals(0f, Note.alpha(15 * DAY, 15), 0f);
    }

    @Test
    public void zeroDaysNeverFadesAndABackwardClockStaysVisible() {
        assertEquals(1f, Note.alpha(1000 * DAY, 0), 0f);
        assertEquals(1f, Note.alpha(-DAY, 15), 0f);
    }

    @Test
    public void anExpiredNoteIsDeleted() throws IOException {
        assertTrue(note.save(png(960, 540, 0)));
        File f = new File(dir.getRoot(), "note.png");
        long sent = f.lastModified();
        assertEquals(sent, note.stamp(sent + 14 * DAY, 15));
        assertEquals(0, note.stamp(sent + 15 * DAY, 15));
        assertFalse(f.exists());
    }

    @Test
    public void onlyPngsOfASensibleSizeCount() {
        assertTrue(Note.validPng(png(960, 540, 0)));
        assertFalse(Note.validPng(png(0, 540, 0)));
        assertFalse(Note.validPng(png(1921, 540, 0)));
        assertFalse(Note.validPng(png(960, 1081, 0)));
        assertFalse(Note.validPng(png(-1, 540, 0)));
        assertFalse(Note.validPng("not a png at all, just some text".getBytes(StandardCharsets.UTF_8)));
        assertFalse(Note.validPng(new byte[4]));
        assertFalse(Note.validPng(null));
    }

    @Test
    public void servesThePage() throws IOException {
        HttpURLConnection c = open("GET", "/");
        assertEquals(200, c.getResponseCode());
        try (InputStream in = c.getInputStream()) {
            assertEquals("PAGE", new String(in.readAllBytes(), StandardCharsets.UTF_8));
        }
    }

    @Test
    public void aQueryStringStillFindsThePage() throws IOException {
        assertEquals(200, open("GET", "/?source=homescreen").getResponseCode());
    }

    @Test
    public void aPostReplacesTheNoteAndADeleteClearsIt() throws IOException {
        byte[] first = png(960, 540, 10), second = png(960, 540, 20);
        assertEquals(204, post("image/png", first));
        assertArrayEquals(first, note.read());
        assertEquals(204, post("image/png", second));
        assertArrayEquals(second, note.read());
        assertEquals(204, open("DELETE", "/note").getResponseCode());
        assertNull(note.read());
        assertEquals(3, changes.get());
    }

    @Test
    public void refusesAnythingElseAndKeepsTheOldNote() throws IOException {
        byte[] kept = png(960, 540, 0);
        assertEquals(204, post("image/png", kept));
        int before = changes.get();
        assertEquals(415, post("text/plain", kept));   // what a page on another site could send without asking
        assertEquals(400, post("image/png", "not a png, and long enough to read".getBytes(StandardCharsets.UTF_8)));
        assertEquals(413, post("image/png", png(960, 540, Note.MAX_BYTES)));
        assertEquals(404, open("GET", "/note").getResponseCode());
        assertEquals(404, open("OPTIONS", "/note").getResponseCode());
        assertArrayEquals(kept, note.read());
        assertEquals(before, changes.get());
    }

    @Test
    public void aMalformedRequestDoesNotStopTheServer() throws IOException {
        try (Socket s = new Socket("127.0.0.1", port)) {
            s.getOutputStream().write("garbage\r\n\r\n".getBytes(StandardCharsets.ISO_8859_1));
            s.getInputStream().read();
        }
        try (Socket s = new Socket("127.0.0.1", port)) {
            s.getOutputStream().write(new byte[] {1, 2, 3});
        }
        assertEquals(200, open("GET", "/").getResponseCode());
    }
}
