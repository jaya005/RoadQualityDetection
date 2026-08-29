package com.example.roadqualitysensor;

public class AnomalyRecord {
    public double latitude;
    public double longitude;
    public String severity;
    public float speedKmh;
    public long timestamp;

    // Firebase REQUIRES an empty constructor
    public AnomalyRecord() {}

    public AnomalyRecord(double latitude, double longitude, String severity, float speedKmh) {
        this.latitude = latitude;
        this.longitude = longitude;
        this.severity = severity;
        this.speedKmh = speedKmh;
        this.timestamp = System.currentTimeMillis();
    }
}