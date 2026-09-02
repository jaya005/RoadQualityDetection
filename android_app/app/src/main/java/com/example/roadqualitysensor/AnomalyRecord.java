package com.example.roadqualitysensor;

public class AnomalyRecord {
    public double latitude;
    public double longitude;
    public String imuSeverity;
    public float speedKmh;
    public long timestamp;

    // Added for Sensor Fusion Research
    public String visualClass;

    // Firebase REQUIRES an empty constructor
    public AnomalyRecord() {}

    public AnomalyRecord(double latitude, double longitude, String imuSeverity, float speedKmh, String visualClass) {
        this.latitude = latitude;
        this.longitude = longitude;
        this.imuSeverity = imuSeverity;
        this.speedKmh = speedKmh;
        this.visualClass = visualClass;
        this.timestamp = System.currentTimeMillis();
    }
}