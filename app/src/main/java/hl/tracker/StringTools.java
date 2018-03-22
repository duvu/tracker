package hl.tracker;

import android.content.Context;
import android.text.TextUtils;

import com.google.android.gms.iid.InstanceID;

import java.util.List;

/**
 * Created by beou on 3/3/17.
 */

public class StringTools {
    private static final String DEVICE_ID = "deviceId";
    public static boolean isBlank(String s) {
        return TextUtils.isEmpty(s);
    }
    public static String getDeviceId(Context context) {
        String id = null;
        id = DBTools.get(DEVICE_ID);
        if (StringTools.isBlank(id)) {
            id = InstanceID.getInstance(context).getId();
        }
        return id;
    }
    public static float mps2kph(float mps) {
        return mps*3.6f;
    }
}
