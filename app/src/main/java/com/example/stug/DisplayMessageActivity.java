package com.example.stug;

import android.content.Context;
import android.content.Intent;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.icu.util.Calendar;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Build;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.os.Handler;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.provider.Settings;
import android.support.wearable.activity.WearableActivity;
import android.util.Log;
import android.view.View;
import android.view.WindowManager;
import android.widget.TextView;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;

public class DisplayMessageActivity extends WearableActivity {

    public Context context;

    String imudata, hrdata, resultdata;
    SensorManager mSensorManager;
    Sensor mSensor;
    Sensor gyroSensor;
    Sensor hrSensor;
    SensorEventListener mListener;

    String x_value = "00";
    String y_value = "00";
    String z_value = "00";
    float hr_value = 0;
    String gx_value = "00";
    String gy_value = "00";
    String gz_value = "00";
    String senseTime;

    float gx, gy, gz = 0;
    int steps = 0;
    float time = 0;
    float hrtime = 0, imutime = 0;
    float leftStepMean = 0, rightStepMean = 0, ratioTSLTSR = 0;
    public ArrayList<Step> maxSteps = new ArrayList<Step>();
    File imufile = null;
    File hrfile = null;
    final String pTAG = "ProcesssTUGData";

    //MQTT Client
    public MQTTClient myClient;

    Vibrator vibrator; //= (Vibrator) getSystemService(VIBRATOR_SERVICE);

    Handler startHandler = new Handler();

