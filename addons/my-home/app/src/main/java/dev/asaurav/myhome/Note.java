package dev.asaurav.myhome;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;

/**
 * The one scribble on Home, a PNG in the app's private files. It stays until it is replaced, cleared or its fade
 * runs out; the file's time is when it was sent, so a reboot does not restart the fade.
 */
final class Note {
    static final int MAX_BYTES = 300 * 1024;   // also has to fit a Binder call to the launcher
    private static final int MAX_WIDTH = 1920, MAX_HEIGHT = 1080;
    private static final long DAY_MS = 24 * 60 * 60 * 1000L;
    private static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n', 0, 0, 0, 13, 'I', 'H', 'D', 'R'};

    private final File file, tmp;

    Note(File dir) {
        file = new File(dir, "note.png");
        tmp = new File(dir, "note.tmp");
    }

    /** False when it is not a PNG of a sensible size; the old note then stays. */
    synchronized boolean save(byte[] png) throws IOException {
        if (png == null || png.length > MAX_BYTES || !validPng(png)) return false;
        Files.write(tmp.toPath(), png);
        Files.move(tmp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING);
        return true;
    }

    synchronized void clear() {
        file.delete();
    }

    /** When the note was sent, or 0 when there is none. A note whose fade has run out is deleted here. */
    synchronized long stamp(long now, int fadeDays) {
        if (!file.isFile()) return 0;
        long at = file.lastModified();
        if (alpha(now - at, fadeDays) <= 0f) {
            file.delete();
            return 0;
        }
        return at;
    }

    synchronized byte[] read() {
        try {
            return file.isFile() ? Files.readAllBytes(file.toPath()) : null;
        } catch (IOException e) {
            return null;
        }
    }

    /** Fully visible until the last day of {@code fadeDays}, then down to nothing. 0 days or fewer never fades. */
    static float alpha(long ageMs, int fadeDays) {
        if (fadeDays <= 0 || ageMs < 0) return 1f;
        long left = fadeDays * DAY_MS - ageMs;
        if (left <= 0) return 0f;
        return left >= DAY_MS ? 1f : (float) left / DAY_MS;
    }

    /** The signature and the IHDR size, read by hand: this comes from anyone on the network. */
    static boolean validPng(byte[] b) {
        if (b == null || b.length < 24) return false;
        for (int i = 0; i < PNG.length; i++) if (b[i] != PNG[i]) return false;
        long w = int32(b, 16), h = int32(b, 20);
        return w >= 1 && w <= MAX_WIDTH && h >= 1 && h <= MAX_HEIGHT;
    }

    private static long int32(byte[] b, int at) {
        return (b[at] & 0xFFL) << 24 | (b[at + 1] & 0xFFL) << 16 | (b[at + 2] & 0xFFL) << 8 | (b[at + 3] & 0xFFL);
    }
}
