package hl.tracker;

import android.content.Context;
import android.preference.PreferenceManager;

public class ApplicationConfig {
    static final String KEY_TAG = "tag";
    static final String KEY_DEBUG = "debug_enabled";

    public static boolean DEBUG = false;
    public static String TAG = "hl.tracker";

    public static long UPDATE_INTERVAL_IN_MILLISECONDS = 10000;
    public static long FASTEST_UPDATE_INTERVAL_IN_MILLISECONDS = UPDATE_INTERVAL_IN_MILLISECONDS / 2;
    public static final int NOTIFICATION_ID = 12345678;

    public static void populate(Context context) {
        DEBUG = PreferenceManager.getDefaultSharedPreferences(context).getBoolean(KEY_DEBUG, DEBUG);
        TAG = PreferenceManager.getDefaultSharedPreferences(context).getString(KEY_TAG, TAG);
    }

    public static void setDebug(Context context, boolean debug) {
        PreferenceManager.getDefaultSharedPreferences(context).edit().putBoolean(KEY_DEBUG, debug).apply();
    }
}
