package hl.tracker;

import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.content.Context;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Build;
import android.os.PowerManager;
import android.telephony.TelephonyManager;
import android.text.TextUtils;

import java.net.InetAddress;

public class NetworkUtils {
    private static ConnectivityManager connectivityManager;
    private static TelephonyManager mTelephonyManager;

    private static String GATEWAY_ID = "";

    public static void init(Context context) {
        connectivityManager = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        mTelephonyManager = (TelephonyManager) context.getSystemService(Context.TELEPHONY_SERVICE);
    }

    public static boolean isConnected() {
        NetworkInfo activeNetwork = connectivityManager.getActiveNetworkInfo();
        return activeNetwork != null && activeNetwork.isConnectedOrConnecting();
    }

    public static boolean isWifi() {
        NetworkInfo activeNetwork = connectivityManager.getActiveNetworkInfo();
        return activeNetwork != null && (activeNetwork.getType() == ConnectivityManager.TYPE_WIFI);
    }

    public static boolean isMobile() {
        NetworkInfo activeNetwork = connectivityManager.getActiveNetworkInfo();
        return activeNetwork != null && (activeNetwork.getType() == ConnectivityManager.TYPE_MOBILE);
    }

    public static boolean isInternetAvailable() {
        try {
            InetAddress ipAddr = InetAddress.getByName("smarttrace.com.au");
            return !ipAddr.toString().equals("");
        } catch (Exception e) {
            Logger.e("[Network] + ", e);
            return false;
        }
    }

    /**
     * Returns true if the device is in Doze/Idle mode. Should be called before checking the network connection because
     * the ConnectionManager may report the device is connected when it isn't during Idle mode.
     * https://github.com/yigit/android-priority-jobqueue/blob/master/jobqueue/src/main/java/com/path/android/jobqueue/network/NetworkUtilImpl.java#L60
     */
    @TargetApi(23)
    public static boolean isDozing(Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PowerManager powerManager = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
            return powerManager.isDeviceIdleMode() &&
                    !powerManager.isIgnoringBatteryOptimizations(context.getPackageName());
        } else {
            return false;
        }
    }

    public static String getGatewayId() {
        if (TextUtils.isEmpty(GATEWAY_ID)) {
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    if (mTelephonyManager.getPhoneType() ==  TelephonyManager.PHONE_TYPE_CDMA) {
                        GATEWAY_ID = mTelephonyManager.getMeid();
                    } else {
                        // GSM
                        GATEWAY_ID = mTelephonyManager.getImei();
                    }
                } else {
                    GATEWAY_ID = mTelephonyManager.getDeviceId();
                }
            } catch (Exception e) {
                GATEWAY_ID = "";
            }
        }
        return GATEWAY_ID;
    }
}
