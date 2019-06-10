package com.example.trackdown;

import java.io.Serializable;

public class ScanUpdate implements Serializable {
    public static final String EXTRA_SCAN_UPDATE = "com.example.trackdown.extra.SCAN_UPDATE";
    public long timestamp;
    public double latitude;
    public double longitude;
    public String locationProvider;
    public float locationSpeedMetersPerSecond;
    public float locationRadiusMeters;
    public String csvFileName;
}
