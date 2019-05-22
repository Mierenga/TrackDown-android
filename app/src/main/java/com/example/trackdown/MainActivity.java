package com.example.trackdown;

import android.Manifest;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.location.Location;
import android.os.Looper;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.maps.CameraUpdate;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.MapView;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.model.CameraPosition;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.gms.maps.model.Polyline;
import com.google.android.gms.maps.model.PolylineOptions;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;

public class MainActivity extends AppCompatActivity {

    private static final int MILLISECONDS_PER_SECOND = 1000;

    public static final int UPDATE_INTERVAL_IN_SECONDS = 5;
    private static final long UPDATE_INTERVAL =
            MILLISECONDS_PER_SECOND * UPDATE_INTERVAL_IN_SECONDS;

    private static final int FASTEST_INTERVAL_IN_SECONDS = 1;
    private static final long FASTEST_INTERVAL =
            MILLISECONDS_PER_SECOND * FASTEST_INTERVAL_IN_SECONDS;

    private boolean requestingLocationUpdates = true;
    private FusedLocationProviderClient locationClient;
    private LocationCallback locationCallback;
    private MapView mapView;
    private PolylineOptions routeLine = new PolylineOptions();
    private Polyline polyline;
    private Marker marker;

    enum RequestCode {
        INTERNET,
        COARSE_LOCATION,
        FINE_LOCATION,
        WRITE_STORAGE
    }
    String[] requiredPermissions = {
            Manifest.permission.INTERNET,
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.WRITE_EXTERNAL_STORAGE
    };


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.d("MainActivity", "OnCreate");
        setContentView(R.layout.activity_main);
        mapView = findViewById(R.id.mapView);
        // TODO need to call mapView.onCreate() when I can get an API key, but the site is not working
        //mapView.onCreate(savedInstanceState);
        routeLine.width(5).color(Color.blue(255));
        locationClient = LocationServices.getFusedLocationProviderClient(this);
        locationCallback = new LocationCallback() {
            @Override
            public void onLocationResult(LocationResult locationResult) {
                if (locationResult == null) {
                    return;
                }
                for (final Location location : locationResult.getLocations()) {
                    // Update UI with location data
                    // ...
                    String line = location.getLatitude() + ", " + location.getLongitude() + ", " + location.getTime();
                    appendLog("sdcard/route." + new SimpleDateFormat("yyyy-MM-dd").format(new Date()) + ".csv", line);
                    Log.d("LOCATION", line);
                    Log.d("SPEED", location.getSpeed() + " m/s");
                    Log.d("PROVIDER", location.getProvider());
                    Log.d("RADIUS", location.getAccuracy() + " meters");

                    mapView.getMapAsync(new OnMapReadyCallback() {
                        @Override
                        public void onMapReady(GoogleMap googleMap) {
                            LatLng latLng = new LatLng(location.getLatitude(), location.getLongitude());
                            routeLine.add(latLng);
                            if (polyline != null) {
                                polyline.remove();
                            }
                            if (marker != null) {
                                marker.remove();
                            }
                            polyline = googleMap.addPolyline(routeLine);
                            marker = googleMap.addMarker(new MarkerOptions()
                                    .position(latLng));
                            CameraPosition cameraPosition = new CameraPosition.Builder()
                                    .target(latLng)
                                    .zoom(17)
                                    .tilt(30)
                                    .build();
                            googleMap.animateCamera(CameraUpdateFactory.newCameraPosition(cameraPosition));
                            mapView.onResume();
                        }
                    });
                }
            }
        };
    }

    @Override
    protected void onResume() {
        super.onResume();
        Log.d("MainActivity", "OnResume");


        for (int i = 0; i < requiredPermissions.length; i++) {
            if (ContextCompat.checkSelfPermission(this, requiredPermissions[i])
                    != PackageManager.PERMISSION_GRANTED) {
                Log.d("PERMISSIONS", "Asking permission for " + requiredPermissions[i]);
                ActivityCompat.requestPermissions(this,
                        new String[]{requiredPermissions[i]},
                        i);
            } else {
                Log.d("PERMISSIONS", "Already have permission for " + requiredPermissions[i]);
            }
        }
        if (requestingLocationUpdates) {
            startLocationUpdates();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {

        String message = "Did not get permissions for ";
        String permission = "unknown";
        if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            message = "Got permission for ";
        }
        switch (RequestCode.values()[requestCode]) {
            case INTERNET: permission = "internet"; break;
            case FINE_LOCATION: permission = "fine location"; break;
            case COARSE_LOCATION: permission = "coarse location"; break;
            case WRITE_STORAGE: permission = "writing to file"; break;
        }

        Log.d("PERMISSIONS", message + permission);
    }

    private void startLocationUpdates() {
        try {
            LocationRequest locationRequest = LocationRequest.create();
            locationRequest.setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY);
            locationRequest.setInterval(UPDATE_INTERVAL);
            locationClient.requestLocationUpdates(locationRequest, locationCallback, Looper.getMainLooper());
        } catch (SecurityException e) {
            Log.d("Security Exception", "unable to get location due to missing permission(s)");
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        Log.d("MainActivity", "OnPause");
        stopLocationUpdates();
    }

    private void stopLocationUpdates() {
       locationClient.removeLocationUpdates(locationCallback);
    }




    public void appendLog(String filename, String text)
    {
        File logFile = new File(filename);
        if (!logFile.exists())
        {
            try
            {
                logFile.createNewFile();
            }
            catch (IOException e)
            {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }
        }
        try
        {
            //BufferedWriter for performance, true to set append to file flag
            BufferedWriter buf = new BufferedWriter(new FileWriter(logFile, true));
            buf.append(text);
            buf.newLine();
            buf.close();
        }
        catch (IOException e)
        {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
    }
}
