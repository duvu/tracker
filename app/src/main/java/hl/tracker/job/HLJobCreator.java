package hl.tracker.job;

import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import com.evernote.android.job.Job;
import com.evernote.android.job.JobCreator;

public class HLJobCreator implements JobCreator {
    @Nullable
    @Override
    public Job create(@NonNull String tag) {
        switch (tag) {
            case HLJob15.TAG :
                return new HLJob15();
            case HLJob20.TAG :
                return new HLJob20();
            case HLJob25.TAG:
                return new HLJob25();
            default:
                return null;
        }
    }
}
