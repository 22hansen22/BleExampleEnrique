package com.example.ja.bleexampleenrique;

import android.Manifest;
import android.annotation.TargetApi;
import android.app.Activity;
import android.app.AlertDialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanFilter;
import android.bluetooth.le.ScanResult;
import android.bluetooth.le.ScanSettings;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.speech.tts.TextToSpeech;
import android.support.v7.app.ActionBarActivity;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.TimeUnit;

@TargetApi(21)
public class MainActivity extends ActionBarActivity {
    private static final int PERMISSION_REQUEST_COARSE_LOCATION = 1;

    private BluetoothAdapter mBluetoothAdapter;
    private int REQUEST_ENABLE_BT = 1;
    private Handler mHandler;
    private static final long SCAN_PERIOD = 30000;
    private BluetoothLeScanner mLEScanner;
    private ScanSettings settings;
    private List<ScanFilter> filters;
    private BluetoothGatt mGatt;
    private BluetoothDevice mDevice;
    private boolean running=false;
    private float dd, dinit, goaldist, ttimeAlarm;
    private boolean first=true;
    private int stateT=0;
    private long tStart;
    Timer timer;
    TimerTask timerTask;
    final Handler handler = new Handler();
    private int count_vspeed, count_cadence, count_stride=0;
    private int sum_vspeed, sum_cadence, sum_stride=0;
    private String cadenceCheckBoxTag, strideCheckBoxTag, timeCheckBoxTag, goaldistTag, timeAlarmTag;
    CheckBox cadenceCheck, strideCheck, timeCheck;
    SharedPreferences mPrefs;
    EditText dist, timeAlarm;
    SharedPreferences.Editor mEditor;

    TextToSpeech t1;

    private int wh=0;

    // setup UI handler
    private final static int UPDATE_DEVICE = 0;

    private final Handler uiHandler = new Handler() {
        public void handleMessage(Message msg) {
            final int what = msg.what;
            wh=what;
            final String value = (String) msg.obj;
            switch (what) {
                case UPDATE_DEVICE:
                    updateDevice(value);
                    break;
                default:
                    updateValue(value);
                    break;
            }
        }
    };

    private void updateDevice(String devName) {
        TextView t = (TextView) findViewById(R.id.dev_type);
        t.setText(devName);
        t1.speak(devName+" connected" , TextToSpeech.QUEUE_FLUSH, null);
        Log.i("BLE",devName+" connected" );
    }

