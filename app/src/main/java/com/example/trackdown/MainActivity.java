package com.example.trackdown;

import android.Manifest;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.drawable.BitmapDrawable;
import android.location.Location;
import android.net.Uri;
import android.net.wifi.ScanResult;
import android.net.wifi.WifiManager;
import android.os.Environment;
import android.os.Handler;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v4.content.FileProvider;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.Toast;

import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.MapView;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.CameraPosition;
import com.google.android.gms.maps.model.CircleOptions;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.gms.maps.model.Polyline;
import com.google.android.gms.maps.model.PolylineOptions;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.List;

import static com.example.trackdown.ScanService.appendCSV;

public class MainActivity extends AppCompatActivity {
    private static final float INITIAL_ZOOM_LEVEL = 19.3f;

    private boolean initialZoomDone = false;
    private MapView mapView;
    private PolylineOptions routePolylineOptions;
    private Polyline routePolyline;
    private Marker currentLocationMarker;
    private Marker startMarker;
    private Marker endMarker;
    protected HashMap<String, LocationedScanResult> locationedScanResults = new HashMap<>();

    class LocationedScanResult {
        public ScanResult scanResult;
        public Location location;
        public Marker marker;
        public LocationedScanResult(ScanResult scanResult, Location location, Marker marker) {
            this.scanResult = scanResult;
            this.location = location;
            this.marker = marker;
        }
    }

    private boolean scanningWifi = true;
    WifiManager wifi;
    List<ScanResult> results;
    private Bitmap wifiIcon;
    private Bitmap currentLocIcon;

    private String lastRouteFileName = null;

    private Button buttonToggleTracking;
    private Button buttonSendRoute;
    private CheckBox optionFollowOnMap;
    private CheckBox optionUpdateCurrentRadiusOnMap;
    private CheckBox optionUpdateWifiOnMap;
    private CheckBox optionRecordWifiScan;

    private boolean isTrackingLocation = false;
    private boolean shouldMarkRouteStart = false;
    private LatLng currentLatLng;

    public BroadcastReceiver scanUpdateReceiver;


    enum RequestCode {
        INTERNET,
        COARSE_LOCATION,
        FINE_LOCATION,
        WRITE_STORAGE,
        WIFI_STATE,
        CHANGE_WIFI_STATE,
    }
    String[] requiredPermissions = {
            Manifest.permission.INTERNET,
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.WRITE_EXTERNAL_STORAGE,
            Manifest.permission.ACCESS_WIFI_STATE,
            Manifest.permission.CHANGE_WIFI_STATE,
    };


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.d("MainActivity", "OnCreate");
        setContentView(R.layout.activity_main);

        wifiIcon = ((BitmapDrawable) this.getResources().getDrawable(R.drawable.wifi)).getBitmap();
        wifiIcon = Bitmap.createScaledBitmap(wifiIcon, 100, 100, false);
        currentLocIcon = ((BitmapDrawable) this.getResources().getDrawable(R.drawable.currentloc)).getBitmap();
        currentLocIcon = Bitmap.createScaledBitmap(currentLocIcon, 80, 80, false);

        mapView = findViewById(R.id.mapView);
        mapView.onCreate(savedInstanceState);

        buttonToggleTracking = findViewById(R.id.buttonTrack);
        buttonSendRoute = findViewById(R.id.buttonSend);
        optionRecordWifiScan = findViewById(R.id.optionRecordWifiScan);
        optionFollowOnMap = findViewById(R.id.optionFollowOnMap);
        optionUpdateWifiOnMap = findViewById(R.id.optionShowWifi);
        optionUpdateCurrentRadiusOnMap = findViewById(R.id.optionShowRadius);

