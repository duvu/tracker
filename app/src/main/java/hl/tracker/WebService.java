package hl.tracker;

import java.io.IOException;

import hl.tracker.box.EventData;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.Response;

public class WebService {
    private static final String URL = "https://panel.mijntrack.be/tracker/";
    public static void postData(EventData evdt, Callback callback) {
        String data = GsonUtils.getInstance().toJson(evdt);
        HttpClient.getIntance().post(URL, data, callback);
    }
}
