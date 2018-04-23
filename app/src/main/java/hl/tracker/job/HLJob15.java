package hl.tracker.job;

import android.support.annotation.NonNull;

import com.evernote.android.job.Job;
import com.evernote.android.job.JobRequest;

import java.util.concurrent.TimeUnit;

import hl.tracker.App;

public class HLJob15 extends Job {
    public static final String TAG = App.PACKAGE_NAME + ".hljob.tag15";
    @NonNull
    @Override
    protected Result onRunJob(@NonNull Params params) {
        boolean success = (new HLEngine(getContext())).updateLocation();

        return success ? Result.SUCCESS : Result.FAILURE;
    }

    public static void schedule() {
        new JobRequest.Builder(TAG)
                .setPeriodic(TimeUnit.MINUTES.toMillis(15), TimeUnit.MINUTES.toMillis(5))
                .setUpdateCurrent(false)
                .build()
                .scheduleAsync();

    }
}
