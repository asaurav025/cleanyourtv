package dev.asaurav.myhome;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.net.Uri;
import android.os.Binder;
import android.os.Bundle;
import android.os.Process;
import android.util.Log;

import java.io.IOException;
import java.io.InputStream;

/**
 * What the Clean Your TV launcher talks to (theme key paired_app). Only call(); no data, no files:
 *   focus  "#RRGGBB"   the focused tile's glow colour
 *   launch "<package>" Home opened an app
 *   home               Home came back to the front
 *   state              -> {"line": the market strip, "greeting": an override or "", "scene": "dusk/rain",
 *                          "note_stamp": when the scribble was sent or 0, "note_alpha": how far it has faded}
 *   note               -> {"png": the scribble}
 * Only the launcher and the ADB shell (for testing with `content call`) may call it, so no other app can drive the
 * room's lights.
 */
public final class HomeProvider extends ContentProvider {
    static final String LAUNCHER = "com.gathin.tvhome";
    /** android.os.Process.SHELL_UID, which the SDK hides. */
    private static final int SHELL_UID = 2000;

    private static final Uri NOTE_URI = Uri.parse("content://dev.asaurav.myhome.home/note");

    private Note note;

    @Override
    public boolean onCreate() {
        note = new Note(getContext().getFilesDir());
        Thread t = new Thread(this::startNoteServer, "note-start");
        t.setDaemon(true);
        t.start();
        NoteService.start(getContext());
        return true;
    }

    /** Lives as long as this process does: Home's first call starts the process, and with it the phone's page. */
    private void startNoteServer() {
        try (InputStream in = getContext().getAssets().open("note.html")) {
            new NoteServer(note, in.readAllBytes(),
                    () -> getContext().getContentResolver().notifyChange(NOTE_URI, null)).start(NoteServer.PORT);
        } catch (IOException e) {
            Log.w("MyHome", "note server did not start", e);
        }
    }

    @Override
    public Bundle call(String method, String arg, Bundle extras) {
        if (!allowed()) return null;
        Room room = Room.get(getContext());
        Bundle out = new Bundle();
        switch (method) {
            case "focus": {
                int rgb = Scene.parseColor(arg);
                if (rgb >= 0) room.focus(rgb);
                break;
            }
            case "launch":
                room.launched(arg);
                break;
            case "home":
                NoteService.start(getContext());
                room.home();
                break;
            case "state":
                out.putString("line", room.line());
                out.putString("greeting", room.greeting());
                out.putString("scene", room.scene());
                long now = System.currentTimeMillis();
                int fadeDays = Room.readConfig(getContext()).noteFadeDays;
                long stamp = note.stamp(now, fadeDays);
                out.putLong("note_stamp", stamp);
                out.putFloat("note_alpha", stamp == 0 ? 0f : Note.alpha(now - stamp, fadeDays));
                break;
            case "note":
                out.putByteArray("png", note.read());
                break;
            default:
                return null;
        }
        return out;
    }

    private boolean allowed() {
        int uid = Binder.getCallingUid();
        if (uid == SHELL_UID || uid == Process.myUid()) return true;
        return LAUNCHER.equals(getCallingPackage());
    }

    @Override
    public Cursor query(Uri uri, String[] p, String s, String[] a, String o) {
        return null;
    }

    @Override
    public String getType(Uri uri) {
        return null;
    }

    @Override
    public Uri insert(Uri uri, ContentValues v) {
        return null;
    }

    @Override
    public int delete(Uri uri, String s, String[] a) {
        return 0;
    }

    @Override
    public int update(Uri uri, ContentValues v, String s, String[] a) {
        return 0;
    }
}
