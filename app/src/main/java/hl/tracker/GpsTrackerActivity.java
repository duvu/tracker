package hl.tracker;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.SystemClock;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GoogleApiAvailability;

public class GpsTrackerActivity extends AppCompatActivity {
    private static final String TAG = "GpsTrackerActivity";
    private static final String CURRENT_TRACKING = "mCurrentlyTracking";
    private static final String DEVICE_ID = "deviceId";
    private static final String INTERVAL_WAKEUP = "IntervalWakeup";
    private static final String IS_FIRST_RUN = "isFirstRun";

    private boolean mCurrentlyTracking = false;
    private boolean mIsFirstRun = true;
    private long mIntervalWakeup = AlarmManager.INTERVAL_HOUR;
    private String deviceId;

    private TextView txtDeviceId;
    private Button btnHidden;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_gps_tracker);

        getSupportActionBar().setDisplayShowHomeEnabled(true);
        getSupportActionBar().setLogo(R.mipmap.ic_launcher);
        getSupportActionBar().setDisplayUseLogoEnabled(true);

        mIsFirstRun = DBTools.getBoolean(IS_FIRST_RUN, true);
        if (mIsFirstRun) {
            mCurrentlyTracking = false;
            DBTools.putBoolean(CURRENT_TRACKING, mCurrentlyTracking);
        } else {
            mCurrentlyTracking = DBTools.getBoolean(CURRENT_TRACKING);
        }
        mIntervalWakeup = DBTools.getInt(INTERVAL_WAKEUP, (int)AlarmManager.INTERVAL_HOUR);
        deviceId = StringTools.getDeviceId(getApplicationContext());

        //--
        txtDeviceId = (TextView) findViewById(R.id.deviceId);
        btnHidden = (Button) findViewById(R.id.hidden_activity);
        txtDeviceId.setText(deviceId);
        btnHidden.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finish();
            }
        });
        startTrack();
    }

    private void startAlarmManager() {
        Log.d(TAG, "startAlarmManager");
        Context context = getApplicationContext();
        AlarmManager alarmManager = (AlarmManager)context.getSystemService(Context.ALARM_SERVICE);
        Intent gpsTrackerIntent = new Intent(context, GpsTrackerAlarmReceiver.class);
        PendingIntent pendingIntent = PendingIntent.getBroadcast(context, 0, gpsTrackerIntent, 0);

        alarmManager.setRepeating(AlarmManager.ELAPSED_REALTIME_WAKEUP,
                SystemClock.elapsedRealtime(),
                mIntervalWakeup,
                pendingIntent);
    }

    private void cancelAlarmManager() {
        Log.d(TAG, "cancelAlarmManager");
        Context context = getApplicationContext();
        Intent gpsTrackerIntent = new Intent(context, GpsTrackerAlarmReceiver.class);
        PendingIntent pendingIntent = PendingIntent.getBroadcast(context, 0, gpsTrackerIntent, 0);
        AlarmManager alarmManager = (AlarmManager)context.getSystemService(Context.ALARM_SERVICE);
        alarmManager.cancel(pendingIntent);
    }

    // called when mTrackingButton is tapped
    protected void startTrack() {
        Log.i(TAG, "...startTrack");
        if (!checkIfGooglePlayEnabled()) {
            return;
        }

        if (!mCurrentlyTracking) {
//            DBTools.put(DEVICE_ID, "clpc4qdu7wu");
            DBTools.put(DEVICE_ID, deviceId);
            startAlarmManager();
            mCurrentlyTracking = true;
            DBTools.putBoolean(CURRENT_TRACKING, mCurrentlyTracking);
        }
    }

    private boolean checkIfGooglePlayEnabled() {
        if (GoogleApiAvailability.getInstance().isGooglePlayServicesAvailable(this) == ConnectionResult.SUCCESS) {
            return true;
        } else {
            Log.e(TAG, "unable to connect to google play services.");
            Toast.makeText(getApplicationContext(), R.string.google_play_services_unavailable, Toast.LENGTH_LONG).show();
            return false;
        }
    }

    @Override
    public void onResume() {
        super.onResume();
    }

    @Override
    protected void onStop() {
        super.onStop();
    }
}
