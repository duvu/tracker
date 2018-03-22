package hl.tracker;

import android.content.Context;

import com.google.firebase.crash.FirebaseCrash;
import com.snappydb.DB;
import com.snappydb.DBFactory;
import com.snappydb.SnappydbException;

import java.util.ArrayList;
import java.util.List;

import hl.tracker.model.DataModel;

/**
 * Created by beou on 3/2/17.
 */

public class DBTools {
    private static final String EVENT_DATA = "EventData";
    private static DB snappyDb = null;
    public static void init(Context context) {
        if (snappyDb == null) {
            try {
                snappyDb = DBFactory.open(context);
            } catch (SnappydbException e) {
                e.printStackTrace();
                FirebaseCrash.report(e);
            }
        }
    }
    public static void terminate() {
        if (snappyDb != null) {
            try {
                snappyDb.close();
            } catch (SnappydbException e) {
                e.printStackTrace();
                FirebaseCrash.report(e);
            }
        }
    }
    public static void insertEventData(DataModel data) {
        if (data == null) {
            FirebaseCrash.log("Datamodel must be non-null");
            return;
        }
        String key = EVENT_DATA + ":" + data.getTimestamp();
        try {
            snappyDb.put(key, data);
        } catch (SnappydbException e) {
            e.printStackTrace();
            FirebaseCrash.report(e);
        }
    }
    public static void insertEventDataLimited(DataModel data, long limit) {
        long keysLength = countEventData();
        if (keysLength >= limit) {
            //--delete keysLength - limit - 1 first row data
            try {
                String[] keysD = snappyDb.findKeys(EVENT_DATA, 0, (int)(keysLength - limit + 1));
                for (String k : keysD) {
                    snappyDb.del(k);
                }
            } catch (SnappydbException e) {
                e.printStackTrace();
                FirebaseCrash.report(e);
            }
        }
        insertEventData(data);
    }
    public static void deleteEventData(long timestamp) {
        String key = EVENT_DATA + ":" + timestamp;
        try {
            snappyDb.del(key);
        } catch (SnappydbException e) {
            e.printStackTrace();
            FirebaseCrash.report(e);
        }
    }

    public static long countEventData() {
        try {
            return snappyDb.countKeys(EVENT_DATA);
        } catch (SnappydbException e) {
            e.printStackTrace();
            FirebaseCrash.report(e);
        }
        return 0;
    }
    public static DataModel selectEventData(long timestamp) {
        String key = EVENT_DATA+":"+timestamp;
        try {
            return snappyDb.getObject(key, DataModel.class);
        } catch (SnappydbException e) {
            e.printStackTrace();
            FirebaseCrash.report(e);
        }
        return null;
    }
    public static DataModel selectBestFit(long timestamp) {
        int idx = 0;
        String key1 = EVENT_DATA + ":" + (timestamp - 60);
        String key2 = EVENT_DATA + ":" + (timestamp + 60);
        String[] keys = new String[0];
        try {
            keys = snappyDb.findKeysBetween(key1, key2);
            idx = keys.length/2;
            return snappyDb.getObject(keys[idx], DataModel.class);
        } catch (SnappydbException e) {
            e.printStackTrace();
            FirebaseCrash.report(e);
        }
        return null;
    }
    public static List<DataModel> selectAllEventData() {
        List<DataModel> dataList = new ArrayList<>();
        try {
            String[] keys = snappyDb.findKeys(EVENT_DATA);
            for (String k : keys) {
                DataModel d = snappyDb.getObject(k, DataModel.class);
                dataList.add(d);
            }
        } catch (SnappydbException e) {
            e.printStackTrace();
            FirebaseCrash.report(e);
        }
        return dataList;
    }
    public static void delete(String key) {
        try {
            snappyDb.del(key);
        } catch (SnappydbException e) {
            e.printStackTrace();
            FirebaseCrash.report(e);
        }
    }
    public static void deleteAllEventData() {
        try {
            String[] keys = snappyDb.findKeys(EVENT_DATA);
            for (String k : keys) {
                snappyDb.del(k);
            }
        } catch (SnappydbException e) {
            e.printStackTrace();
            FirebaseCrash.report(e);
        }
    }
    //--
    public static void put(String key, String value) {
        try {
            snappyDb.put(key, value);
        } catch (SnappydbException e) {
            e.printStackTrace();
            FirebaseCrash.report(e);
        }
    }
    public static String get(String key) {
        if (StringTools.isBlank(key)) {
            return null;
        }
        try {
            return snappyDb.get(key);
        } catch (SnappydbException e) {
            e.printStackTrace();
            FirebaseCrash.report(e);
        }
        return null;
    }
    public static Boolean getBoolean(String key) {
        if (StringTools.isBlank(key)) {
            return false;
        }
        try {
            return snappyDb.getBoolean(key);
        } catch (SnappydbException e) {
            e.printStackTrace();
            FirebaseCrash.report(e);
        }
        return false;
    }
    public static Boolean getBoolean(String key, boolean dft) {
        if (StringTools.isBlank(key)) {
            return dft;
        }
        try {
            return snappyDb.getBoolean(key);
        } catch (SnappydbException e) {
            e.printStackTrace();
            FirebaseCrash.report(e);
        }
        return dft;
    }
    public static int getInt(String key, int dft) {
        if (StringTools.isBlank(key)) {
            return dft;
        }
        try {
            return snappyDb.getInt(key);
        } catch (SnappydbException e) {
            e.printStackTrace();
            FirebaseCrash.report(e);
        } return dft;
    }
    public static int getInt(String key) {
        return getInt(key, 0);
    }
    public static void putBoolean(String key, Boolean value) {
        if (StringTools.isBlank(key)) {
            return;
        }
        try {
            snappyDb.putBoolean(key, value);
        } catch (SnappydbException e) {
            e.printStackTrace();
            FirebaseCrash.report(e);
        }
    }
    public static void putString(String key, String value) {
        put(key, value);
    }

    public static void putInt(String key, int value) {
        if (StringTools.isBlank(key)) {
            return;
        }
        try {
            snappyDb.putInt(key, value);
        } catch (SnappydbException e) {
            e.printStackTrace();
            FirebaseCrash.report(e);
        }
    }
    public static void putFloat(String key, Float value) {
        if (StringTools.isBlank(key)) {
            return;
        }
        try {
            snappyDb.putFloat(key, value);
        } catch (SnappydbException e) {
            e.printStackTrace();
            FirebaseCrash.report(e);
        }
    }
}
