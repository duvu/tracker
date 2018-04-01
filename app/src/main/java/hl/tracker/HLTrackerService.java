package hl.tracker;

import android.Manifest;
import android.app.ActivityManager;
import android.app.AlarmManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.location.Address;
import android.location.Geocoder;
import android.location.Location;
import android.os.AsyncTask;
import android.os.BatteryManager;
import android.os.Binder;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.Looper;
import android.os.SystemClock;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v4.app.NotificationCompat;
import android.support.v4.content.LocalBroadcastManager;
import android.text.TextUtils;
import android.util.Log;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GoogleApiAvailability;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.location.ActivityRecognition;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.crash.FirebaseCrash;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import hl.tracker.model.DataModel;
import hl.tracker.model.Resp;
import hl.tracker.net.HttpClient;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

//import com.google.android.gms.common.GooglePlayServicesUtil;

public class HLTrackerService extends Service {

    private static final String PACKAGE_NAME = "hl.tracker";
    private static final String TAG = HLTrackerService.class.getSimpleName();
    private static final String CHANNEL_ID = "channel_01";
    static final String ACTION_BROADCAST = PACKAGE_NAME + ".broadcast";
    static final String EXTRA_LOCATION = PACKAGE_NAME + ".location";
    private static final String EXTRA_STARTED_FROM_NOTIFICATION = PACKAGE_NAME + ".started_from_notification";

    private final IBinder mBinder = new LocalBinder();

    private boolean mChangingConfiguration = false;
    private NotificationManager mNotificationManager;

    private LocationRequest mLocationRequest;
    private FusedLocationProviderClient mFusedLocationClient;
    private LocationCallback mLocationCallback;
    private Handler mServiceHandler;

    private Geocoder mGeocoder;

    //-- current location
    private Location mLocation;

    //-- current battery-level
    private float mBatteryLevel = 0.0f;

