package hl.tracker;

import android.content.Context;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;

public class SharedRef {
    private static final String PACKAGE = "hl.tracker";
    public static final String KEY_INTERVAL = PACKAGE + ".interval";
    private static final String KEY_PINCODE = PACKAGE + ".pin_code";

    private static SharedPreferences sharedPreferences;
    public static void init(Context ctx) {
        if (sharedPreferences == null) {
            sharedPreferences = PreferenceManager.getDefaultSharedPreferences(ctx);
        }
    }


    public static void setInterval(Long interval) {
        sharedPreferences.edit().putLong(KEY_INTERVAL, interval).apply();
    }

    public static Long getInterval() {
        return sharedPreferences.getLong(KEY_INTERVAL, 30); //default = 30sec
    }

    public static void setPin(String pin) {
        sharedPreferences.edit().putString(KEY_PINCODE, pin).apply();
    }

    public static String getPin() {
        return sharedPreferences.getString(KEY_PINCODE, "");
    }
}
