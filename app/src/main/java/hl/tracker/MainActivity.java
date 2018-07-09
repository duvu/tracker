package hl.tracker;

import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.location.Location;
import android.os.Bundle;
import android.os.IBinder;
import android.support.v4.content.ContextCompat;
import android.support.v4.content.LocalBroadcastManager;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.text.SpannableString;
import android.text.TextUtils;
import android.text.style.ForegroundColorSpan;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.TextView;
import android.widget.Toast;


public class MainActivity extends AppCompatActivity {

    private HLService mService;
    private boolean mBound = false;

    private TextView txtLocation;
    private TextView txtAddress;


    // The BroadcastReceiver used to listen from broadcasts from the service.
    private MyReceiver myReceiver;

    private final ServiceConnection mConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            Logger.d("going to connect");
            HLService.LocalBinder binder = (HLService.LocalBinder) service;
            mService = (HLService) binder.getService();
            mBound = true;
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            Logger.d("going to disconnect");
            mService = null;
            mBound = false;
        }
    };
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        myReceiver = new MyReceiver();
        // Check that the user hasn't revoked permissions by going to Settings.

        TextView instruction2 = (TextView) findViewById(R.id.instruction2);
        String txtStr = getString(R.string.using_this_imei) + " " + NetworkUtils.getGatewayId() + " " + getString(R.string.to_create_a_new_device);

        SpannableString txt = new SpannableString(txtStr);
        txt.setSpan(new ForegroundColorSpan(ContextCompat.getColor(this, R.color.colorTextWarn)), 0, txtStr.length(), 0);
        txt.setSpan(new ForegroundColorSpan(ContextCompat.getColor(this, R.color.colorPrimaryDark)), 17, 32, 0);
        instruction2.setText(txt, TextView.BufferType.SPANNABLE);


        txtLocation = (TextView) findViewById(R.id.current_location);
        txtAddress = (TextView) findViewById(R.id.current_address);
    }

    @Override
    protected void onStart() {
        super.onStart();
        bindService(new Intent(this, HLService.class), mConnection, BIND_AUTO_CREATE);
        mBound = true;

        makeServiceRunning();
    }

    @Override
    protected void onResume() {
        super.onResume();
        LocalBroadcastManager.getInstance(this).registerReceiver(myReceiver, new IntentFilter(HLService.ACTION_BROADCAST));
    }

    @Override
    protected void onPause() {
        LocalBroadcastManager.getInstance(this).unregisterReceiver(myReceiver);
        super.onPause();
    }

    @Override
    protected void onStop() {
        if (mBound) {
            unbindService(mConnection);
        }
        super.onStop();
    }
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        return super.onOptionsItemSelected(item);
    }



    private void makeServiceRunning() {
        Intent intent1 = new Intent(MainActivity.this, HLService.class);
        startService(intent1);
    }

    /**
     * Receiver for broadcasts sent by {@link HLService}.
     */
    private class MyReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            Location location = intent.getParcelableExtra(HLService.EXTRA_LOCATION);
            String address = intent.getStringExtra(HLService.EXTRA_ADDRESS);
            if (location != null) {
                txtLocation.setText(HLUtils.getLocationText(location));
            }
            if (!TextUtils.isEmpty(address)) {
                txtAddress.setText(address);
            }
        }
    }

}