        buttonToggleTracking.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (!isTrackingLocation) {
                    Log.d("MainActivity", "Starting route.");
                    startRoute();
                } else {
                    Log.d("MainActivity", "Ending route.");
                    endRoute();
                }
            }
        });

        buttonSendRoute.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (lastRouteFileName != null) {
                    sendEmail(lastRouteFileName);
                } else {
                    Toast.makeText(MainActivity.this, "No route available to send. Record a route first.", Toast.LENGTH_LONG).show();
                }
            }
        });

        optionRecordWifiScan.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                Log.d("toggled wifi scan", "" + isChecked);
                ScanService.setOptions(getApplicationContext(), makeScanOptions());
            }
        });

        routePolylineOptions = getEmptyPolylineOptions();

        scanUpdateReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context c, Intent intent) {

                final ScanUpdate scanUpdate = (ScanUpdate)intent.getSerializableExtra(ScanUpdate.EXTRA_SCAN_UPDATE);
                if (scanUpdate == null) { return; }

                Log.d("SCAN_UPDATE", (scanUpdate.csvFileName == null) ? "no file" : scanUpdate.csvFileName);
                if (scanUpdate.csvFileName != null) {
                    lastRouteFileName = scanUpdate.csvFileName;
                }

                mapView.getMapAsync(new OnMapReadyCallback() {
                    @Override
                    public void onMapReady(GoogleMap googleMap) {
                        currentLatLng = new LatLng(scanUpdate.latitude, scanUpdate.longitude);

                        currentLocationMarker = googleMap.addMarker(new MarkerOptions()
                                .icon(BitmapDescriptorFactory.fromBitmap(currentLocIcon))
                                .zIndex(201)
                                .anchor(0.5f, 0.5f)
                                .position(currentLatLng));

                        if (optionUpdateCurrentRadiusOnMap.isChecked()) {
                            googleMap.addCircle(new CircleOptions()
                                    .radius(scanUpdate.locationRadiusMeters)
                                    .center(currentLatLng)
                                    .strokeWidth(0)
                                    .fillColor(0x104F159D)
                            );
                        }

                        if (isTrackingLocation) {
                            routePolylineOptions.add(currentLatLng);
                            if (routePolyline != null) {
                                routePolyline.remove();
                            }
                            routePolyline = googleMap.addPolyline(routePolylineOptions);
                        }
                        if (shouldMarkRouteStart) {
                            startMarker = addBalloonMarker(googleMap, currentLatLng, BitmapDescriptorFactory.HUE_GREEN, 101);
                            shouldMarkRouteStart = false;
                        }
                        if (currentLocationMarker != null) {
                            currentLocationMarker.remove();
                        }
                        if (optionFollowOnMap.isChecked()) {
                            float zoom = googleMap.getCameraPosition().zoom;

                            if (!initialZoomDone && zoom >= INITIAL_ZOOM_LEVEL) {
                                if (zoom >= INITIAL_ZOOM_LEVEL) {
                                    initialZoomDone = true;
                                }
                            } else if (!initialZoomDone) {
                                zoom = INITIAL_ZOOM_LEVEL;
                            }

                            CameraPosition cameraPosition = new CameraPosition.Builder()
                                    .target(currentLatLng)
                                    .zoom(zoom)
                                    .bearing(googleMap.getCameraPosition().bearing)
                                    .build();
                            googleMap.animateCamera(CameraUpdateFactory.newCameraPosition(cameraPosition));
                        }

//                            Marker wifiMarker = null;
//                            if (location != null) {
//                                LatLng latLng = new LatLng(location.getLatitude(), location.getLongitude());
//                                if (locationedScanResults.containsKey(result.BSSID)) {
//                                    LocationedScanResult locationedScanResult = locationedScanResults.get(result.BSSID);
//                                    if (result.level >= locationedScanResult.scanResult.level) {
//                                        locationedScanResults.remove(result.BSSID);
//                                        if (locationedScanResult.currentLocationMarker != null) {
//                                            locationedScanResult.currentLocationMarker.remove();
//                                            locationedScanResult.currentLocationMarker = null;
//                                        }
//
//                                        if (optionUpdateWifiOnMap.isChecked()) {
//                                            wifiMarker = googleMap.addMarker(new MarkerOptions()
//                                                    .icon(BitmapDescriptorFactory.fromBitmap(wifiIcon))
//                                                    .title(result.SSID)
//                                                    .position(latLng));
//                                        }
//                                        locationedScanResults.put(result.BSSID,
//                                                new LocationedScanResult(result, location, wifiMarker));
//                                    }
//                                } else {
//                                    if (optionUpdateWifiOnMap.isChecked()) {
//                                        wifiMarker = googleMap.addMarker(new MarkerOptions()
//                                                .icon(BitmapDescriptorFactory.fromBitmap(wifiIcon))
//                                                .title(result.SSID)
//                                                .position(latLng));
//                                        googleMap.setOnMarkerClickListener(new GoogleMap.OnMarkerClickListener() {
//                                            @Override
//                                            public boolean onMarkerClick(Marker currentLocationMarker) {
//                                                wifiListView.setVisibility(View.VISIBLE);
//                                                wifiListView.setAdapter(new RecyclerView.Adapter() {
//                                                    @NonNull
//                                                    @Override
//                                                    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup viewGroup, int i) {
//                                                        return null;
//                                                    }
//
//                                                    @Override
//                                                    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder viewHolder, int i) {
//
//                                                    }


                        mapView.onResume();
                    }
                });

            }
        };

        ScanService.start(getApplicationContext(), makeScanOptions());
    }

    private ScanOptions makeScanOptions() {
        ScanOptions scanOptions = new ScanOptions();
        scanOptions.active = true;
        scanOptions.logLocation = isTrackingLocation;
        scanOptions.logWifi = isTrackingLocation && optionRecordWifiScan.isChecked();
        return scanOptions;
    }

    private void startRoute() {
        buttonToggleTracking.setBackgroundTintList(getResources().getColorStateList(R.color.colorRed));
        buttonToggleTracking.setCompoundDrawablesWithIntrinsicBounds(null,null,getResources().getDrawable(android.R.drawable.ic_media_pause), null);
        buttonToggleTracking.setText("Stop Tracking");
        if (routePolyline != null) { routePolyline.remove(); }
        routePolylineOptions = getEmptyPolylineOptions();
        if (startMarker != null) { startMarker.remove(); startMarker = null; }
        if (endMarker != null) { endMarker.remove(); endMarker = null; }
        shouldMarkRouteStart = true;
        isTrackingLocation = true;
        ScanService.setOptions(getApplicationContext(), makeScanOptions());
        Toast.makeText(this, "Route Started", Toast.LENGTH_SHORT).show();
    }


    private void endRoute() {
        buttonToggleTracking.setBackgroundTintList(getResources().getColorStateList(R.color.colorGreen));
        buttonToggleTracking.setCompoundDrawablesWithIntrinsicBounds(null,null,getResources().getDrawable(android.R.drawable.ic_media_play), null);
        buttonToggleTracking.setText("Start Tracking");
        isTrackingLocation = false;
        Log.d("MainActivity", "End Route");
        if (currentLatLng != null) {
            Log.d("MainActivity", currentLatLng.toString());
            Log.d("MainActivity", "Calling Map");
            mapView.getMapAsync(new OnMapReadyCallback() {
                @Override
                public void onMapReady(GoogleMap googleMap) {
                    Log.d("MainActivity", "Marking End Route Location");
                    if (endMarker != null) { endMarker.remove(); }
                    endMarker = addBalloonMarker(googleMap, currentLatLng, BitmapDescriptorFactory.HUE_RED, 102);
                }
            });
        }
        if (lastRouteFileName != null) {
            buttonSendRoute.setText("Email Last Route");
        }
        ScanService.setOptions(getApplicationContext(), makeScanOptions());
        Toast.makeText(this, "Route Ended", Toast.LENGTH_SHORT).show();
    }

    private PolylineOptions getEmptyPolylineOptions() {
        PolylineOptions polylineOptions = new PolylineOptions();
        polylineOptions.width(20).color(Color.rgb(255,165, 0));
        return polylineOptions;
    }

    /**
     * @param googleMap
     * @param hue One of BitmapDescriptorFactory.HUE_*;
     */
    private Marker addBalloonMarker(GoogleMap googleMap, LatLng latLng, float hue, float zIndex) {
        Marker marker = googleMap.addMarker(new MarkerOptions()
                .position(latLng)
                .zIndex(zIndex)
                .icon(BitmapDescriptorFactory.defaultMarker(hue))
        );
        return marker;
    }



    @Override
    protected void onResume() {
        super.onResume();
        registerReceiver(scanUpdateReceiver, new IntentFilter(ScanService.SCAN_UPDATE_ACTION));
        Log.d("MainActivity", "OnResume");
        checkPermissions();
    }

    private void checkPermissions() {
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
            case WIFI_STATE: permission = "wifi state"; break;
            case CHANGE_WIFI_STATE: permission = "changing wifi state"; break;
        }

        Log.d("PERMISSIONS", message + permission);
    }

    @Override
    protected void onPause() {
        super.onPause();
        Log.d("MainActivity", "OnPause");
        unregisterReceiver(scanUpdateReceiver);
    }

    public void sendEmail(String fileName) {
        Intent intent = new Intent(Intent.ACTION_SEND);
//        intent.setType("text/plain");
        intent.setType("text/csv");
//        intent.setType("text/xml");
//        intent.setType("text/json");
        intent.putExtra(Intent.EXTRA_EMAIL, new String[]{"mike.swierenga@gmail.com"});
        intent.putExtra(Intent.EXTRA_SUBJECT, "TrackDown: " + fileName);
        intent.putExtra(Intent.EXTRA_TEXT, "Message from TrackDown App");
        File file = new File(fileName);
        if (!file.exists() || !file.canRead()) {
            Toast.makeText(this, "Attachment Error", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }
//        Uri uri = Uri.fromFile(file);
        Uri uri = FileProvider.getUriForFile(this, this.getApplicationContext().getPackageName() + ".share", file);
        intent.putExtra(Intent.EXTRA_STREAM, uri);
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        try {
            startActivity(Intent.createChooser(intent, "Send email..."));
        } catch (android.content.ActivityNotFoundException ex) {
            Toast.makeText(this, "There are no email clients installed.", Toast.LENGTH_SHORT).show();
        }
    }

}
