package hl.tracker;

import com.google.gson.JsonSyntaxException;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;

/**
 * Created by beou on 3/21/18.
 */

public class HttpClient {
    private OkHttpClient client = null;
    private static HttpClient httpClient = null;

    public static HttpClient getIntance() {
        if (httpClient == null) {
            httpClient = new HttpClient();
        }
        return httpClient;
    }

    private HttpClient() {
        client = new OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(20, TimeUnit.SECONDS)
                .writeTimeout(20, TimeUnit.SECONDS)
                .build();
    }

    //-- Sync
    public <T> T get(String url, Class<T> clazz) throws IOException {
        Request request = new Request.Builder()
                .url(url)
                .get()
                .build();

        Response response = client.newCall(request).execute();
        ResponseBody body = response.body();
        return GsonUtils.getInstance().fromJson(body.string(), clazz);
    }

    public <T> T post(String url, String data, Class<T> clazz) throws IOException {
        MediaType mediaType = getMediaType(data);
        RequestBody body = RequestBody.create(mediaType, data);
        Request request = new Request.Builder()
                .url(url)
                .post(body)
                .build();
        Response response = client.newCall(request).execute();
        ResponseBody body1 = response.body();
        return GsonUtils.getInstance().fromJson(body1.string(), clazz);
    }

    public void post(String url, String data, Callback callback) {
        MediaType mediaType = getMediaType(data);
        RequestBody body = RequestBody.create(mediaType, data);
        Request request = new Request.Builder()
                .url(url)
                .post(body)
                .build();
        client.newCall(request).enqueue(callback);
    }

    public void get(String url, Callback callback) {
        Request request = new Request.Builder()
                .url(url)
                .get()
                .build();
        client.newCall(request).enqueue(callback);
    }

    private MediaType getMediaType(String data) {
        if (isJson(data)) {
            return MediaType.parse("application/json; charset=utf-8");
        } else {
            return MediaType.parse("text/plain; charset=utf-8");
        }
    }

    private boolean isJson(String json) {
        try {
            GsonUtils.getInstance().fromJson(json, Object.class);
            return true;
        } catch (JsonSyntaxException ex) {
            return false;
        }
    }
}
