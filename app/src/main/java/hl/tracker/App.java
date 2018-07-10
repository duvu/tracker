package hl.tracker;

import android.app.Application;
import hl.tracker.box.MyObjectBox;

import io.objectbox.BoxStore;

public class App extends Application {
    public static final String PACKAGE_NAME = "hl.tracker";
    private BoxStore boxStore;

    @Override
    public void onCreate() {
        super.onCreate();
        //start Android-job
        boxStore = MyObjectBox.builder().androidContext(App.this).build();
        NetworkUtils.init(this);
        SharedRef.init(this);
    }

    public BoxStore getBoxStore() {
        return boxStore;
    }
}
