package ca.sarvajit.sensorrecord;

import android.annotation.SuppressLint;
import android.app.Notification;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.database.SQLException;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.Messenger;
import android.os.PowerManager;
import android.os.PowerManager.WakeLock;
import android.os.Process;
import android.os.RemoteException;
import android.util.Log;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class SensorService extends Service{

    public static final String TAG = "SensorService";

    public static final int SCREEN_OFF_RECEIVER_DELAY = 100;

    int POLL_FREQUENCY = 9; //in milliseconds

    private long lastUpdate = -1;
    long curTime;

    private Messenger messageHandler;

    private SensorManager sensorManager = null;
    private WakeLock wakeLock = null;
    ExecutorService executor;
    DBHelper dbHelper;
    Sensor sensor;
    Sensor accelerometer;
    Sensor gyroscope;
    Sensor gravity;
    Sensor magnetic;
    Sensor proximity;
    Sensor light;
    Sensor stepdetector;
    LocationManager locationManager;
    gpsdata newgpsdata=new gpsdata();
    sensordata newsensordata=new sensordata();
    NewFragment newfragment=new NewFragment();

    float[] accelerometerMatrix = new float[3];
    float[] gyroscopeMatrix = new float[3];
    float[] gravityMatrix = new float[3];
    float[] magneticMatrix = new float[3];
    float[] rotationMatrix = new float[9];
    float[] proximatrix = new float[1];
    float[] lightmatrix = new float[1];
    float[] stepmatrix = new float[1];
    float[] gpsmatrix=new float[2];




    public BroadcastReceiver receiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            Log.i(TAG, "onReceive(" + intent + ")");

            if (!intent.getAction().equals(Intent.ACTION_SCREEN_OFF)) {
                return;
            }

            Runnable runnable = new Runnable() {
                public void run() {
                    Log.i(TAG, "Runnable executing...");
                    newsensordata.unregisterListener();
                    newsensordata.registerListener();
                }
            };

            new Handler().postDelayed(runnable, SCREEN_OFF_RECEIVER_DELAY);
        }
    };

    public void sendMessage(String state) {
        Message message = Message.obtain();
        switch (state) {
            case "HIDE" :
                message.arg1 = 0;
                break;
            case "SHOW":
                message.arg1 = 1;
                break;
        }
        try {
            messageHandler.send(message);
        } catch (RemoteException e) {
            e.printStackTrace();
        }
    }


    public class sensordata implements SensorEventListener {
        public void onAccuracyChanged(Sensor sensor, int accuracy) {
            //Safe not to implement
        }

        public void onSensorChanged(SensorEvent event) {
            sensor = event.sensor;

            //Store sensor data
            int i = sensor.getType();
            if (i == MainActivity.TYPE_ACCELEROMETER) {
                accelerometerMatrix = event.values;
            } else if (i == MainActivity.TYPE_GYROSCOPE) {
                gyroscopeMatrix = event.values;
            } else if (i == MainActivity.TYPE_GRAVITY) {
                gravityMatrix = event.values;
            } else if (i == MainActivity.TYPE_MAGNETIC) {
                magneticMatrix = event.values;
            } else if (i == sensor.TYPE_PROXIMITY) {
                proximatrix = event.values;
            } else if (i == sensor.TYPE_LIGHT) {
                lightmatrix = event.values;
            } else if (i == sensor.TYPE_STEP_DETECTOR) {
                stepmatrix = event.values;
            }
            SensorManager.getRotationMatrix(rotationMatrix, null, gravityMatrix, magneticMatrix);

            POLL_FREQUENCY=newfragment.frequency;
            curTime = event.timestamp; //in nanoseconds
            if ((curTime - lastUpdate) > POLL_FREQUENCY * 1000000) {

                lastUpdate = curTime;
                try {
                    Runnable insertHandler = new InsertHandler(curTime, accelerometerMatrix, gyroscopeMatrix,
                            gravityMatrix, magneticMatrix, rotationMatrix, proximatrix, lightmatrix, stepmatrix, gpsmatrix);
                    executor.execute(insertHandler);
                } catch (SQLException e) {
                    Log.e(TAG, "insertData: " + e.getMessage(), e);
                }
            }
        }

        private void registerListener() {
            sensorManager.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_FASTEST);
            sensorManager.registerListener(this, gyroscope, SensorManager.SENSOR_DELAY_FASTEST);
            sensorManager.registerListener(this, gravity, SensorManager.SENSOR_DELAY_FASTEST);
            sensorManager.registerListener(this, magnetic, SensorManager.SENSOR_DELAY_FASTEST);
            sensorManager.registerListener(this, proximity, SensorManager.SENSOR_DELAY_FASTEST);
            sensorManager.registerListener(this, light, SensorManager.SENSOR_DELAY_FASTEST);
            sensorManager.registerListener(this, stepdetector, SensorManager.SENSOR_DELAY_FASTEST);

        }

        private void unregisterListener() {
            sensorManager.unregisterListener(this);
        }

    }




    @SuppressLint({"InvalidWakeLockTag", "MissingPermission"})
    @Override
    public void onCreate() {
        super.onCreate();

        dbHelper = DBHelper.getInstance(getApplicationContext());

        sensorManager = (SensorManager) getSystemService(SENSOR_SERVICE);
        accelerometer = sensorManager.getDefaultSensor(MainActivity.TYPE_ACCELEROMETER);
        gyroscope = sensorManager.getDefaultSensor(MainActivity.TYPE_GYROSCOPE);
        gravity = sensorManager.getDefaultSensor(MainActivity.TYPE_GRAVITY);
        magnetic = sensorManager.getDefaultSensor(MainActivity.TYPE_MAGNETIC);
        proximity=sensorManager.getDefaultSensor(sensor.TYPE_PROXIMITY);
        light=sensorManager.getDefaultSensor(Sensor.TYPE_LIGHT);
        stepdetector=sensorManager.getDefaultSensor(Sensor.TYPE_STEP_DETECTOR);
        locationManager=(LocationManager)getSystemService(Context.LOCATION_SERVICE);
        locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER,1,0,newgpsdata);
        PowerManager manager = (PowerManager) getSystemService(Context.POWER_SERVICE);
        wakeLock = manager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, TAG);

        registerReceiver(receiver, new IntentFilter(Intent.ACTION_SCREEN_OFF));

        executor = Executors.newSingleThreadExecutor();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        super.onStartCommand(intent, flags, startId);

        startForeground(Process.myPid(), new Notification());
        newsensordata.registerListener();
        wakeLock.acquire();

        //Message handler for progress dialog
        Bundle extras = intent.getExtras();
        messageHandler = (Messenger) extras.get("MESSENGER");

        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        //Show dialog
        sendMessage("SHOW");

        //Unregister receiver and listener prior to executor shutdown
        unregisterReceiver(receiver);
        newsensordata.unregisterListener();

        //Prevent new tasks from being added to thread
        executor.shutdown();
        Log.d(TAG, "Executor shutdown is called");

        //Create new thread to wait for executor to clear queue and wait for termination
        new Thread(new Runnable() {

            public void run() {
                try {
                    //Wait for all tasks to finish before we proceed
                    while (!executor.awaitTermination(1, TimeUnit.SECONDS)) {
                        Log.i(TAG, "Waiting for current tasks to finish");
                    }
                    Log.i(TAG, "No queue to clear");
                } catch (InterruptedException e) {
                    Log.e(TAG, "Exception caught while waiting for finishing executor tasks");
                    executor.shutdownNow();
                    Thread.currentThread().interrupt();
                }

                if (executor.isTerminated()) {
                    //Stop everything else once the task queue is clear
                    wakeLock.release();
                    stopForeground(true);

                    //Dismiss progress dialog
                    sendMessage("HIDE");
                }
            }
        }).start();
    }



    public class gpsdata implements LocationListener{

        @Override
        public void onLocationChanged(Location location) {
            gpsmatrix[0]= (float) location.getLongitude();
            gpsmatrix[1]=(float)location.getLatitude();
        }

        @Override
        public void onStatusChanged(String provider, int status, Bundle extras) {

        }

        @Override
        public void onProviderEnabled(String provider) {

        }

        @Override
        public void onProviderDisabled(String provider) {

        }
    }






    class InsertHandler implements Runnable {
        final long curTime;
        final float[] accelerometerMatrix;
        final float[] gyroscopeMatrix;
        final float[] gravityMatrix;
        final float[] magneticMatrix;
        final float[] rotationMatrix;
        final float[] proximatrix;
        final float[] lightmatrix;
        final float[] stepmatrix;
        final float[] gpsmatrix;
        //Store the current sensor array values into THIS objects arrays, and db insert from this object
        public InsertHandler(long curTime, float[] accelerometerMatrix,
                             float[] gyroscopeMatrix, float[] gravityMatrix,
                             float[] magneticMatrix, float[] rotationMatrix, float[] proximatrix,
                             float[] lightmatrix, float[] stepmatrix, float[] gpsmatrix) {
            this.curTime = curTime;
            this.accelerometerMatrix = accelerometerMatrix;
            this.gyroscopeMatrix = gyroscopeMatrix;
            this.gravityMatrix = gravityMatrix;
            this.magneticMatrix = magneticMatrix;
            this.rotationMatrix = rotationMatrix;
            this.proximatrix=proximatrix;
            this.lightmatrix=lightmatrix;
            this.stepmatrix=stepmatrix;
            this.gpsmatrix = gpsmatrix;
        }

        public void run() {
            dbHelper.insertDataTemp(Short.parseShort(dbHelper.getTempSubInfo("subNum")),
                    this.curTime,
                    this.accelerometerMatrix,
                    this.gyroscopeMatrix,
                    this.gravityMatrix,
                    this.magneticMatrix,
                    this.rotationMatrix,
                    this.proximatrix,
                    this.lightmatrix,
                    this.stepmatrix,
                    this.gpsmatrix);
        }
    }
}