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
        // if we are currently trying to get a location and the alarm manager has called this again,
        // no need to start processing a new location.
        if (!currentlyProcessingLocation) {
            currentlyProcessingLocation = true;
            startTracking(intervalUpdate);
        }

        return START_NOT_STICKY;
    }

    private void startTracking(long intervalTime) {
        Logger.d( "startTracking");
        //-- send old data
        sendOldData();
        if (GoogleApiAvailability.getInstance().isGooglePlayServicesAvailable(this) == ConnectionResult.SUCCESS) {
            googleApiClient = new GoogleApiClient.Builder(this)
                    .addApi(LocationServices.API)
                    .addApi(ActivityRecognition.API)
                    .addConnectionCallbacks(this)
                    .addOnConnectionFailedListener(this)
                    .build();

            if (!googleApiClient.isConnected() || !googleApiClient.isConnecting()) {
                setupRequests(intervalTime);
                googleApiClient.connect();
            }
        } else {
            Log.e(TAG, "unable to connect to google play services.");
        }
    }

    private void setupRequests(long intervalTime) {
        if (intervalTime < DEFAULT_INTERVAL_LOCATION_UPDATE) {
            intervalTime = DEFAULT_INTERVAL_LOCATION_UPDATE;
        }
        locationRequest = LocationRequest.create();
        locationRequest.setInterval(intervalTime); // milliseconds
        locationRequest.setFastestInterval(intervalTime/2); // the fastest rate in milliseconds at which your app can handle location updates
        locationRequest.setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY);
    }

    protected void sendOldData() {
        final List<DataModel> dataList = DBTools.selectAllEventData();
        if (dataList == null || dataList.size() == 0) {
            return;
        }
        HttpClient.init(baseUrl).post(dataList, new Callback<Resp>() {
            @Override
            public void onResponse(Call<Resp> call, Response<Resp> response) {
                //delete all events
                //DBTools.deleteAllEventData();
                for (DataModel dm : dataList) {
                    DBTools.deleteEventData(dm.getTimestamp());
                }
            }

            @Override
            public void onFailure(Call<Resp> call, Throwable t) {
                //noop
            }
        });
    }

    protected void postDataToServer(Location location) {
        final DataModel data = new DataModel(StringTools.getDeviceId(getApplicationContext()));
        data.updateLocation(location);
        data.setBatteryLevel(mBatteryLevel);
        data.setSatCount(10);
        data.setSignalStrength(1.0f);
        postDataToServer(data);
    }

    protected void postDataToServer(final DataModel data) {
        HttpClient.init(baseUrl).post(data, new Callback<Resp>() {
            @Override
            public void onResponse(Call<Resp> call, Response<Resp> response) {
                Resp resp = response.body();
                switch (resp.getCode()) {
                    case -1: //not yet added or malformed data
                        //cancelAlarmManager();
                        //restartAlarmManager(10); //check every 10 minutes
                        stopLocationUpdates();
                        stopSelf();
                        break;
                    case 401: //device was inactivated
                        //restartAlarmManager(24*60); //check next day
                        //cancelAlarmManager();
                        stopLocationUpdates();
                        stopSelf();
                        break;
                }
            }

            @Override
            public void onFailure(Call<Resp> call, Throwable t) {
                //store data to DB
                DBTools.insertEventDataLimited(data, limitStorage);
                FirebaseCrash.report(t);
            }
        });
    }


    @Override
    public void onDestroy() {
        unregisterReceiver(mBatteryInfoReceiver);
        stopLocationUpdates();
        stopSelf(); //stop service
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onLocationChanged(Location location) {
        if (location != null) {
            Log.e(TAG, "position: " + location.getLatitude() + ", " + location.getLongitude() + " accuracy: " + location.getAccuracy());

            // we have our desired accuracy of 500 meters so lets quit this service,
            // onDestroy will be called and stop our location uodates
            if (location.getAccuracy() < 2000.0f) {
                //stopLocationUpdates();
                //postDataToServer(location);
                geocoderAsyncTask = new GeocoderAsyncTask();
                geocoderAsyncTask.execute(location);
            }
        }
    }

    private void stopLocationUpdates() {
        Log.i(TAG, "...stopLocationUpdates");
        if (googleApiClient != null && googleApiClient.isConnected()) {
            LocationServices.FusedLocationApi.removeLocationUpdates(googleApiClient, this);
            googleApiClient.disconnect();
        }
    }

    /**
     * Called by Location Services when the request to connect the
     * client finishes successfully. At this point, you can
     * request the current location or start periodic updates
     */
    @Override
    public void onConnected(Bundle bundle) {
        Logger.d( "onConnected");
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            // TODO: Consider calling
            //    ActivityCompat#requestPermissions
            // here to request the missing permissions, and then overriding
            //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
            //                                          int[] grantResults)
            // to handle the case where the user grants the permission. See the documentation
            // for ActivityCompat#requestPermissions for more details.
            return;
        }
        LocationServices.FusedLocationApi.requestLocationUpdates(googleApiClient, locationRequest, this);
        //--
        Intent intent = new Intent(this, DetectedActivityService.class );
        PendingIntent pendingIntent = PendingIntent.getService(this, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT);
        ActivityRecognition.ActivityRecognitionApi.requestActivityUpdates(googleApiClient, intervalUpdate, pendingIntent);
    }

    @Override
    public void onConnectionFailed(ConnectionResult connectionResult) {
        Log.e(TAG, "onConnectionFailed");
        stopLocationUpdates();
        stopSelf(); //stop service
    }

    @Override
    public void onConnectionSuspended(int i) {
        Log.e(TAG, "GoogleApiClient connection has been suspend");
        stopLocationUpdates();
        stopSelf(); //stop service
    }

    // calculate batter level
    private float calculateBatteryLevel(Intent battState) {
        if (battState == null) return 0;
        int level = battState.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
        int scale = battState.getIntExtra(BatteryManager.EXTRA_SCALE, -1);

        return (level / (float) scale);
    }

    private void restartAlarmManager(int intervalInMinutes) {
        Logger.d( "startAlarmManager");
        Context context = getApplicationContext();
        Intent gpsTrackerIntent = new Intent(context, GpsTrackerAlarmReceiver.class);
        PendingIntent pendingIntent = PendingIntent.getBroadcast(context, 0, gpsTrackerIntent, 0);
        AlarmManager alarmManager = (AlarmManager)context.getSystemService(Context.ALARM_SERVICE);
        alarmManager.setRepeating(AlarmManager.ELAPSED_REALTIME_WAKEUP,
                SystemClock.elapsedRealtime(),
                intervalInMinutes * 60000, // 60000 = 1 minute
                pendingIntent);
    }
    private void cancelAlarmManager() {
        Logger.d( "cancelAlarmManager");

        Context context = getBaseContext();
        Intent gpsTrackerIntent = new Intent(context, GpsTrackerAlarmReceiver.class);
        PendingIntent pendingIntent = PendingIntent.getBroadcast(context, 0, gpsTrackerIntent, 0);
        AlarmManager alarmManager = (AlarmManager)context.getSystemService(Context.ALARM_SERVICE);
        alarmManager.cancel(pendingIntent);
    }

    private class GeocoderAsyncTask extends AsyncTask<Location, Void, DataModel> {

        @Override
        protected DataModel doInBackground(Location... locations) {
            List<Address> addresses = null;

            try {
                addresses = geocoder.getFromLocation(
                        locations[0].getLatitude(),
                        locations[0].getLongitude(),
                        // In this sample, get just a single address.
                        1);
            } catch (IOException ioException) {
                FirebaseCrash.report(ioException);
            } catch (IllegalArgumentException illegalArgumentException) {
                FirebaseCrash.report(illegalArgumentException);
            }

            // Handle case where no address was found.
            if (addresses == null || addresses.size()  == 0) {

            } else {
                Address address = addresses.get(0);
                ArrayList<String> addressFragments = new ArrayList<String>();
                for(int i = 0; i < address.getMaxAddressLineIndex(); i++) {
                    addressFragments.add(address.getAddressLine(i));
                }
                String strAddress =  TextUtils.join(System.getProperty("line.separator"), addressFragments);
                DataModel dataModel = new DataModel(StringTools.getDeviceId(getApplicationContext()));
                dataModel.updateLocation(locations[0]);
                dataModel.setAddress(strAddress);
                dataModel.setBatteryLevel(mBatteryLevel);
                dataModel.setSatCount(10);
                dataModel.setSignalStrength(1.0f);
                return dataModel;
            }
            return null;
        }
        @Override
        protected void onPostExecute(DataModel data) {
            postDataToServer(data);
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