    //BatteryReceiver
    private BroadcastReceiver mBatteryInfoReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            mBatteryLevel = calculateBatteryLevel(intent);
        }
    };

    public HLTrackerService() { }


    @Override
    public void onCreate() {
        super.onCreate();
        IntentFilter batteryChangedFilter = new IntentFilter(Intent.ACTION_BATTERY_CHANGED);
        registerReceiver(mBatteryInfoReceiver, batteryChangedFilter);

        mGeocoder = new Geocoder(this, Locale.getDefault());
        mFusedLocationClient = LocationServices.getFusedLocationProviderClient(this);
        mLocationCallback = new LocationCallback() {
            @Override
            public void onLocationResult(LocationResult locationResult) {
                super.onLocationResult(locationResult);
                onNewLocation(locationResult.getLastLocation());
            }
        };

        createLocationRequest();
        getLastLocation();

        HandlerThread handlerThread = new HandlerThread(TAG);
        handlerThread.start();
        mServiceHandler = new Handler(handlerThread.getLooper());

        mNotificationManager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);

        // Android O requires a notification channel
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            CharSequence name = getString(R.string.app_name);
            NotificationChannel channel = new NotificationChannel(CHANNEL_ID, name, NotificationManager.IMPORTANCE_DEFAULT);
            // set the notification channel for notification-manager
            mNotificationManager.createNotificationChannel(channel);
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Logger.d("Service started");

        boolean startedFromNotification = intent.getBooleanExtra(EXTRA_STARTED_FROM_NOTIFICATION, false);
        if (startedFromNotification) {
            removeLocationUpdates();
            stopSelf();
        }
        return START_NOT_STICKY;
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        mChangingConfiguration = true;
    }

    @Override
    public void onDestroy() {
        unregisterReceiver(mBatteryInfoReceiver);
        mServiceHandler.removeCallbacksAndMessages(null);
    }

    @Override
    public IBinder onBind(Intent intent) {
        // Called when a client  comes to the foreground and binds with this service. The service
        // should cease to be a foreground service when that happends.
        Logger.d("in onBind()");
        stopForeground(true);
        mChangingConfiguration = false;
        return mBinder;
    }

    @Override
    public void onRebind(Intent intent) {
        // Called when a client returns to the foreground and binds once again with this service.
        // The service should cease to be a foreground service when that happens.
        Log.i(TAG, "in onRebind()");
        stopForeground(true);
        mChangingConfiguration = false;
        super.onRebind(intent);
    }

    @Override
    public boolean onUnbind(Intent intent) {
        Log.i(TAG, "Last client unbound from service");

        // Called when the last client (MainActivity in case of this sample) unbinds from this
        // service. If this method is called due to a configuration change in MainActivity, we
        // do nothing. Otherwise, we make this service a foreground service.
        if (!mChangingConfiguration && ApplicationConfig.requestingLocationUpdates(this)) {
            Log.i(TAG, "Starting foreground service");
            /*
            // TODO(developer). If targeting O, use the following code.
            if (Build.VERSION.SDK_INT == Build.VERSION_CODES.O) {
                mNotificationManager.startServiceInForeground(new Intent(this,
                        LocationUpdatesService.class), NOTIFICATION_ID, getNotification());
            } else {
                startForeground(NOTIFICATION_ID, getNotification());
            }
             */
            startForeground(ApplicationConfig.NOTIFICATION_ID, getNotification());
        }
        return true; // Ensures onRebind() is called when a client re-binds.
    }


    //--

    /**
     * Makes a request for location updates. Note that in this sample we merely log the
     * {@link SecurityException}.
     */
    public void requestLocationUpdates() {
        Log.i(TAG, "Requesting location updates");
        ApplicationConfig.setRequestingLocationUpdates(this, true);
        startService(new Intent(getApplicationContext(), HLTrackerService.class));
        try {
            mFusedLocationClient.requestLocationUpdates(mLocationRequest, mLocationCallback, Looper.myLooper());
        } catch (SecurityException unlikely) {
            ApplicationConfig.setRequestingLocationUpdates(this, false);
            Log.e(TAG, "Lost location permission. Could not request updates. " + unlikely);
        }
    }

    public void removeLocationUpdates() {
        Logger.d("Removing location update");
        try {
            mFusedLocationClient.removeLocationUpdates(mLocationCallback);
            ApplicationConfig.setRequestingLocationUpdates(this, false);
            stopSelf();
        } catch (SecurityException ex) {
            ApplicationConfig.setRequestingLocationUpdates(this, true);
            Logger.d("Lost location permission. Could not remove update." + ex);
        }
    }

    /**
     * Sets the location request parameters.
     */
    private void createLocationRequest() {
        mLocationRequest = LocationRequest.create();
        mLocationRequest.setInterval(ApplicationConfig.UPDATE_INTERVAL_IN_MILLISECONDS);
        mLocationRequest.setFastestInterval(ApplicationConfig.FASTEST_UPDATE_INTERVAL_IN_MILLISECONDS);
        mLocationRequest.setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY);
    }

    private void getLastLocation() {
        try {
            mFusedLocationClient.getLastLocation()
                    .addOnCompleteListener(new OnCompleteListener<Location>() {
                        @Override
                        public void onComplete(@NonNull Task<Location> task) {
                            if (task.isSuccessful() && task.getResult() != null) {
                                mLocation = task.getResult();
                            } else {
                                Log.w(TAG, "Failed to get location.");
                            }
                        }
                    });
        } catch (SecurityException unlikely) {
            Log.e(TAG, "Lost location permission." + unlikely);
        }
    }

    private void onNewLocation(Location location) {
        Logger.d("New Location: " + location);
        mLocation = location;
        // notify anyone listening for broadcasts about the new location.
        Intent intent = new Intent(ACTION_BROADCAST);
        intent.putExtra(EXTRA_LOCATION, location);
        LocalBroadcastManager.getInstance(getApplicationContext()).sendBroadcast(intent);

        // Update notification content if running as a foreground service
        if (serviceIsRunningInForeground(this)) {
            mNotificationManager.notify(ApplicationConfig.NOTIFICATION_ID, getNotification());
        }
    }

    // calculate batter level
    private float calculateBatteryLevel(Intent battState) {
        if (battState == null) return 0;
        int level = battState.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
        int scale = battState.getIntExtra(BatteryManager.EXTRA_SCALE, -1);

        return (level / (float) scale);
    }

    private Notification getNotification() {
        Intent intent = new Intent(this, HLTrackerService.class);
        CharSequence text = Utils.getLocationText(mLocation);

        // extra to help figure out if we arrived in onStartCommand via notification or not
        intent.putExtra(EXTRA_STARTED_FROM_NOTIFICATION, true);

        // the PendingIntent that leads to a call to onStartCommand() in this service
        PendingIntent servicePendingIntent = PendingIntent.getService(this, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT);

        // The PendingIntent to launch activity
        PendingIntent activityPendingIntent = PendingIntent.getActivity(this, 0, new Intent(this, GpsTrackerActivity.class), 0);

        NotificationCompat.Builder builder = new NotificationCompat.Builder(this)
                .addAction(R.drawable.ic_launch, getString(R.string.launch_activity), activityPendingIntent)
                .addAction(R.drawable.ic_cancel, getString(R.string.remove_location_updates), servicePendingIntent)
                .setContentText(text)
                .setContentTitle(Utils.getLocationTitle(this))
                .setOngoing(true)
                .setPriority(Notification.PRIORITY_HIGH)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setTicker(text)
                .setWhen(System.currentTimeMillis());

        // set the Channel ID for Android O
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            builder.setChannelId(CHANNEL_ID);
        }
        return builder.build();
    }

    public class LocalBinder extends Binder {
        HLTrackerService getService() {
            return HLTrackerService.this;
        }
    }

    /**
     * Returns true if this is a foreground service
     * @param context The {@link Context}
     */
    public boolean serviceIsRunningInForeground(Context context) {
        ActivityManager manager = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
        for (ActivityManager.RunningServiceInfo serviceInfo: manager.getRunningServices(Integer.MAX_VALUE)) {
            if (getClass().getName().equals(serviceInfo.service.getClassName())) {
                if (serviceInfo.foreground) {
                    return true;
                }
            }
        }
        return false;
    }
}
