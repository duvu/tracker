package hl.tracker.net;

import java.util.List;

import hl.tracker.model.DataModel;
import hl.tracker.model.Resp;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.http.Body;
import retrofit2.http.POST;

/**
 * Created by beou on 3/2/17.
 */

public interface TrackerService {
    @POST("tracker/")
    Call<Resp> post(@Body DataModel data);

    @POST("tracker/old")
    Call<Resp> post(@Body List<DataModel> data);
}
