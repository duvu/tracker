package hl.tracker;

import android.annotation.TargetApi;
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
import android.content.res.Configuration;
import android.hardware.GeomagneticField;
import android.location.Address;
import android.location.Geocoder;
import android.location.Location;
import android.os.BatteryManager;
import android.os.Binder;
import android.os.Build;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.Looper;
import android.os.PowerManager;
import android.os.SystemClock;
import android.support.annotation.NonNull;
import android.support.annotation.RequiresApi;
import android.support.v4.app.NotificationCompat;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;

import java.io.IOException;
import java.util.List;
import java.util.Locale;

import hl.tracker.box.EventData;
import io.objectbox.Box;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.Response;

public class HLService extends Service {

    private static final String PACKAGE_NAME                        = "hl.tracker";
    private static final String TAG                                 = HLService.class.getSimpleName();
    private static final String CHANNEL_ID                          = "channel_01";
    static final String ACTION_BROADCAST                            = PACKAGE_NAME + ".broadcast";
    static final String EXTRA_LOCATION                              = PACKAGE_NAME + ".location";
    static final String EXTRA_ADDRESS                              = PACKAGE_NAME + ".address";
    private static final String EXTRA_STARTED_FROM_NOTIFICATION     = PACKAGE_NAME + ".started_from_notification";

    private final IBinder mBinder = new LocalBinder();
    private NotificationManager mNotificationManager;

    private LocationRequest mLocationRequest;
    private FusedLocationProviderClient mFusedLocationClient;
    private LocationCallback mLocationCallback;
    private Handler mServiceHandler;

    AlarmManager nextPointAlarmManager;

    private Geocoder mGeocoder;
    private Box<EventData> eventBox;

    //-- current location
    private Location mLocation;

    //-- current battery-level
    private Double mBatteryLevel = 0d;

