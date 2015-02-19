/**
 * Example of using libmuse library on android.
 * Interaxon, Inc. 2015
 */

package com.interaxon.test.libmuse;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;

import android.app.Activity;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.AsyncTask;
import android.preference.EditTextPreference;
import android.preference.PreferenceActivity;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.TextView;


import com.interaxon.libmuse.Accelerometer;
import com.interaxon.libmuse.ConnectionState;
import com.interaxon.libmuse.Eeg;
import com.interaxon.libmuse.LibMuseVersion;
import com.interaxon.libmuse.Muse;
import com.interaxon.libmuse.MuseArtifactPacket;
import com.interaxon.libmuse.MuseConnectionListener;
import com.interaxon.libmuse.MuseConnectionPacket;
import com.interaxon.libmuse.MuseDataListener;
import com.interaxon.libmuse.MuseDataPacket;
import com.interaxon.libmuse.MuseDataPacketType;
import com.interaxon.libmuse.MuseManager;
import com.interaxon.libmuse.MusePreset;
import com.interaxon.libmuse.MuseVersion;

import netP5.NetAddress;
import netP5.NetInfo;
import oscP5.OscArgument;
import oscP5.OscMessage;
import oscP5.OscP5;

/**
 * In this simple example MainActivity implements 2 MuseHeadband listeners
 * and updates UI when data from Muse is received. Similarly you can implement
 * listers for other data or register same listener to listen for different type
 * of data.
 * For simplicity we create Listeners as inner classes of MainActivity. We pass
 * reference to MainActivity as we want listeners to update UI thread in this
 * example app.
 * You can also connect multiple muses to the same phone and register same
 * listener to listen for data from different muses. In this case you will
 * have to provide synchronization for data members you are using inside
 * your listener.
 *
 * Usage instructions:
 * 1. Enable bluetooth on your device
 * 2. Pair your device with muse
 * 3. Run this project
 * 4. Press Refresh. It should display all paired Muses in Spinner
 * 5. Make sure Muse headband is waiting for connection and press connect.
 * It may take up to 10 sec in some cases.
 * 6. You should see EEG and accelerometer data as well as connection status,
 * Version information and MuseElements (alpha, beta, theta, delta, gamma waves)
 * on the screen.
 */
public class MainActivity extends Activity implements OnClickListener {
    /**
     * Connection listener updates UI with new connection status and logs it.
     */
    private int PORT_IN = 12000;   // not used, could this be a problem??
    private int PORT_OUT = 5002;   // changes for each EEG device, need a prefs/setting somewhere
    private String SEND_TO_IP = "none";
    private NetAddress thisLocation;
    private OscP5 osc;

    private String oscAddressPattern;

    private float [] oscWaveData;           // stuff with 4 floats

    class ConnectionListener extends MuseConnectionListener {

        final WeakReference<Activity> activityRef;

        ConnectionListener(final WeakReference<Activity> activityRef) {
            this.activityRef = activityRef;
        }

