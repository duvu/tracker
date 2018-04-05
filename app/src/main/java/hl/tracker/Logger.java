package hl.tracker;

import android.util.Log;

public class Logger {
    public static void d(String message) {
        if (AppConfig.DEBUG) {
            Log.d(AppConfig.TAG, message);
        }
    }
}