    Runnable startRun = new Runnable() {
        @Override
        public void run() {
            mSensorManager.registerListener(mListener, mSensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER), 10000);
            mSensorManager.registerListener(mListener, mSensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE), 10000);
            vibrator.vibrate(VibrationEffect.createOneShot(2000, 250));
        }
    };

    Calendar c = Calendar.getInstance();
    SimpleDateFormat df = new SimpleDateFormat("yyMMddHHmm");
    String startTime = df.format(c.getTime());

    private String android_id; //= Settings.Secure.getString(this.getContentResolver(), Settings.Secure.ANDROID_ID);

    /* ***********************************************
        Data structure class definition
       ***********************************************
     */

    public class IMUData {
        ArrayList<Float> timeStamp = new ArrayList<Float>();
        ArrayList<Float> relativeTime = new ArrayList<Float>();
        ArrayList<Float> xAcc = new ArrayList<Float>();
        ArrayList<Float> yAcc = new ArrayList<Float>();
        ArrayList<Float> zAcc = new ArrayList<Float>();
        ArrayList<Float> xGy = new ArrayList<Float>();
        ArrayList<Float> yGy = new ArrayList<Float>();
        ArrayList<Float> zGy = new ArrayList<Float>();
    }

    public class HRData {
        ArrayList<Float> hrts = new ArrayList<Float>();
        ArrayList<Float> hr = new ArrayList<Float>();

    }

    public class Step {
        public float timecount;
        public float gzval;
        public float wmval;
        String stepType;

        public Step(float tcount, float gzcount, float wmcount, String step) {
            timecount = tcount;
            gzval = gzcount;
            wmval = wmcount;
            stepType=step;
        }
    }

    public IMUData IMU = new IMUData();
    public HRData HR = new HRData();

    private TextView mTextCount;
    private TextView mHRData;
    private TextView mTextbutton;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        android_id = Settings.Secure.getString(this.getContentResolver(), Settings.Secure.ANDROID_ID);
        context = getApplicationContext();
        imufile = new File(getExternalFilesDir(null), Build.MODEL + "_STUG_IMU_" + startTime + ".txt");
        writeheader(imufile.toString());
        hrfile = new File(getExternalFilesDir(null), Build.MODEL + "_STUG_HR_" + startTime + ".txt");
        writeheader(hrfile.toString());

        //initialize client
        myClient = new MQTTClient(this.getApplicationContext());
    } // end of onCreate()

    private void writeheader(String file) {
        String print_acc_header = "time,AX,AY,AZ,GX,GY,GZ";
        String print_hr_header = "time,HeartRate";
        byte[] header;
        try {
            FileOutputStream os = new FileOutputStream(file, true);
            if (file.contains("IMU") == true) {
                //ACC file, write ACC file header
                header = print_acc_header.getBytes();
            } else {
                //HR file, write HR file header
                header = print_hr_header.getBytes();
            }
            String newline = "\n";
            byte[] nl = newline.getBytes();

            os.write(header);
            os.write(nl);
            os.close();
        } catch (IOException e) {
            Log.w("ExternalStorage", "Error writing " + file, e);
        }
    }

    /* ***********************************************
        Start onResume()
       ***********************************************
     */

    @Override
    protected void onResume() {
        super.onResume();
        setContentView(R.layout.activity_display_message);

        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        mTextCount = (TextView) findViewById(R.id.countDown);
        mHRData = (TextView) findViewById(R.id.hrData);
        mTextbutton = (TextView) findViewById(R.id.button1);
        mTextbutton.setClickable(false);
        new CountDownTimer(15000, 1000) {

            public void onTick(long millisUntilFinished) {
                mTextCount.setText("" + millisUntilFinished / 1000);
            }

            public void onFinish() {
                mTextbutton.setClickable(true);
            }
        }.start();

        vibrator = (Vibrator) getSystemService(VIBRATOR_SERVICE);

        vibrator.vibrate(VibrationEffect.createOneShot(2000, 250));

        //////////////////////////////////
        // Enables Always-on
        setAmbientEnabled();
        /////////////////////////////////

        mSensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        mSensor = mSensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        gyroSensor = mSensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE);
        hrSensor = mSensorManager.getDefaultSensor(Sensor.TYPE_HEART_RATE);

        startHandler.postDelayed(startRun, 15000);

        mListener = new SensorEventListener() {
            @Override
            public void onAccuracyChanged(Sensor arg0, int arg1) {
            }

            @Override
            public void onSensorChanged(SensorEvent event) {
                Sensor sensor = event.sensor;

                if (sensor.getType() == Sensor.TYPE_ACCELEROMETER) {

                    float time = event.timestamp;

                    float x = event.values[0];
                    float y = event.values[1];
                    float z = event.values[2];

                    //String eventTime = String.format(time);
                    x_value = String.format("%.2f", x);
                    y_value = String.format("%.2f", y);
                    z_value = String.format("%.2f", z);

                    senseTime = String.valueOf(time);

                    // Input IMU data into arraylist IMU

                    IMU.timeStamp.add(time);
                    IMU.relativeTime.add(imutime);
                    IMU.xAcc.add(x);
                    IMU.yAcc.add(y);
                    IMU.zAcc.add(z);
                    IMU.xGy.add(gx);
                    IMU.yGy.add(gy);
                    IMU.zGy.add(gz);
                    imutime = ++imutime;

                } else if (sensor.getType() == Sensor.TYPE_GYROSCOPE) {

                    gx = event.values[0];
                    gy = event.values[1];
                    gz = event.values[2];

                    gx_value = String.format("%.2f", gx);
                    gy_value = String.format("%.2f", gy);
                    gz_value = String.format("%.2f", gz);

                } else if (event.sensor.getType() == Sensor.TYPE_HEART_RATE) {

                    String msg = "" + (int) event.values[0];
                    hr_value = event.values[0];
                    mHRData.setText(msg);
                    HR.hrts.add(hrtime);
                    HR.hr.add(hr_value);
                    hrtime = ++hrtime;
                }
            } // end of onSensorChanged()
        }; // end of SensorEventListener()

        mSensorManager.registerListener(mListener, mSensorManager.getDefaultSensor(Sensor.TYPE_HEART_RATE), SensorManager.SENSOR_DELAY_FASTEST);

    } // end of onResume()


    protected void onPause() {
        super.onPause();
        mSensorManager.unregisterListener(mListener);

        startHandler.removeCallbacks(startRun);

        String comma = ",";
        String newline = "\n";
        byte[] com = comma.getBytes();
        byte[] nl = newline.getBytes();

        try {
            FileOutputStream imuf = new FileOutputStream(imufile, true);
            int size = IMU.timeStamp.size();
            for (int i = 0; i < size; i++) {

                byte[] ts = String.valueOf(IMU.relativeTime.get(i)).getBytes();
                byte[] xacc = String.format("%.2f", IMU.xAcc.get(i)).getBytes();
                byte[] yacc = String.format("%.2f", IMU.yAcc.get(i)).getBytes();
                byte[] zacc = String.format("%.2f", IMU.zAcc.get(i)).getBytes();
                byte[] xgy = String.format("%.2f", IMU.xGy.get(i)).getBytes();
                byte[] ygy = String.format("%.2f", IMU.yGy.get(i)).getBytes();
                byte[] zgy = String.format("%.2f", IMU.zGy.get(i)).getBytes();
                imuf.write(ts);
                imuf.write(com);
                imuf.write(xacc);
                imuf.write(com);
                imuf.write(yacc);
                imuf.write(com);
                imuf.write(zacc);
                imuf.write(com);
                imuf.write(xgy);
                imuf.write(com);
                imuf.write(ygy);
                imuf.write(com);
                imuf.write(zgy);
                imuf.write(nl);
            }

        } catch (
                IOException e) {
            Log.w("ExternalStorage", "Error writing " + imufile, e);
        }

        try {
            FileOutputStream ftest = new FileOutputStream(hrfile, true);
            int size = HR.hrts.size();
            for (int i = 0; i < size; i++) {

                byte[] ts = String.valueOf(HR.hrts.get(i)).getBytes();
                byte[] hrdat = String.format("%.2f", HR.hr.get(i)).getBytes();

                ftest.write(ts);
                ftest.write(com);
                ftest.write(hrdat);
                ftest.write(nl);
            }

        } catch (
                IOException e) {
            Log.e("ExternalStorage", "Error writing " + hrfile, e);
        }

        final File resultfile = new File(getExternalFilesDir(null), Build.MODEL + "_STUG_RESULT_" + startTime + ".txt");

        String total = "Total STUG Time: " + (((float) IMU.timeStamp.size() - 50) / 100) + " Total Steps: " + maxSteps.size();//50 is to account to for delay in sit&press stop button

        try {
            FileOutputStream dat = new FileOutputStream(resultfile, true);

            dat.write(total.getBytes());
            dat.write(nl);

            for (int count = 0; count < maxSteps.size(); count++) {
                String steps = (count +1)+" "+maxSteps.get(count).stepType+" Step,"+ " Time: " + maxSteps.get(count).timecount;
                dat.write(steps.getBytes());
                dat.write(nl);
            }
            String stepMean = "Left Step Mean :" + String.format("%.3f", leftStepMean) + " Right Step Mean :" + String.format("%.3f", rightStepMean) + " TSL/TSR " + String.format("%.3f", ratioTSLTSR);
            dat.write(stepMean.getBytes());
            dat.write(nl);
        } catch (IOException e) {
            Log.e("ExternalStorage", "Error writing " + resultfile, e);
        }

        imudata = "/storage/emulated/0/Android/data/com.example.stug/files/" + Build.MODEL + "_STUG_IMU_" + startTime + ".txt";
        hrdata = "/storage/emulated/0/Android/data/com.example.stug/files/" + Build.MODEL + "_STUG_HR_" + startTime + ".txt";
        resultdata = "/storage/emulated/0/Android/data/com.example.stug/files/" + Build.MODEL + "_STUG_RESULT_" + startTime + ".txt";

        uploadData(String.valueOf(((float) IMU.timeStamp.size() - 50) / 100), String.valueOf(maxSteps.size()), String.format("%.3f", ratioTSLTSR));

    } // end of onPause()

    public void uploadData(String totalTime, String totalSteps, String stepRatio) {

        if (isOnline(this)) {

            //upload to MQTT Server
            if(myClient != null) {
                myClient.publishMessage(totalTime, "UAHSMARTWATCH/STUG/TIME/");
                myClient.publishMessage(totalSteps, "UAHSMARTWATCH/STUG/STEPS/");
                myClient.publishMessage(stepRatio, "UAHSMARTWATCH/STUG/TSLTSR/");
            }

            String path = getExternalFilesDir(null).getAbsolutePath();

            File directory = null;
            try {
                directory = new File(path);
                Log.i("FtpActivity", "dir = " + directory);
            } catch (Exception e) {
                Log.i("FtpActivity", "Uri e = " + e);
            }
            File[] files = directory.listFiles();

            //upload to FTP Server
            connectAndUpload(files);

        } else {
            Log.i("FtpActivity", "Please check your internet connection!");
        }
    }

    private boolean isOnline(Context context) {
        ConnectivityManager cm = (ConnectivityManager) context
                .getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo netInfo = cm.getActiveNetworkInfo();
        if (netInfo != null && netInfo.isConnected()) {
            return true;
        }
        return false;
    }

    private void connectAndUpload(File[] files) {

        new Thread(new Runnable() {
            public void run() {
                final String host = "ftp.drivehq.com";
                final String TAG = "FtpActivity";
                final String username = "talksuresh";
                final String password = "UAHThesis2022";

                FTPClientFunctions ftpclient = new FTPClientFunctions();

                boolean status = ftpclient.ftpConnect(host, username, password, 21);
                if (status == true) {
                    Log.i(TAG, "FTP Connection Success");
                    if (files != null) {
                        for (int i = 0; i < files.length; i++) {
                            String filename = files[i].getName();
                            Log.d(TAG, "Uploading the file " + filename);
                            status = ftpclient.ftpUpload(filename, filename, context);

                            if (status == true) {
                                Log.i(TAG, "Upload success " + filename);
                            } else {
                                Log.e(TAG, "Upload failed " + filename);
                                return;
                            }
                            Log.d(TAG, "Delete file from local storage " + filename);
                            files[i].delete();
                        }
                    }
                } else {
                    Log.e(TAG, "Connection failed");
                }
            }
        }).start();
    }

    public int ProcessData() {
        int count = 0;
        int size = 0;
        int numberOfSteps = 0, numberOfLeftSteps = 0, numberOfRightSteps = 0; // number of steps

        // Store Time
        size = IMU.timeStamp.size();
        float[] timecount = new float[size];
        for (count = 1; count < size; count++) {
            timecount[count] = IMU.relativeTime.get(count);
            //Log.d(pTAG, "time " + timecount[count]);
        }

        // Store Acceleration magnitude
        size = IMU.xAcc.size();
        float accx = 0, accy = 0, accz = 0;
        float[] wm = new float[size];
        for (count = 0; count < size; count++) {
            accx = IMU.xAcc.get(count);
            accy = IMU.yAcc.get(count);
            accz = IMU.zAcc.get(count);
            wm[count] = (float) ((Math.sqrt(accx * accx + accy * accy + accz * accz)) - 9.81); //Taking Gravity into account
            //Log.d(pTAG, "Acc " + wm[count]);
        }

        // store zGy values
        size = IMU.zGy.size();
        float[] gz = new float[size];
        for (count = 0; count < size; count++) {
            gz[count] = IMU.zGy.get(count);
            //Log.d(pTAG, "zGy " + gz[count]);
        }

        // store xGy+yGy values
        float[] gxy = new float[size];
        for (count = 0; count < size; count++) {
            gxy[count] = IMU.xGy.get(count) + IMU.yGy.get(count);
            //Log.d(pTAG, "gxy " + gxy[count]);
        }

        //Find gxyPeakValley and its zerocrossing values
        float gxyPeakValley = 0;
        int gxyNegZeroCrossing = 0;
        int gxyPosZeroCrossingT = 0;
        int gxyNegZeroCrossingT = 0;
        boolean ascend = false;

        for (count = 0; count < size - 1; count++) {
            if ((gxy[count] >= 0) && (gxy[count + 1] < 0)) {
                gxyNegZeroCrossing = count + 1;
                //Log.d(pTAG,  "gxyNegZeroCrossingT "+timecount[gxyNegZeroCrossing]+" gxyval "+gxy[gxyNegZeroCrossing]+" count "+gxyNegZeroCrossing);
            }
            if (gxy[count] < gxyPeakValley) {
                gxyPeakValley = gxy[count];
                gxyNegZeroCrossingT = gxyNegZeroCrossing;
                ascend = true;
                //Log.d(pTAG," gxyNegZeroCrossingT "+timecount[gxyNegZeroCrossing]+" gxyPeakval "+gxyPeakValley);
            }
            if ((ascend) && (gxy[count] <= 0) && (gxy[count + 1] > 0)) {
                gxyPosZeroCrossingT = count + 1;
                ascend = false;
                //Log.d(pTAG," gxyPosZeroCrossingT "+timecount[gxyPosZeroCrossingT]+" gxyval "+gxy[gxyPosZeroCrossingT]+" count "+gxyPosZeroCrossingT);
            }
        }
        Log.d(pTAG, "gxy Peak Valley " + gxyPeakValley + " gxyNegZeroCrossingT " + timecount[gxyNegZeroCrossingT] + " gxyPosZeroCrossingT " + timecount[gxyPosZeroCrossingT] + " negCount " + gxyNegZeroCrossingT + " posCount " + gxyPosZeroCrossingT);

        //Find gz zerocrossing values
        boolean[] zerogz = new boolean[size];
        for (count = 0; count < size - 1; count++) {
            if (((gz[count] >= 0) && gz[count + 1] < 0) || ((gz[count] <= 0) && (gz[count + 1] > 0))) {
                zerogz[count + 1] = true;
                //Log.d(pTAG,"Zero Crossing "+timecount[count+1]+" gzval "+gz[count+1]+" zerogz count "+(count+1));
            }
        }

        //Find peak Acc Magnitude values
        if (size > 1) {
            if (wm[0] < wm[1]) {
                ascend = true;
            }
        }

        boolean[] peakwm = new boolean[IMU.xAcc.size()];
        for (count = 0; count < size - 5; count++) {
            if ((ascend) && (wm[count] > 0.2) && (wm[count + 1] < wm[count]) && (wm[count + 5] < wm[count])) {//prevent small/negligible spikes
                peakwm[count] = true;
                //Log.d(pTAG,"Peak Value "+timecount[count]+" wm "+wm[count]);
                ascend = false;
            } else if ((!ascend) && (((wm[count + 1] - wm[count])) > 0)) {
                //Log.d(pTAG,"Valley Value "+timecount[count]+" wm "+wm[count]);
                ascend = true;
            }
        }

        //////////////////////////////
        // START OF ALGORITHM
        //////////////////////////////

        int lastStepCount = 0;
        float firstLeftStep = 0, firstRightStep = 0, lastLeftStep = 0, lastRightStep = 0;
        //find steps from zero crossing and peak values
        //use 3 seconds as threshold for sit to stand and 2 seconds to press STOP button
        for (count = 300; count < size - 200; count++) {
            boolean stepFound = false;
            int stepCount = 0;

            if (zerogz[count] == true) {
                if ((count >= gxyNegZeroCrossingT) && (count <= gxyPosZeroCrossingT)) {
                    //Log.d(pTAG, "Ignoring Turn Steps count " + count + " time count " + timecount[count]);
                } else {
                    // Log.d(pTAG,"looking for zerogz count "+count+" time count "+timecount[count]);
                    // check for highest peak after zero crossing
                    for (int peakCount = count + 1; (zerogz[peakCount] != true) && (peakCount < size); peakCount++) {
                        // Find the First peak after zero crossing
                        if (peakwm[peakCount] == true) {
                            stepFound = true;
                            stepCount = peakCount;
                            break;
                        }
                    }
                    if ((stepFound == true) && (stepCount >= lastStepCount + 25)) // healthy younger adults
                    {
                        lastStepCount = stepCount;
                        numberOfSteps++; //Increment step counter
                        if (gz[count] < 0) {
                            numberOfLeftSteps++;
                            addStep((timecount[stepCount] / 100), gz[stepCount], wm[stepCount], "Left");
                            if(numberOfLeftSteps==1) {
                                firstLeftStep = (timecount[stepCount])/100;
                            }
                            lastLeftStep = (timecount[stepCount])/100;
                        } else {
                            numberOfRightSteps++;
                            addStep((timecount[stepCount] / 100), gz[stepCount], wm[stepCount], "Right");
                            if(numberOfRightSteps==1) {
                                firstRightStep = (timecount[stepCount])/100;
                            }
                            lastRightStep = (timecount[stepCount])/100;
                        }
                        //Log.d(pTAG, "Adding " + stepstr + timecount[stepCount] + " gz " + gz[stepCount] + " wm " + wm[stepCount]);
                    }
                }
            }
        }

        if(numberOfLeftSteps>1)
        {
            leftStepMean = (lastLeftStep - firstLeftStep)/(numberOfLeftSteps-1);
        }
        if(numberOfRightSteps>1)
        {
            rightStepMean = (lastRightStep - firstRightStep)/(numberOfRightSteps-1);
        }

        ratioTSLTSR = leftStepMean / rightStepMean;
        Log.d(pTAG, "rightStepMean "+rightStepMean+" LeftStepMean "+leftStepMean+" TSLTSR "+ratioTSLTSR);

        return numberOfSteps;
    } // end of ProcessData()

    public boolean addStep(float val, float gzcount, float wmcount, String stepType) {
        maxSteps.add(new Step(val, gzcount, wmcount, stepType));
        return true;
    }

    public void testComplete(View view) {
        mSensorManager.unregisterListener(mListener, mSensor);
        mSensorManager.unregisterListener(mListener, gyroSensor);
        mTextbutton.setClickable(false);
        mTextbutton.setText("RELAX");

        new CountDownTimer(15000, 1000) {
            public void onTick(long millisUntilFinished) {
                mTextCount.setText("" + millisUntilFinished / 1000);
            }

            public void onFinish() {
                mSensorManager.unregisterListener(mListener, hrSensor);   // try unregistering just hr listener
                vibrator.vibrate(VibrationEffect.createOneShot(2000, 250));
                steps = ProcessData();

                if (IMU.timeStamp.size() > 0) {
                    time = (((float) IMU.timeStamp.size() - 50) / 100);
                }

                Log.e("UAH", "time " + time + " steps " + steps + " TSL/TSR " + ratioTSLTSR);
                Intent end = new Intent(getApplicationContext(), Result.class);
                end.putExtra("time", time);
                end.putExtra("steps", steps);
                end.putExtra("ratioTSLTSR", ratioTSLTSR);

                startActivity(end);
                finish();// Prevent coming back here from result screen
            }
        }.start();
    }
} // end of DisplayMessageActivity