    private void updateValue(String value) {
        TextView t;
        switch(wh){
            case 1: //vspeed
                count_vspeed+=1;
                sum_vspeed+= Integer.parseInt(value);
                t = (TextView) findViewById(R.id.value_read1);
                t.setText(value);
                break;
            case 2: //cadence
                count_cadence+=1;
                sum_cadence+= Integer.parseInt(value);
                t = (TextView) findViewById(R.id.value_read2);
                t.setText(value);
                break;
            case 3: //vstride
                count_stride+=1;
                sum_stride+= Integer.parseInt(value);
                t = (TextView) findViewById(R.id.value_read3);
                t.setText(value);
                break;
            case 4: //distance
                t = (TextView) findViewById(R.id.value_read4);
                t.setText(Float.toString((float)Integer.parseInt(value) / 10));
                if (first) {
                    first=false;
                    dd = 0;
                    dinit=(float)Integer.parseInt(value) / 10;
                } else{
                    dd= ((float)Integer.parseInt(value) / 10)-dinit;
                }
                break;
        }

        if (running==true){
            if (dd>=goaldist*0.25 && dd<goaldist*0.5 && stateT==0){
                t1.speak("25 percent done" , TextToSpeech.QUEUE_FLUSH, null);
                stateT=1;
            }
            if (dd>=goaldist*0.5 && dd<goaldist*0.75 && stateT==1){
                t1.speak("50 percent done "+(goaldist-dd)+ " meters remaining" , TextToSpeech.QUEUE_FLUSH, null);
                stateT=2;
            }
            if (dd>=goaldist*0.75 && dd<goaldist && stateT==2){
                t1.speak("Almost done "+(goaldist-dd)+ " meters remaining" , TextToSpeech.QUEUE_FLUSH, null);
                stateT=3;
            }
            if (dd>=goaldist && stateT==3){
                double elapsedSeconds = (System.currentTimeMillis() - tStart) / 1000.0;
                t1.speak("Goal achieved in "+(int)(elapsedSeconds/60)+" minutes and "+(elapsedSeconds % 60)+" seconds" , TextToSpeech.QUEUE_FLUSH, null);
                stateT=4;
            }

        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        mHandler = new Handler();

        mPrefs = getSharedPreferences("label", 0);
        timeCheckBoxTag = mPrefs.getString("timeCheckBox", "0");
        cadenceCheckBoxTag = mPrefs.getString("cadenceCheckBox", "0");
        strideCheckBoxTag = mPrefs.getString("strideCheckBox", "0");
        cadenceCheck = (CheckBox) findViewById(R.id.cadenceCheck);
        strideCheck = (CheckBox) findViewById(R.id.strideCheck);
        timeCheck = (CheckBox) findViewById(R.id.timeCheck);
        mEditor = mPrefs.edit();

        //set values store in data for the check options
        if(timeCheckBoxTag.equals("0"))     timeCheck.setChecked(false);
        else                                timeCheck.setChecked(true);
        if(cadenceCheckBoxTag.equals("0"))  cadenceCheck.setChecked(false);
        else                                cadenceCheck.setChecked(true);
        if(strideCheckBoxTag.equals("0"))   strideCheck.setChecked(false);
        else                                strideCheck.setChecked(true);

        //if state of checkbox is changed save the new value in the app data
        timeCheck.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if(timeCheck.isChecked())       mEditor.putString("timeCheckBox", "1").commit();
                else                            mEditor.putString("timeCheckBox", "0").commit();
            }
        });

        cadenceCheck.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if(cadenceCheck.isChecked())    mEditor.putString("cadenceCheckBox", "1").commit();
                else                            mEditor.putString("cadenceCheckBox", "0").commit();

            }
        });

        strideCheck.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if(strideCheck.isChecked())     mEditor.putString("strideCheckBox", "1").commit();
                else                            mEditor.putString("strideCheckBox", "0").commit();

            }
        });


        t1=new TextToSpeech(getApplicationContext(), new TextToSpeech.OnInitListener() {
            @Override
            public void onInit(int status) {
                if(status != TextToSpeech.ERROR) {
                    t1.setLanguage(Locale.US);
                }
            }
        });

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            // Android M Permission check 
            if (this.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED){
                final AlertDialog.Builder builder = new AlertDialog.Builder(this);
                builder.setTitle("This app needs location access");
                builder.setMessage("Please grant location access so this app can detect beacons.");
                builder.setPositiveButton(android.R.string.ok, null);
                builder.setOnDismissListener(new DialogInterface.OnDismissListener() {
                    @Override
                    @TargetApi(Build.VERSION_CODES.M)
                    public void onDismiss(DialogInterface dialog) {
                        requestPermissions(new String[]{Manifest.permission.ACCESS_COARSE_LOCATION}, PERMISSION_REQUEST_COARSE_LOCATION);
                    }
                });
                builder.show();
            }
        }


        if (!getPackageManager().hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)) {
            Toast.makeText(this, "BLE Not Supported",
                    Toast.LENGTH_SHORT).show();
            finish();
        }
        final BluetoothManager bluetoothManager =
                (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
        mBluetoothAdapter = bluetoothManager.getAdapter();

        dist = (EditText) findViewById(R.id.etDist);
        goaldistTag = mPrefs.getString("goaldist", "3000");
        dist.setText(goaldistTag);

        goaldist=Float.parseFloat(dist.getText().toString());

        dist.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {

            }
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {

            }

            @Override
            public void afterTextChanged(Editable s) {
                goaldist=Float.parseFloat(dist.getText().toString());
                mEditor.putString("goaldist", dist.getText().toString()).commit();
            }
        });
        //goaldist=3000
        ///----------------------------------------------

        timeAlarm = (EditText) findViewById(R.id.etPeriodA);
        timeAlarmTag = mPrefs.getString("timeAlarm", "1");
        timeAlarm.setText(timeAlarmTag);

        ttimeAlarm=Float.parseFloat(dist.getText().toString());

        timeAlarm.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {

            }
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {

            }

            @Override
            public void afterTextChanged(Editable s) {
                ttimeAlarm=Float.parseFloat(dist.getText().toString());
                mEditor.putString("timeAlarm", timeAlarm.getText().toString()).commit();
            }
        });
        ///-------------------------------------

        Button b=(Button) findViewById(R.id.button);

        b.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                running=true;
                tStart = System.currentTimeMillis();
                startTimer();
                t1.speak(" Start running" , TextToSpeech.QUEUE_FLUSH, null);
            }
        });

    }

    @Override
    protected void onResume() {
        super.onResume();
        if (mBluetoothAdapter == null || !mBluetoothAdapter.isEnabled()) {
            Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT);
        } else {
            if (Build.VERSION.SDK_INT >= 21) {
                mLEScanner = mBluetoothAdapter.getBluetoothLeScanner();
                settings = new ScanSettings.Builder()
                        .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
                        .build();
                filters = new ArrayList<ScanFilter>();
            }
            scanLeDevice(true);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (mBluetoothAdapter != null && mBluetoothAdapter.isEnabled()) {
            scanLeDevice(false);
        }
    }

    @Override
    protected void onDestroy() {
        if (mGatt == null) {
            return;
        }
        mGatt.close();
        mGatt = null;
        super.onDestroy();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == REQUEST_ENABLE_BT) {
            if (resultCode == Activity.RESULT_CANCELED) {
                //Bluetooth not enabled.
                finish();
                return;
            }
        }
        super.onActivityResult(requestCode, resultCode, data);
    }

    private void scanLeDevice(final boolean enable) {
        if (enable) {
            mHandler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    if (Build.VERSION.SDK_INT < 21) {
                        mBluetoothAdapter.stopLeScan(mLeScanCallback);
                    } else {
                        mLEScanner.stopScan(mScanCallback);

                    }
                }
            }, SCAN_PERIOD);
            if (Build.VERSION.SDK_INT < 21) {
                mBluetoothAdapter.startLeScan(mLeScanCallback);
            } else {
                mLEScanner.startScan(filters, settings, mScanCallback);
            }
        } else {
            if (Build.VERSION.SDK_INT < 21) {
                mBluetoothAdapter.stopLeScan(mLeScanCallback);
            } else {
                mLEScanner.stopScan(mScanCallback);
            }
        }
    }


    private ScanCallback mScanCallback = new ScanCallback() {
        @Override
        public void onScanResult(int callbackType, ScanResult result) {
            Log.i("callbackType", String.valueOf(callbackType));
            String devicename = result.getDevice().getName();

            if (devicename != null) {
                if (devicename.startsWith("MilestonePod")) {
                    Log.i("result", "Device name: " + devicename);
                    Log.i("result", result.toString());
                    BluetoothDevice btDevice = result.getDevice();
                    connectToDevice(btDevice);
                }
            }

        }

        @Override
        public void onBatchScanResults(List<ScanResult> results) {
            for (ScanResult sr : results) {
                Log.i("ScanResult - Results", sr.toString());
            }
        }

        @Override
        public void onScanFailed(int errorCode) {
            Log.e("Scan Failed", "Error Code: " + errorCode);
        }
    };

    private BluetoothAdapter.LeScanCallback mLeScanCallback =
            new BluetoothAdapter.LeScanCallback() {
                @Override
                public void onLeScan(final BluetoothDevice device, int rssi,
                                     byte[] scanRecord) {
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            Log.i("onLeScan", device.toString());
                            connectToDevice(device);
                        }
                    });
                }
            };

    public void connectToDevice(BluetoothDevice device) {
        if (mGatt == null) {
            Log.d("connectToDevice", "connecting to device: " + device.toString());
            this.mDevice = device;
            mGatt = device.connectGatt(this, false, gattCallback);
            scanLeDevice(false);// will stop after first device detection
        }
    }

    private final BluetoothGattCallback gattCallback = new BluetoothGattCallback() {


        @Override
        public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
            Log.i("onConnectionStateChange", "Status: " + status);
            switch (newState) {
                case BluetoothProfile.STATE_CONNECTED:
                    Log.i("gattCallback", "STATE_CONNECTED");

                    //update UI
                    Message msg = Message.obtain();

                    String deviceName = gatt.getDevice().getName();
                    msg.obj = deviceName;
                    msg.what = 0;
                    msg.setTarget(uiHandler);
                    msg.sendToTarget();

                    gatt.discoverServices();
                    break;
                case BluetoothProfile.STATE_DISCONNECTED:
                    Log.e("gattCallback", "STATE_DISCONNECTED");
                    Log.i("gattCallback", "reconnecting...");
                    BluetoothDevice mDevice = gatt.getDevice();
                    mGatt = null;
                    connectToDevice(mDevice);
                    break;
                default:
                    Log.e("gattCallback", "STATE_OTHER");
            }

        }

        @Override
        public void onServicesDiscovered(BluetoothGatt gatt, int status) {
            mGatt = gatt;
            List<BluetoothGattService> services = gatt.getServices();
            Log.i("onServicesDiscovered", services.toString());
            BluetoothGattCharacteristic therm_char = services.get(2).getCharacteristics().get(0);

            for (BluetoothGattDescriptor descriptor : therm_char.getDescriptors()) {
                //descriptor.setValue(BluetoothGattDescriptor.ENABLE_INDICATION_VALUE);
                descriptor.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
                mGatt.writeDescriptor(descriptor);
            }

            //gatt.readCharacteristic(therm_char);
            gatt.setCharacteristicNotification(therm_char, true);

        }

        @Override
        public void onCharacteristicRead(BluetoothGatt gatt,
                                         BluetoothGattCharacteristic
                                                 characteristic, int status) {
            Log.i("onCharacteristicRead", characteristic.toString());
            byte[] value = characteristic.getValue();
            String v = new String(value);
            Log.i("onCharacteristicRead", "Value: " + v);
            //gatt.disconnect();
        }

        @Override
        public void onCharacteristicChanged(BluetoothGatt gatt,
                                            BluetoothGattCharacteristic
                                                    characteristic) {


            float vspeed = characteristic.getIntValue(BluetoothGattCharacteristic.FORMAT_UINT16, 1)/ 256.0f * 3.6f; // 1/256 m/s in km/h
            float vcadence = characteristic.getIntValue(BluetoothGattCharacteristic.FORMAT_UINT8, 3);
            float vstride = characteristic.getIntValue(BluetoothGattCharacteristic.FORMAT_UINT16, 4);
            float vdistance = characteristic.getIntValue(BluetoothGattCharacteristic.FORMAT_UINT32, 6);

            Log.i("vspeed ", Float.toString(vspeed));           // m/s
            Log.i("vcadence ", Float.toString(vcadence));         // reps/minute
            Log.i("vstride ", Float.toString(vstride));          // cms
            Log.i("vdistance ", Float.toString(vdistance));        // dm (1/10m)

            String deviceName = gatt.getDevice().getName();
            float []valuei=new float[4];
            valuei[0]=vspeed;
            valuei[1]=vcadence;
            valuei[2]=vstride;
            valuei[3]=vdistance;

            for (int i=0; i<4; i++){
                //update UI
                Message msg = Message.obtain();
                msg.obj = Float.toString(valuei[i]);
                msg.what = i+1;
                msg.setTarget(uiHandler);
                msg.sendToTarget();
            }

            //gatt.disconnect();
        }

    };

    public void startTimer() {
        timer = new Timer();
        initializeTimerTask();
        //schedule the timer, after the first 5000ms the TimerTask will run every 1 min
        TextView t = (TextView) findViewById(R.id.etPeriodA);
        timer.schedule(timerTask, (int)(Float.parseFloat(t.getText().toString())*60*1000), (int)(Float.parseFloat(t.getText().toString())*60*1000)); //
    }

    public void stoptimertask(View v) {
        if (timer != null) {
            timer.cancel();
            timer = null;
        }
    }

    public void initializeTimerTask() {
        timerTask = new TimerTask() {
            public void run() {
                handler.post(new Runnable() {
                    public void run() {
                        String phrase="";
                        phrase="Time elapsed "+ Math.round(TimeUnit.MILLISECONDS.toMinutes(((System.currentTimeMillis() - tStart)))) +" minutes";
                        phrase= phrase + ", distance "+dd;
                        //Toast.makeText(getApplicationContext(), "1 min elapsed", Toast.LENGTH_SHORT).show();
                        if(count_vspeed>0 && count_cadence>0 && count_stride>0) {
                            //phrase=phrase+ ",speed " + sum_vspeed / count_vspeed + "meters per second";

                            if(cadenceCheck.isChecked()) {
                                phrase = phrase + " ,cadence " + sum_cadence / count_cadence /*+ "strides per minute"*/;
                            }
                            if(strideCheck.isChecked()) {
                                phrase = phrase + " ,stride " + sum_stride / count_stride /*+ "centimeters"*/;
                            }
                        }
                        t1.speak(phrase, TextToSpeech.QUEUE_FLUSH, null);

                        //clear values of the variables to gather the averages for next period
                        count_vspeed=0; count_cadence=0;    count_stride=0;
                        sum_vspeed=0;   sum_cadence=0;      sum_stride=0;
                    }
                });
            }
        };
    }
}