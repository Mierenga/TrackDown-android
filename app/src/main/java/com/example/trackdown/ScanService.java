package com.example.trackdown;

import android.app.IntentService;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Intent;
import android.content.Context;
import android.content.IntentFilter;
import android.location.Location;
import android.net.wifi.ScanResult;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.Looper;
import android.os.Message;
import android.os.Process;
import android.os.ResultReceiver;
import android.telephony.TelephonyManager;
import android.util.Log;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.location.LocationServices;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Serializable;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.TimeZone;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import javax.xml.transform.Result;


public class ScanService extends Service {
    public static final String ACTION_START_SCAN = "com.example.trackdown.action.ACTION_SCAN_WIFI";
    public static final String ACTION_SET_OPTIONS = "com.example.trackdown.action.ACTION_SCAN_LOCATION";

    public static final String SCAN_UPDATE_ACTION = "com.example.trackdown.action.SCAN_UPDATE_ACTION";
    public static final String EXTRA_SCAN_UPDATE_RECEIVER = "com.example.trackdown.extra.SCAN_UPDATE_RECEIVER";

    public static final int LOCATION_UPDATE_INTERVAL_MS = 1000;

    Looper serviceLooper;
    ServiceHandler serviceHandler;

    WifiManager wifiManager;
    ScheduledExecutorService scheduleTaskExecutor;
    BroadcastReceiver wifiScanReceiver;
    String deviceWifiMAC = "unknown";
    List<ScanResult> results;

    private final class ServiceHandler extends Handler {
        Context context;
        private FusedLocationProviderClient locationClient;
        private LocationCallback locationCallback;
        private Location location;
        ScanOptions scanOptions = new ScanOptions();
        String currentCSVFileName;

        public ServiceHandler(Looper looper, Context context) {
            super(looper);
            this.context = context;
            wifiManager = (WifiManager) getApplicationContext().getSystemService(Context.WIFI_SERVICE);
            deviceWifiMAC = getDeviceID();
        }

        @Override
        public void handleMessage(Message msg) {
            Bundle bundle = msg.getData();
            final ScanOptions scanOptions = (ScanOptions) bundle.getSerializable(ScanOptions.EXTRA_SCAN_OPTIONS);
            if (locationClient == null) {
                handleActionStartScan();
            } else {
                setScanOptions(scanOptions);
            }
        }

        private void handleActionStartScan() {
            if (!this.scanOptions.active) { return; }
            if (locationClient == null) {
                locationCallback = new LocationCallback() {
                    @Override
                    public void onLocationResult(LocationResult locationResult) {
                        if (locationResult == null) { return; }

                        for (final Location loc : locationResult.getLocations()) {
                            location = loc;
                            Log.d("LOCATION UPDATE", "" + loc.getTime());
                            String line = formatLocationForCSV(location, getDeviceID());
                            if (scanOptions.logLocation) {
                                if (currentCSVFileName == null) {
                                    currentCSVFileName = "sdcard/trackdown.locations." + new SimpleDateFormat("yyyy-MM-dd.HH-mm-ss").format(new Date()) + ".csv";
                                }
                                appendCSV(currentCSVFileName, line);
                            }
                            Log.d("CSV FILE NAME", "" + currentCSVFileName);
                            sendUpdate(packageScanUpdate(location, currentCSVFileName));
                        }
                    }
                };

                locationClient = LocationServices.getFusedLocationProviderClient(this.context);

                try {
                    LocationRequest locationRequest = LocationRequest.create();
                    locationRequest.setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY);
                    locationRequest.setInterval(LOCATION_UPDATE_INTERVAL_MS);
                    locationRequest.setFastestInterval(LOCATION_UPDATE_INTERVAL_MS);
                    locationClient.requestLocationUpdates(locationRequest, locationCallback, Looper.getMainLooper());
                } catch (SecurityException e) {
                    Log.d("Security Exception", "unable to get location due to missing permission(s)");
                }
            }

        }

        private void setScanOptions(ScanOptions scanOptions) {
            if (scanOptions == null) { return; }
            this.scanOptions = scanOptions;
            if (!scanOptions.logLocation) {
                currentCSVFileName = null;
            }
        }

        private ScanUpdate packageScanUpdate(Location loc, String csvFileName) {
            ScanUpdate scanUpdate = new ScanUpdate();
            scanUpdate.timestamp = loc.getTime();
            scanUpdate.latitude = loc.getLatitude();
            scanUpdate.longitude = loc.getLongitude();
            scanUpdate.locationProvider = loc.getProvider();
            scanUpdate.locationSpeedMetersPerSecond = loc.getSpeed();
            scanUpdate.locationRadiusMeters = loc.getAccuracy();
            scanUpdate.csvFileName = csvFileName;
            return scanUpdate;
        }

        private void sendUpdate(ScanUpdate scanUpdate) {
            Intent intent = new Intent();
            intent.setAction(SCAN_UPDATE_ACTION);
            intent.putExtra(ScanUpdate.EXTRA_SCAN_UPDATE, scanUpdate);
            sendBroadcast(intent);
        }

