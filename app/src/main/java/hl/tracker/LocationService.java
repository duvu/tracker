package hl.tracker;

import android.Manifest;
import android.app.AlarmManager;
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
import android.os.Bundle;
import android.os.IBinder;
import android.os.SystemClock;
import android.support.v4.app.ActivityCompat;
import android.text.TextUtils;
import android.util.Log;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GoogleApiAvailability;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.location.ActivityRecognition;
import com.google.android.gms.location.LocationListener;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationServices;
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

public class LocationService extends Service implements
        GoogleApiClient.ConnectionCallbacks,
        GoogleApiClient.OnConnectionFailedListener,
        LocationListener {

    private static final String TAG = "LocationService";
    private static final String LIMIT_STORAGE = "LimitStorage";
    private static final String BASE_URL = "baseUrl";
    private static final String INTERVAL_UPDATE = "intervalUpdate";

    private static final int DEFAULT_INTERVAL_LOCATION_UPDATE = 10000;

    private boolean currentlyProcessingLocation = false;
    private LocationRequest locationRequest;
    private GoogleApiClient googleApiClient;
    private Geocoder geocoder;
    private GeocoderAsyncTask geocoderAsyncTask;

    private String baseUrl = null;
    private int limitStorage = 10000;
    private long intervalUpdate = 10000;
    private float battLevel = 0f;
    //BatteryReceiver
    private BroadcastReceiver mBatteryInfoReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            battLevel = calculateBatteryLevel(intent);
        }
    };

    @Override
    public void onCreate() {
        super.onCreate();
        IntentFilter iFilter = new IntentFilter(Intent.ACTION_BATTERY_CHANGED);
        registerReceiver(mBatteryInfoReceiver, iFilter);
        intervalUpdate = DBTools.getInt(INTERVAL_UPDATE, 10000);
        geocoder = new Geocoder(this, Locale.getDefault());
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
        Log.d(TAG, "startTracking");
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
        data.setBatteryLevel(battLevel);
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
        Log.d(TAG, "onConnected");
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
        Log.d(TAG, "startAlarmManager");
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
        Log.d(TAG, "cancelAlarmManager");

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
                dataModel.setBatteryLevel(battLevel);
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
}
