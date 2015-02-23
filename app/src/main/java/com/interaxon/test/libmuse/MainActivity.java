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
 *
 *  Refer to http://android.choosemuse.com/enumcom_1_1interaxon_1_1libmuse_1_1_eeg.html
 *  For details on eeg
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
    private long numWavePackets;
    private long lastTS;                    // for package time
    private long totalElapsedMS;            // how many elapsed MS, used for packet-counting

    class ConnectionListener extends MuseConnectionListener {

        final WeakReference<Activity> activityRef;

        ConnectionListener(final WeakReference<Activity> activityRef) {
            this.activityRef = activityRef;
        }

        ///XXX: optimize this here, figure out better pathway for this
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

                        TextView museVersionText = (TextView) findViewById(R.id.headset_version);
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
//            System.out.println( "Packet Type: " + String.valueOf(p.getPacketType()) );

            int packetSkipAmount = 4;

            switch (p.getPacketType()) {
                case ALPHA_ABSOLUTE:
                   if( numWavePackets % packetSkipAmount == 0 )
                        updateAlphaAbsolute(p.getValues());

                case BETA_ABSOLUTE:
                    if( numWavePackets % packetSkipAmount == 0 )
                        updateBetaAbsolute(p.getValues());
                    break;

                case DELTA_ABSOLUTE:
                    if( numWavePackets % packetSkipAmount == 0 )
                        updateDeltaAbsolute(p.getValues());
                    break;

                case GAMMA_ABSOLUTE:
                    if( numWavePackets % packetSkipAmount == 0 )
                        updateGammaAbsolute(p.getValues());
                    break;

                case THETA_ABSOLUTE:
                    if( numWavePackets % packetSkipAmount == 0 )
                        updateThetaAbsolute(p.getValues());
                    updatePacketInfo(p.getTimestamp());
                    break;

                case HORSESHOE:
                    if( numWavePackets % packetSkipAmount == 0 )
                        updateHorseshoe(p.getValues());
                    break;

                case BATTERY:
                    updateBattery(p.getValues());
                default:
                    Log.i("DataPacket ", "Received");
                    break;
            }
        }


        @Override
        public void receiveMuseArtifactPacket(MuseArtifactPacket p) {
            // we ignore this
            ///REMOVE
            /*
            if (p.getHeadbandOn() && p.getBlink()) {
                Log.i("Artifacts", "blink");
            }*/
        }


        private float generateFloatFromEEG( double eegValue ) {
            if( Float.isNaN(Eeg.TP9.ordinal()))
                return -1;
            else {
                String s = String.format("%6.2f", eegValue);
                return Float.valueOf(s);
            }
        }

        private void stuffOSCWaveData(String pattern, final ArrayList<Double> data) {
            oscAddressPattern = new String(pattern);

            oscWaveData[0] = generateFloatFromEEG(data.get(Eeg.TP9.ordinal()));
            oscWaveData[1] = generateFloatFromEEG(data.get(Eeg.FP1.ordinal()));
            oscWaveData[2] = generateFloatFromEEG(data.get(Eeg.FP2.ordinal()));
            oscWaveData[3] = generateFloatFromEEG(data.get(Eeg.TP10.ordinal()));
        }

        // Assumes OSC Data is already in oscWaveData global array, 4 elements, check stuffOSCData()
        private void updateWaveFields( int field1, int field2, int field3, int field4 ) {
            TextView elem1 = (TextView) findViewById(field1);
            TextView elem2 = (TextView) findViewById(field2);
            TextView elem3 = (TextView) findViewById(field3);
            TextView elem4 = (TextView) findViewById(field4);

            elem1.setText(String.format( "%6.2f", oscWaveData[0]));
            elem2.setText(String.format( "%6.2f", oscWaveData[1]));
            elem3.setText(String.format( "%6.2f", oscWaveData[2]));
            elem4.setText(String.format( "%6.2f", oscWaveData[3]));
        }

        private void updateAlphaAbsolute(final ArrayList<Double> data) {
            Activity activity = activityRef.get();
            if (activity != null) {
                activity.runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        // Extra wave patterns
                        stuffOSCWaveData("/muse/elements/alpha_absolute", data);

                        // update text fields with this EEG wave data
                        updateWaveFields(R.id.alpha_t9, R.id.alpha_fp1, R.id.alpha_fp2, R.id.alpha_t10);

                        // transmit OSC data
                        sendOSCWaveData();
                    }
                });
            }
        }

        private void updateBetaAbsolute(final ArrayList<Double> data) {
            Activity activity = activityRef.get();
            if (activity != null) {
                activity.runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        // Extra wave patterns
                        stuffOSCWaveData("/muse/elements/beta_absolute", data);

                        // update text fields with this EEG wave data
                        updateWaveFields(R.id.beta_t9, R.id.beta_fp1, R.id.beta_fp2, R.id.beta_t10);

                        // transmit OSC data
                        sendOSCWaveData();
                    }
                });
            }
        }

        private void updateDeltaAbsolute(final ArrayList<Double> data) {
            Activity activity = activityRef.get();
            if (activity != null) {
                activity.runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        // Extra wave patterns
                        stuffOSCWaveData("/muse/elements/delta_absolute", data);

                        // update text fields with this EEG wave data
                        updateWaveFields(R.id.delta_t9, R.id.delta_fp1, R.id.delta_fp2, R.id.delta_t10);

                        // transmit OSC data
                        sendOSCWaveData();
                    }
                });
            }
        }

        private void updateGammaAbsolute(final ArrayList<Double> data) {
            Activity activity = activityRef.get();
            if (activity != null) {
                activity.runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        // Extra wave patterns
                        stuffOSCWaveData("/muse/elements/gamma_absolute", data);

                        // update text fields with this EEG wave data
                        updateWaveFields(R.id.gamma_t9, R.id.gamma_fp1, R.id.gamma_fp2, R.id.gamma_t10);

                        // transmit OSC data
                        sendOSCWaveData();
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
                        // Extra wave patterns
                        stuffOSCWaveData("/muse/elements/theta_absolute", data);

                        // update text fields with this EEG wave data
                        updateWaveFields(R.id.theta_t9, R.id.theta_fp1, R.id.theta_fp2, R.id.theta_t10);

                        // transmit OSC data
                        sendOSCWaveData();
                    }
                });
            }
        }

        // Updates last packet speed, average packet speed, number of packets
        private void updatePacketInfo( long ts ) {
            Activity activity = activityRef.get();
            if (activity != null) {
                activity.runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        /*long ts = lastTS + 666;
                        //-- this is the first packet, so no averaging
                        if( lastTS == 0L ) {
                            lastTS = ts;
                            return;
                        }
                        */
                        numWavePackets++;
                        TextView nWavePackets = (TextView) findViewById(R.id.num_packets);
                        nWavePackets.setText(String.format( "%d", numWavePackets));

                        /*
                        long elapsedTS = ts-lastTS;
                        float elapsedSec = (float)elapsedTS/1000.0f;
                        totalElapsedMS= totalElapsedMS+ elapsedTS;

                        TextView lastWavePacket = (TextView) findViewById(R.id.last_wave_packet);

                        // this is crashing...
                        //lastWavePacket.setText(String.format( "%6.2f", elapsedSec));

                        //TextView averageWavePacket = (TextView) findViewById(R.id.average_wave_packet);


                        float avgWavePacketSpeed = totalElapsedMS/(numWavePackets-1);

                        //lastWavePacket.setText(String.format( "%6.2f", avgWavePacketSpeed));

                        lastTS = ts;
                        */
                    }
                });
            }
        }


        private void updateHorseshoe(final ArrayList<Double> data) {
            Activity activity = activityRef.get();
            if (activity != null) {
                activity.runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        String [] horseshoeStatus;
                        horseshoeStatus = new String[4];

                        // stuff the OSC data fields
                        for( int i = 0; i < 4; i++ )
                            horseshoeStatus[i] = getHorseshoeString( data.get(i) );

                            //oscWaveData[i] = generateFloatFromEEG(data.get(i));

                        TextView elem1 = (TextView) findViewById(R.id.horseshoe_1);
                        TextView elem2 = (TextView) findViewById(R.id.horseshoe_2);
                        TextView elem3 = (TextView) findViewById(R.id.horseshoe_3);
                        TextView elem4 = (TextView) findViewById(R.id.horseshoe_4);

                        //XXX: Add better features here, a percentage, change oscWaveData name
                        //NONE == 4.0
                        //CONNECTED == 1.0
//                        elem1.setText(String.format( "%6.2f", oscWaveData[0]));
//                        elem2.setText(String.format( "%6.2f", oscWaveData[1]));
//                        elem3.setText(String.format( "%6.2f", oscWaveData[2]));
//                        elem4.setText(String.format( "%6.2f", oscWaveData[3]));
                        elem1.setText(horseshoeStatus[0]);
                        elem2.setText(horseshoeStatus[1]);
                        elem3.setText(horseshoeStatus[2]);
                        elem4.setText(horseshoeStatus[3]);

                        // transmit OSC data
                        //sendOSCWaveData();
                    }
                });
            }
        }

        // 1.0 = GOOD, 2.0 = OKAY, 3.0 = BAD, 4.0 NONE
        private String getHorseshoeString(double horseshoeValue) {
            if( horseshoeValue == 4.0 )
                return "NONE";
            else if( horseshoeValue == 3.0 )
                return "BAD";
            else if( horseshoeValue == 2.0 )
                return "OKAY";
            else if( horseshoeValue == 1.0 )
                return "GOOD";
            else
                return String.format( "ERROR, value = %6.2f", horseshoeValue);

        }

        /*
        PACKET INFO:
            /muse/batt iiii

        sent every 10 seconds
        Position 1 = State of Charge, Divide this by 100 to get percentage of charge remaining, (e.g. 5367 is 53.67%) Range: 16 bit, 0-10000.
        Position 2 = Millivolts measured by Fuel Gauge, Range: 16bit, 3000-4200 mV.
        Position 3 = Millivolts measured by ADC, Range: 16bits, 3200-4200 mV. Values below 3350 are not reliable(they will flat line and stop falling) and you can consider the battery close to dead at that point(about 5 mins left).
        Position 4 = Temperature in degrees Celcius, signed integer, 1°C Resolution, range is -40 to +125 °C.
        */

        private void updateBattery(final ArrayList<Double> data) {
            Activity activity = activityRef.get();
            if (activity != null) {
                activity.runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        double batteryLife = data.get(0);
                        TextView batteryDisplay = (TextView) findViewById(R.id.battery_life);
                        batteryDisplay.setText(String.format("%6.0f", batteryLife) + "%");
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
        lastTS = 0L;
        numWavePackets = 0;
        totalElapsedMS = 0L;      ///XXX: not currently used

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
            Button pauseButton = (Button) findViewById(R.id.pause);

            dataTransmission = !dataTransmission;
            if (muse != null) {
                muse.enableDataTransmission(dataTransmission);

                if( dataTransmission )
                    pauseButton.setText("Pause");
                else
                    pauseButton.setText("Resume");
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

    ///XXX: rename sendOSCWaveData
    // Global variables oscAddressPattern and oscWaveData are stuffed, from other functions
    public void sendOSCWaveData(){
        new AsyncTask<Void, Void, String>(){

            @Override
            protected String doInBackground(Void... params) {

                boolean bSendOSCData = true;

                if( bSendOSCData  ) {
                    OscMessage oscM = new OscMessage(oscAddressPattern, new Object[0]);
                    for (int i = 0; i < 4; i++)
                        oscM.add(oscWaveData[i]);
                    OscP5.flush(oscM, thisLocation);
                }
                return "doing in background";
            }

            @Override
            protected void onPostExecute(String result) {
                //System.out.println("done in background");
            }

        }.execute();
    }

    ///XXX:CLEAN
    private void configure_library() {
        muse.registerConnectionListener(connectionListener);

        muse.registerDataListener(dataListener, MuseDataPacketType.ALPHA_ABSOLUTE);
        muse.registerDataListener(dataListener, MuseDataPacketType.BETA_ABSOLUTE);
        muse.registerDataListener(dataListener, MuseDataPacketType.DELTA_ABSOLUTE);
        muse.registerDataListener(dataListener, MuseDataPacketType.GAMMA_ABSOLUTE);
        muse.registerDataListener(dataListener, MuseDataPacketType.THETA_ABSOLUTE);

        muse.registerDataListener(dataListener, MuseDataPacketType.BATTERY);
        muse.registerDataListener(dataListener, MuseDataPacketType.HORSESHOE

        // blinks ,etc, not needed for now
        //muse.registerDataListener(dataListener, MuseDataPacketType.ARTIFACTS);
);

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