    //BatteryReceiver
    private BroadcastReceiver mBatteryInfoReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            mBatteryLevel = calculateBatteryLevel(intent);
        }
    };

    //----------------------------------------------------------------------------------------------
    private Runnable stopManagerRunable = new Runnable() {
        @Override
        public void run() {
            Logger.d("[>_] Absolute timeout reached");
            stopManagerAndResetAlarm();
        }
    };

    private void startAbsoluteTimer() {
        mServiceHandler.postDelayed(stopManagerRunable, 30000);
    }
    private void stopAbsoluteTimer() {
        mServiceHandler.removeCallbacks(stopManagerRunable);
    }

    private void stopManagerAndResetAlarm() {
        removeLocationUpdates();
        stopAbsoluteTimer();
        setAlarmForNextPoint();
    }
    //----------------------------------------------------------------------------------------------

    public HLService() { }


    @Override
    public void onCreate() {
        super.onCreate();
        IntentFilter batteryChangedFilter = new IntentFilter(Intent.ACTION_BATTERY_CHANGED);
        registerReceiver(mBatteryInfoReceiver, batteryChangedFilter);

        nextPointAlarmManager = (AlarmManager) getSystemService(ALARM_SERVICE);

        mGeocoder = new Geocoder(this, Locale.getDefault());
        if (HLUtils.isGooglePlayServicesAvailable(this)) {
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
        }

        HandlerThread handlerThread = new HandlerThread(TAG);
        handlerThread.start();
        mServiceHandler = new Handler(handlerThread.getLooper());

        mNotificationManager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);

        // Android O requires a notification channel
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            CharSequence name = getString(R.string.app_name);
            NotificationChannel channel = new NotificationChannel(CHANNEL_ID, name, NotificationManager.IMPORTANCE_NONE);
            // set the notification channel for notification-manager
            mNotificationManager.createNotificationChannel(channel);
        }

        eventBox = ((App) getApplicationContext()).getBoxStore().boxFor(EventData.class);
        startForeground(AppConfig.NOTIFICATION_ID, getNotification());
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Logger.d("Service started");

        requestUpdateData();
        startAbsoluteTimer();
        return START_NOT_STICKY;
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
    }

    @Override
    public void onDestroy() {
        unregisterReceiver(mBatteryInfoReceiver);
        mServiceHandler.removeCallbacksAndMessages(null);
        stopForeground(true);
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        // Called when a client  comes to the foreground and binds with this service. The service
        // should cease to be a foreground service when that happends.
        Logger.d("in onBind()");
        //stopForeground(true);
        return mBinder;
    }

    @Override
    public void onRebind(Intent intent) {
        // Called when a client returns to the foreground and binds once again with this service.
        // The service should cease to be a foreground service when that happens.
        Log.i(TAG, "in onRebind()");
        //stopForeground(true);
        super.onRebind(intent);
    }

    @Override
    public boolean onUnbind(Intent intent) {
        Log.i(TAG, "Last client unbound from service");
        //startForeground(AppConfig.NOTIFICATION_ID, getNotification());
        return true; // Ensures onRebind() is called when a client re-binds.
    }


    //--
    private void requestUpdateData() {
        Logger.d("[starting location] ...");
        requestLocationUpdates();

        //startAbsoluteTimer();
    }
    /**
     * Makes a request for location updates. Note that in this sample we merely log the
     * {@link SecurityException}.
     */
    public void requestLocationUpdates() {
        Log.i(TAG, "Requesting location updates");
        try {
            mFusedLocationClient.requestLocationUpdates(mLocationRequest, mLocationCallback, Looper.myLooper());
        } catch (SecurityException unlikely) {
            Log.e(TAG, "Lost location permission. Could not request updates. " + unlikely);
        }
    }

    public void removeLocationUpdates() {
        Logger.d("Removing location update");
        try {
            mFusedLocationClient.removeLocationUpdates(mLocationCallback);
        } catch (SecurityException ex) {
            Logger.d("Lost location permission. Could not remove update." + ex);
        }
    }

    /**
     * Sets the location request parameters.
     */
    private void createLocationRequest() {
        mLocationRequest = LocationRequest.create();
        mLocationRequest.setInterval(AppConfig.UPDATE_INTERVAL);
        mLocationRequest.setFastestInterval(AppConfig.FASTEST_UPDATE_INTERVAL);
        mLocationRequest.setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY);
        mLocationRequest.setSmallestDisplacement(AppConfig.SMALLEST_DISPLACEMENT);
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
        mLocation = location;
        Intent intent = new Intent(ACTION_BROADCAST);
        intent.putExtra(EXTRA_LOCATION, location);
        final EventData evdt = new EventData();

        try {
            List<Address> addresses = mGeocoder.getFromLocation(location.getLatitude(), location.getLongitude(), 1);
            evdt.setAddress(addresses.get(0).getAddressLine(0));
            intent.putExtra(EXTRA_ADDRESS, addresses.get(0).getAddressLine(0));
        } catch (IOException e) {
            e.printStackTrace();
        }

        LocalBroadcastManager.getInstance(getApplicationContext()).sendBroadcast(intent);

        evdt.setDeviceId(NetworkUtils.getGatewayId());
        evdt.setLatitude(location.getLatitude());
        evdt.setLongitude(location.getLongitude());
        evdt.setAltitude(location.getAltitude());
        evdt.setHeading(Float.valueOf(location.getBearing()).doubleValue());
        evdt.setSpeedKph((double)location.getSpeed());
        evdt.setBatteryLevel(mBatteryLevel);
        evdt.setStatusCode(0);
        evdt.setTimestamp(location.getTime()/1000);
        WebService.postData(evdt, new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                //save to database
                Logger.e("[>_] failed", e);
                saveToDB(evdt);
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                //check if have old events in database and send
                pushOldData();
            }
        });

        stopManagerAndResetAlarm();
    }

    private void saveToDB(EventData evdt) {
        eventBox.put(evdt);
    }

    private void pushOldData() {
        List<EventData> evdtList = eventBox.getAll();
        for (final EventData evdt : evdtList) {
            WebService.postData(evdt, new Callback() {
                @Override
                public void onFailure(Call call, IOException e) {
                    //noop
                }

                @Override
                public void onResponse(Call call, Response response) throws IOException {
                    eventBox.remove(evdt);
                }
            });
        }
    }

    // calculate batter level
    private Double calculateBatteryLevel(Intent battState) {
        if (battState == null) return 0.0;
        int level = battState.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
        int scale = battState.getIntExtra(BatteryManager.EXTRA_SCALE, -1);

        return ((double)level / (double)scale);
    }

    private Notification getNotification() {

        Logger.d("[>_] getNotification() ...");
        PendingIntent activityPendingIntent = PendingIntent.getActivity(this, 0, new Intent(this, MainActivity.class), 0);

        String text = getString(R.string.app_name);

        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, CHANNEL_ID)
                .addAction(R.drawable.ic_launch, getString(R.string.launch_activity), activityPendingIntent)
                //.addAction(R.drawable.ic_cancel, getString(R.string.remove_location_updates), servicePendingIntent)
                .setContentText(text)
                .setContentTitle("GpsTracker")
                .setOngoing(true)
                .setSmallIcon(R.drawable.notification)
                .setWhen(System.currentTimeMillis());

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
            builder.setPriority(Notification.PRIORITY_MIN);
        }

        // set the Channel ID for Android O
//        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
//            builder.setChannelId(CHANNEL_ID);
//        }
        return builder.build();
    }

    public class LocalBinder extends Binder {
        HLService getService() {
            return HLService.this;
        }
    }

    @TargetApi(23)
    private void setAlarmForNextPoint() {
        Logger.d("[>_] Set alarm in: 10 seconds");

        Intent i = new Intent(this, HLService.class);
        PendingIntent pi = PendingIntent.getService(this, 0, i, 0);
        nextPointAlarmManager.cancel(pi);

        if(isDozing(this)){
            //Only invoked once per 15 minutes in doze mode
            Logger.d("Device is dozing, using infrequent alarm");
            nextPointAlarmManager.setExactAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, SystemClock.elapsedRealtime() + 30000, pi);
        }
        else {
            nextPointAlarmManager.set(AlarmManager.ELAPSED_REALTIME_WAKEUP, SystemClock.elapsedRealtime() + 30000, pi);
        }
    }

    /**
     * Returns true if the device is in Doze/Idle mode. Should be called before checking the network connection because
     * the ConnectionManager may report the device is connected when it isn't during Idle mode.
     * https://github.com/yigit/android-priority-jobqueue/blob/master/jobqueue/src/main/java/com/path/android/jobqueue/network/NetworkUtilImpl.java#L60
     */
    @TargetApi(23)
    public static boolean isDozing(Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PowerManager powerManager = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
            return powerManager.isDeviceIdleMode() &&
                    !powerManager.isIgnoringBatteryOptimizations(context.getPackageName());
        } else {
            return false;
        }
    }


}
