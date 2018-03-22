package hl.tracker.net;

import java.util.List;

import hl.tracker.model.DataModel;
import hl.tracker.model.Resp;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

/**
 * Created by beou on 3/2/17.
 */

public class HttpClient {
    private static final String DEFAULT_BASE_URL = "https://panel.mijntrack.be/";
    private static TrackerService tracker;
    private static HttpClient client = null;
    public static HttpClient init(String baseUrl) {
        if (baseUrl == null) {
            baseUrl = DEFAULT_BASE_URL;
        }
        Retrofit retrofit = new Retrofit.Builder()
                .baseUrl(baseUrl)
                .addConverterFactory(GsonConverterFactory.create())
                .build();
        tracker = retrofit.create(TrackerService.class);
        if (client == null) {
            client = new HttpClient();
        }
        return client;
    }

    public void post(DataModel data, Callback<Resp> callback) {
        Call<Resp> call = tracker.post(data);
        call.enqueue(callback);
    }
    public void post(List<DataModel> dataModels, Callback<Resp> callback) {
        Call<Resp> call = tracker.post(dataModels);
        call.enqueue(callback);
    }
}
