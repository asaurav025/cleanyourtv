package dev.asaurav.myhome;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.net.Uri;
import android.os.Binder;
import android.os.Bundle;
import android.os.Process;

/**
 * What the Clean Your TV launcher talks to (theme key paired_app). Only call(); no data, no files:
 *   focus  "#RRGGBB"   the focused tile's glow colour
 *   launch "<package>" Home opened an app
 *   home               Home came back to the front
 *   state              -> {"line": the market strip, "greeting": an override or "", "scene": "dusk/rain"}
 * Only the launcher and the ADB shell (for testing with `content call`) may call it, so no other app can drive the
 * room's lights.
 */
public final class HomeProvider extends ContentProvider {
    static final String LAUNCHER = "com.gathin.tvhome";
    /** android.os.Process.SHELL_UID, which the SDK hides. */
    private static final int SHELL_UID = 2000;

    @Override
    public boolean onCreate() {
        return true;
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
                room.home();
                break;
            case "state":
                out.putString("line", room.line());
                out.putString("greeting", room.greeting());
                out.putString("scene", room.scene());
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
