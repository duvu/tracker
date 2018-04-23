package hl.tracker.job;

import android.support.annotation.NonNull;

import com.evernote.android.job.Job;
import com.evernote.android.job.JobRequest;

import java.util.concurrent.TimeUnit;

import hl.tracker.App;

public class HLJob25 extends Job {
    public static final String TAG = App.PACKAGE_NAME + ".hljob.tag25";
    @NonNull
    @Override
    protected Result onRunJob(@NonNull Params params) {
        new JobRequest.Builder(HLJob15.TAG)
                .setPeriodic(TimeUnit.MINUTES.toMillis(15), TimeUnit.MINUTES.toMillis(5))
                .setUpdateCurrent(false)
                .build()
                .scheduleAsync();
        return Result.SUCCESS;
    }

    public static void schedule() {
        new JobRequest.Builder(TAG)
                .setExact(TimeUnit.MINUTES.toMillis(25))
                .build().scheduleAsync();
    }
}
