package hl.tracker;

import android.app.Application;

/**
 * Created by beou on 3/2/17.
 */

public class MyApplication extends Application {
    @Override
    public void onCreate() {
        super.onCreate();
        //init db
        DBTools.init(this.getApplicationContext());
    }

    @Override
    public void onTerminate() {
        //destroy db
//        DBTools.terminate();
        super.onTerminate();
    }
}
