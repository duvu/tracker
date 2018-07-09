package hl.tracker;

import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;

public class GsonUtils {
    private static GsonUtils instance = null;
    private Gson gson = null;
    public static GsonUtils getInstance() {
        if (instance == null) {
            instance = new GsonUtils();
        }
        return instance;
    }

    public GsonUtils() {
        gson = new Gson();
    }

    public <T> T fromJson(String json, Class<T> classOfT) {
        try {
            return gson.fromJson(json, classOfT);
        } catch (JsonSyntaxException jse) {
            //Logger.d("[>>JSON ERROR] #" + json);
            return null;
        }
    }

    public String toJson(Object ob) {
        try {
            return gson.toJson(ob);
        } catch (Exception ex) {
            //Logger.e("JsonError", ex);
            return null;
        }
    }
}
