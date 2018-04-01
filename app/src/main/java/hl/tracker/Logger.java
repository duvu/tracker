package hl.tracker;

import android.preference.PreferenceManager;
import android.util.Log;

public class Logger {
    public static void d(String message) {
        if (ApplicationConfig.DEBUG) {
            Log.d(ApplicationConfig.TAG, message);
        }
    }
}
