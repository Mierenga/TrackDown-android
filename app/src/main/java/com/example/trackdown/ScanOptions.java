package com.example.trackdown;

import java.io.Serializable;

public class ScanOptions implements Serializable {
    public static final String EXTRA_SCAN_OPTIONS = "com.example.trackdown.extra.SCAN_OPTIONS";
    public boolean active = true;
    public boolean logLocation = false;
    public boolean logWifi = false;
}
