package hl.tracker;

import android.app.Application;

import com.evernote.android.job.JobManager;

import hl.tracker.box.MyObjectBox;
import hl.tracker.job.HLJobCreator;
import io.objectbox.BoxStore;

public class App extends Application {
    public static final String PACKAGE_NAME = "hl.tracker";
    private BoxStore boxStore;

    @Override
    public void onCreate() {
        super.onCreate();
        //start Android-job
        JobManager.create(this).addJobCreator(new HLJobCreator());
        boxStore = MyObjectBox.builder().androidContext(App.this).build();
    }

    public BoxStore getBoxStore() {
        return boxStore;
    }
}
