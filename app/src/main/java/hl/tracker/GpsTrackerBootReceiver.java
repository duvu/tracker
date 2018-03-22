package hl.tracker;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.SystemClock;

public class GpsTrackerBootReceiver extends BroadcastReceiver {
    private static final String TAG = "GpsTrackerBootReceiver";
    private static final String CURRENT_TRACKING = "mCurrentlyTracking";
    private static final String INTERVAL_WAKEUP = "IntervalWakeup";
    @Override
    public void onReceive(Context context, Intent intent) {
        AlarmManager alarmManager = (AlarmManager)context.getSystemService(Context.ALARM_SERVICE);
        Intent gpsTrackerIntent = new Intent(context, GpsTrackerAlarmReceiver.class);
        PendingIntent pendingIntent = PendingIntent.getBroadcast(context, 0, gpsTrackerIntent, 0);

//        SharedPreferences sharedPreferences = context.getSharedPreferences("com.websmithing.gpstracker.prefs", Context.MODE_PRIVATE);
//        int intervalInMinutes = sharedPreferences.getInt("intervalInMinutes", 1);
//        Boolean currentlyTracking = sharedPreferences.getBoolean("currentlyTracking", false);

        boolean currentlyTracking = DBTools.getBoolean(CURRENT_TRACKING, false);
        int mIntervalUpdate = DBTools.getInt(INTERVAL_WAKEUP, (int)AlarmManager.INTERVAL_HOUR);
        if (currentlyTracking) {
            alarmManager.setRepeating(AlarmManager.ELAPSED_REALTIME_WAKEUP,
                    SystemClock.elapsedRealtime(),
                    mIntervalUpdate, // 60000 = 1 minute,
                    pendingIntent);
        } else {
            alarmManager.cancel(pendingIntent);
        }
    }
}