        @Override
        public void receiveMuseConnectionPacket(MuseConnectionPacket p) {
            final ConnectionState current = p.getCurrentConnectionState();
            final String status = p.getPreviousConnectionState().toString() +
                         " -> " + current;
            final String full = "Muse " + p.getSource().getMacAddress() +
                                " " + status;
            Log.i("Muse Headband", full);
            Activity activity = activityRef.get();
            if (activity != null) {
                activity.runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        TextView statusText =
                                (TextView) findViewById(R.id.con_status);
                        statusText.setText(status);
                        TextView museVersionText =
                                (TextView) findViewById(R.id.version);
                        if (current == ConnectionState.CONNECTED) {
                            MuseVersion museVersion = muse.getMuseVersion();
                            String version = museVersion.getFirmwareType() +
                                 " - " + museVersion.getFirmwareVersion() +
                                 " - " + Integer.toString(
                                    museVersion.getProtocolVersion());
                            museVersionText.setText(version);
                        } else {
                            museVersionText.setText(R.string.undefined);
                        }
                    }
                });
            }
        }
    }

    /**
     * Data listener will be registered to listen for: Accelerometer,
     * Eeg and Relative Alpha bandpower packets. In all cases we will
     * update UI with new values.
     * We also will log message if Artifact packets contains "blink" flag.
     */
    class DataListener extends MuseDataListener {

        final WeakReference<Activity> activityRef;

        DataListener(final WeakReference<Activity> activityRef) {
            this.activityRef = activityRef;
        }



        @Override
        public void receiveMuseDataPacket(MuseDataPacket p) {
            //System.out.println( "Packet Type: " + String.valueOf(p.getPacketType()) );

            switch (p.getPacketType()) {
                case EEG:
                    //System.out.println("EEG Packet");
                    updateEeg(p.getValues());
                    break;

                case BETA_ABSOLUTE:
                    updateBetaAbsolute(p.getValues());
                    break;

                case THETA_ABSOLUTE:
                    updateThetaAbsolute(p.getValues());
                    break;
                default:
                    break;
            }
        }

        @Override
        public void receiveMuseArtifactPacket(MuseArtifactPacket p) {
            if (p.getHeadbandOn() && p.getBlink()) {
                Log.i("Artifacts", "blink");
            }
        }

        private void updateAccelerometer(final ArrayList<Double> data) {
            Activity activity = activityRef.get();
            if (activity != null) {
                activity.runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        // we pulled these out
                        /*
                        TextView acc_x = (TextView) findViewById(R.id.acc_x);
                        TextView acc_y = (TextView) findViewById(R.id.acc_y);
                        TextView acc_z = (TextView) findViewById(R.id.acc_z);
                        acc_x.setText(String.format(
                            "%6.2f", data.get(Accelerometer.FORWARD_BACKWARD.ordinal())));
                        acc_y.setText(String.format(
                            "%6.2f", data.get(Accelerometer.UP_DOWN.ordinal())));
                        acc_z.setText(String.format(
                            "%6.2f", data.get(Accelerometer.LEFT_RIGHT.ordinal())));
                           */
                    }
                });
            }
        }

        private void updateEeg(final ArrayList<Double> data) {
            Activity activity = activityRef.get();
            if (activity != null) {
                activity.runOnUiThread(new Runnable() {
                    @Override
                    public void run() {

                         TextView tp9 = (TextView) findViewById(R.id.eeg_tp9);
                         TextView fp1 = (TextView) findViewById(R.id.eeg_fp1);
                         TextView fp2 = (TextView) findViewById(R.id.eeg_fp2);
                         TextView tp10 = (TextView) findViewById(R.id.eeg_tp10);
                         tp9.setText(String.format(
                            "%6.2f", data.get(Eeg.TP9.ordinal())));
                         fp1.setText(String.format(
                            "%6.2f", data.get(Eeg.FP1.ordinal())));
                         fp2.setText(String.format(
                            "%6.2f", data.get(Eeg.FP2.ordinal())));
                         tp10.setText(String.format(
                            "%6.2f", data.get(Eeg.TP10.ordinal())));
                    }
                });
            }
        }

        private float generateFloatFromEEG( double eegValue ) {
            if( Float.isNaN(Eeg.TP9.ordinal()))
                return -1;
            else {
                String s = String.format("%6.2f", eegValue);
                return Float.valueOf(s);
            }
        }
        private void updateBetaAbsolute(final ArrayList<Double> data) {
            Activity activity = activityRef.get();
            if (activity != null) {
                activity.runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        oscAddressPattern = "/muse/elements/beta_absolute";
                        for (int i = 0; i < 4; i++) {
                            oscWaveData[0] = generateFloatFromEEG(data.get(Eeg.TP9.ordinal()));
                            oscWaveData[1] = generateFloatFromEEG(data.get(Eeg.FP1.ordinal()));
                            oscWaveData[2] = generateFloatFromEEG(data.get(Eeg.FP2.ordinal()));
                            oscWaveData[3] = generateFloatFromEEG(data.get(Eeg.TP10.ordinal()));
                        }

                        sendOSCWaveData();
                        TextView elem1 = (TextView) findViewById(R.id.elem1);
                        TextView elem2 = (TextView) findViewById(R.id.elem2);
                        TextView elem3 = (TextView) findViewById(R.id.elem3);
                        TextView elem4 = (TextView) findViewById(R.id.elem4);
                        elem1.setText(String.format(
                                "%6.2f", data.get(Eeg.TP9.ordinal())));
                        elem2.setText(String.format(
                                "%6.2f", data.get(Eeg.FP1.ordinal())));
                        elem3.setText(String.format(
                                "%6.2f", data.get(Eeg.FP2.ordinal())));
                        elem4.setText(String.format(
                                "%6.2f", data.get(Eeg.TP10.ordinal())));
                    }
                });
            }
        }

        private void updateThetaAbsolute(final ArrayList<Double> data) {
            Activity activity = activityRef.get();
            if (activity != null) {
                activity.runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        oscAddressPattern = "/muse/elements/theta_absolute";
                        for (int i = 0; i < 4; i++) {
                            oscWaveData[0] = generateFloatFromEEG(data.get(Eeg.TP9.ordinal()));
                            oscWaveData[1] = generateFloatFromEEG(data.get(Eeg.FP1.ordinal()));
                            oscWaveData[2] = generateFloatFromEEG(data.get(Eeg.FP2.ordinal()));
                            oscWaveData[3] = generateFloatFromEEG(data.get(Eeg.TP10.ordinal()));
                        }

                        sendOSCWaveData();
                    }
                });
            }
        }
    }

    private Muse muse = null;
    private ConnectionListener connectionListener = null;
    private DataListener dataListener = null;
    private boolean dataTransmission = true;

    public MainActivity() {
        // Create listeners and pass reference to activity to them
        WeakReference<Activity> weakActivity =
                                new WeakReference<Activity>(this);
        connectionListener = new ConnectionListener(weakActivity);
        dataListener = new DataListener(weakActivity);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        // uncommment to revert to defaults
        //clearPrefs();

        oscWaveData = new float[4];

        // find way to hide popup keyboard
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        Button refreshButton = (Button) findViewById(R.id.refresh);
        refreshButton.setOnClickListener(this);
        Button connectButton = (Button) findViewById(R.id.connect);
        connectButton.setOnClickListener(this);
        Button disconnectButton = (Button) findViewById(R.id.disconnect);
        disconnectButton.setOnClickListener(this);
        Button pauseButton = (Button) findViewById(R.id.pause);
        pauseButton.setOnClickListener(this);

        Button saveButton = (Button) findViewById(R.id.save);
        saveButton.setOnClickListener(this);
        Log.i("Muse Headband", "libmuse version=" + LibMuseVersion.SDK_VERSION);

        readPrefs();
    }

    @Override
    public void onClick(View v) {

        Spinner musesSpinner = (Spinner) findViewById(R.id.muses_spinner);
        if (v.getId() == R.id.refresh) {
            MuseManager.refreshPairedMuses();
            List<Muse> pairedMuses = MuseManager.getPairedMuses();
            List<String> spinnerItems = new ArrayList<String>();
            for (Muse m: pairedMuses) {
                String dev_id = m.getName() + "-" + m.getMacAddress();
                Log.i("Muse Headband", dev_id);
                spinnerItems.add(dev_id);
            }
            ArrayAdapter<String> adapterArray = new ArrayAdapter<String> (
                    this, android.R.layout.simple_spinner_item, spinnerItems);
            musesSpinner.setAdapter(adapterArray);

            EditText portEditText = (EditText) findViewById(R.id.ti_port);
            portEditText.setEnabled(true);

            EditText ipEditText = (EditText) findViewById(R.id.ti_ip);
            ipEditText.setEnabled(true);
        }
        else if (v.getId() == R.id.connect) {
            List<Muse> pairedMuses = MuseManager.getPairedMuses();
            if (pairedMuses.size() < 1 ||
                musesSpinner.getAdapter().getCount() < 1) {
                Log.w("Muse Headband", "There is nothing to connect to");
            }
            else {
                muse = pairedMuses.get(musesSpinner.getSelectedItemPosition());
                ConnectionState state = muse.getConnectionState();
                if (state == ConnectionState.CONNECTED ||
                    state == ConnectionState.CONNECTING) {
                    Log.w("Muse Headband", "doesn't make sense to connect second time to the same muse");
                    return;
                }
                configure_library();
                /**
                 * In most cases libmuse native library takes care about
                 * exceptions and recovery mechanism, but native code still
                 * may throw in some unexpected situations (like bad bluetooth
                 * connection). Print all exceptions here.
                 */
                try {
                    muse.runAsynchronously();

                    // turn off editable text fields, store OSC values here
                    EditText portEditText = (EditText) findViewById(R.id.ti_port);
                    String portString = portEditText.getText().toString();
                    //System.out.println("PORT: " + portString );
                    PORT_OUT = Integer.valueOf(portString);
                    PORT_IN = PORT_OUT + 7000;      // guarantees unique

                    EditText ipEditText = (EditText) findViewById(R.id.ti_ip);
                    SEND_TO_IP = ipEditText.getText().toString();


                    portEditText.setEnabled(false);
                    ipEditText.setEnabled(false);
                    System.out.println("IP: " + SEND_TO_IP );
                    System.out.println("PORT_IN: " + String.valueOf(PORT_IN) );
                    System.out.println("PORT_OUT: " + String.valueOf(PORT_OUT) );

                    osc = new OscP5(this,PORT_IN);
                    thisLocation = new NetAddress(SEND_TO_IP,PORT_OUT);
                } catch (Exception e) {
                    Log.e("Muse Headband", e.toString());
                }
            }
        }
        else if (v.getId() == R.id.disconnect) {
            if (muse != null) {
                muse.disconnect(true);
            }

            EditText portEditText = (EditText) findViewById(R.id.ti_port);
            portEditText.setEnabled(true);

            EditText ipEditText = (EditText) findViewById(R.id.ti_ip);
            ipEditText.setEnabled(true);
        }
        else if (v.getId() == R.id.save) {
            savePrefs();
        }
        else if (v.getId() == R.id.pause) {

            dataTransmission = !dataTransmission;
            if (muse != null) {
                muse.enableDataTransmission(dataTransmission);
            }
        }
    }

    //-- default preferences
    public void clearPrefs() {
        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(this);
        SharedPreferences.Editor editor = preferences.edit();
        editor.putString("IP_ADDRESS","10.0.0.0");
        editor.putString("PORT_NUM","5000");
        editor.apply();
    }

    public void readPrefs() {
        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(this);
        String ipAddress = preferences.getString("IP_ADDRESS","");
        String portNum = preferences.getString("PORT_NUM","");

        EditText portEditText = (EditText) findViewById(R.id.ti_port);
        portEditText.setText(portNum);

        EditText ipEditText = (EditText) findViewById(R.id.ti_ip);
        ipEditText.setText(ipAddress);
    }

    public void savePrefs() {

        EditText portEditText = (EditText) findViewById(R.id.ti_port);
        String portString = portEditText.getText().toString();

        EditText ipEditText = (EditText) findViewById(R.id.ti_ip);
        String ipString = ipEditText.getText().toString();

        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(this);
        SharedPreferences.Editor editor = preferences.edit();
        editor.putString("IP_ADDRESS",ipString);
        editor.putString("PORT_NUM",portString);
        editor.apply();
    }

    // Global variables oscAddressPattern and oscWaveData are stuffed, from other functions
    public void sendOSCWaveData(){
        new AsyncTask<Void, Void, String>(){

            @Override
            protected String doInBackground(Void... params) {
              // System.out.println("sending connect message: " + String.valueOf(oscWaveData) );
               // OscMessage oscM = new OscMessage("/test",new Object[0]);
                OscMessage oscM = new OscMessage(oscAddressPattern,new Object[0]);
                for( int i = 0; i < 4; i++ )
                    oscM.add(oscWaveData[i]);
                OscP5.flush(oscM,thisLocation);
                //OscP5.flush(m,new NetAddress("255.255.255.255",PORT_OUT));
                return "doing in background";
            }

            @Override
            protected void onPostExecute(String result) {
                //System.out.println("done in background");
            }

        }.execute();
    }


    private void configure_library() {
        muse.registerConnectionListener(connectionListener);

        muse.registerDataListener(dataListener,
                                  MuseDataPacketType.BETA_ABSOLUTE);
        muse.registerDataListener(dataListener,
                MuseDataPacketType.THETA_ABSOLUTE);
        muse.registerDataListener(dataListener,
                                  MuseDataPacketType.ARTIFACTS);

        muse.setPreset(MusePreset.PRESET_14);
        muse.enableDataTransmission(dataTransmission);
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
        if (id == R.id.action_settings) {
            return true;
        }
        return super.onOptionsItemSelected(item);
    }
}