        private String getDeviceID() {
            return wifiManager.getConnectionInfo().getMacAddress();
        }

    }

    @Override
    public void onCreate() {
        scheduleTaskExecutor = Executors.newScheduledThreadPool(5);
        HandlerThread thread = new HandlerThread("ScanThread", Process.THREAD_PRIORITY_BACKGROUND);
        thread.start();

        serviceLooper = thread.getLooper();
        serviceHandler = new ServiceHandler(serviceLooper, getApplicationContext());
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Message msg = serviceHandler.obtainMessage();
        msg.arg1 = startId;
        msg.setData(intent.getExtras());
        serviceHandler.sendMessage(msg);
        return START_STICKY;
    }

    public static void start(Context context, ScanOptions scanOptions) {
        Intent scanIntent = new Intent(context, ScanService.class);
        scanIntent.setAction(ScanService.ACTION_START_SCAN);
        scanIntent.putExtra(ScanOptions.EXTRA_SCAN_OPTIONS, scanOptions);
        context.startService(scanIntent);
    }

    public static void setOptions(Context context, ScanOptions scanOptions) {
        Intent intent = new Intent(context, ScanService.class);
        intent.setAction(ACTION_SET_OPTIONS);
        intent.putExtra(ScanOptions.EXTRA_SCAN_OPTIONS, scanOptions);
        context.startService(intent);
    }

//    @Override
//    protected void onHandleIntent(Intent intent) {
//        if (wifiManager == null) {
//            wifiManager = (WifiManager) getApplicationContext().getSystemService(Context.WIFI_SERVICE);
//            deviceWifiMAC = wifiManager.getConnectionInfo().getMacAddress();
//        }
//        if (wifiScanReceiver == null) {
//            wifiScanReceiver = new BroadcastReceiver() {
//                @Override
//                public void onReceive(Context c, Intent intent) {
//                    Log.d("Service:onReceive", intent.getAction());
//                    results = wifiManager.getScanResults();
//                    for (final ScanResult result : results) {
//                        String line = result.timestamp + ", " + result.BSSID + ", " + result.SSID + ", " + result.level;
//                        appendCSV("sdcard/trackdown.wifiscans." + new SimpleDateFormat("yyyy-MM-dd").format(new Date()) + ".csv", line);
//                    }
//                    scheduleTaskExecutor.schedule(new Runnable() {
//                        @Override
//                        public void run() {
//                            if (scanOptions.logWifi) {
//                                wifiManager.startScan();
//                                Log.d("ScheduleTaskExecutor", "Starting new scan");
//                            }
//                        }
//                    }, 5, TimeUnit.SECONDS);
//
//
//                }
//            };
//            registerReceiver(wifiScanReceiver, new IntentFilter(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION));
//        }

//        this.scanUpdateReceiver = intent.getParcelableExtra(EXTRA_SCAN_UPDATE_RECEIVER);
//
//        if (intent != null) {
//            final String action = intent.getAction();
//            final ScanOptions scanOptions = (ScanOptions)intent.getSerializableExtra(ScanOptions.EXTRA_SCAN_OPTIONS);
//            if (ACTION_START_SCAN.equals(action)) {
//                handleActionStartScan();
//            } else if (ACTION_SET_OPTIONS.equals(action)) {
//                setScanOptions(scanOptions);
//            }

//            if (scanOptions.logWifi) {
//                Log.d("WIFI", "Starting WiFi Scan");
//                startWifiScan();
//            }
//        }
//    }

    private void startWifiScan() {
        if (!wifiManager.isWifiEnabled()) {
            wifiManager.setWifiEnabled(true);
        }
        Log.d("WIFI","starting wifi scan");
        wifiManager.startScan();
    }

    private String formatLocationForCSV(Location location, String deviceID) {

        /* Timestamp (UTC), Latitude, Longitude, Display Radius (Meters), Source, Device Tag */
        String entry = "";
        entry += timestampToUTCFormat(location.getTime());
        entry += ",";
        entry += location.getLatitude();
        entry += ",";
        entry += location.getLongitude();
        entry += ",";
        entry += location.getAccuracy();
        entry += ",";
        entry += location.getProvider();
        entry += ",";
        entry += deviceID;

        Log.d("CSV ENTRY", entry);
        return entry;
    }

    private static String getCSVHeader() {
        return "Timestamp (UTC), Latitude, Longitude, Display Radius (Meters), Source, Device Tag";
    }

    @Override
    public IBinder onBind(Intent intent) {
        // We don't provide binding, so return null
        return null;
    }

    public static void appendCSV(String filename, String text) {
        File logFile = new File(filename);
        try {
            if (!logFile.exists()) {
                logFile.createNewFile();
                appendLine(logFile, getCSVHeader());
            } else {
                appendLine(logFile, text);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static void appendLine(File file, String line) throws IOException {
        BufferedWriter buf = new BufferedWriter(new FileWriter(file, true));
        buf.append(line);
        buf.newLine();
        buf.close();
    }

    private static String timestampToUTCFormat(long ts) {
        SimpleDateFormat utcFormatter = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'");
        utcFormatter.setTimeZone(TimeZone.getTimeZone("UTC"));
        return utcFormatter.format(ts);
    }

}
