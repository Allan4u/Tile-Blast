package com.allan.tileblast;

import android.content.Context;

/**
 * Tiny holder for the application context so static API surfaces (like
 * {@link com.allan.tileblast.game.Piece}'s color accessors) can resolve a
 * Context without having one threaded through every call site.
 *
 * Activities should call {@link #init(Context)} once during {@code onCreate}.
 */
public final class AppContext {
    private static Context applicationContext;

    private AppContext() {}

    public static void init(Context c) {
        if (c != null) {
            applicationContext = c.getApplicationContext();
        }
    }

    public static Context get() {
        return applicationContext;
    }
}
