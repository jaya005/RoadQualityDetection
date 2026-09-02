package com.example.roadqualitysensor;

import android.util.Log;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;

import java.util.Iterator;
import java.util.LinkedList;

public class SensorFusionEngine {
    private static final String TAG = "SensorFusion";

    // Research Optimization: 1.5s is too long. At 40km/h, a car travels 16 meters in 1.5s.
    // We tighten the window to 800ms. We also allow a -200ms buffer because the physical
    // IMU spike might register *before* the CameraX background thread finishes running
    // the AI model and logging the vision timestamp.
    private static final long MAX_TIME_DIFF_MS = 800;
    private static final long MIN_TIME_DIFF_MS = -200;

    private final LinkedList<VisionEvent> visionQueue = new LinkedList<>();
    private final LinkedList<IMUEvent> imuQueue = new LinkedList<>();

    public static class VisionEvent {
        long timestamp;
        String detectionClass;
        public VisionEvent(long ts, String detClass) {
            this.timestamp = ts;
            this.detectionClass = detClass;
        }
    }

    public static class IMUEvent {
        long timestamp;
        String severity;
        double lat, lng;
        float speed;
        public IMUEvent(long ts, String sev, double lat, double lng, float speed) {
            this.timestamp = ts;
            this.severity = sev;
            this.lat = lat;
            this.lng = lng;
            this.speed = speed;
        }
    }

    public synchronized void addVisionDetection(String detectionClass) {
        visionQueue.addLast(new VisionEvent(System.currentTimeMillis(), detectionClass));
        cleanOldEvents();
    }

    public synchronized void addIMUDetection(String severity, double lat, double lng, float speedKmh) {
        long currentTime = System.currentTimeMillis();
        imuQueue.addLast(new IMUEvent(currentTime, severity, lat, lng, speedKmh));

        if (!severity.equals("Normal")) {
            evaluateDecisionMatrix(currentTime, severity, lat, lng, speedKmh);
        }
        cleanOldEvents();
    }

    private synchronized void evaluateDecisionMatrix(long imuTimestamp, String jerkSeverity, double lat, double lng, float speedKmh) {
        Iterator<VisionEvent> iterator = visionQueue.iterator();

        while (iterator.hasNext()) {
            VisionEvent vEvent = iterator.next();
            long timeDifference = imuTimestamp - vEvent.timestamp;

            if (timeDifference >= MIN_TIME_DIFF_MS && timeDifference <= MAX_TIME_DIFF_MS) {
                Log.d(TAG, "CONFIRMED ANOMALY! Visual: " + vEvent.detectionClass +
                        " | Physical: " + jerkSeverity +
                        " | Time Delta: " + timeDifference + "ms");

                if (lat != 0.0 && lng != 0.0) {
                    DatabaseReference database = FirebaseDatabase.getInstance().getReference("anomalies");
                    AnomalyRecord record = new AnomalyRecord(lat, lng, jerkSeverity, speedKmh,vEvent.detectionClass);
                    database.push().setValue(record);
                    Log.d(TAG, "Successfully uploaded to Firebase Database!");
                } else {
                    Log.w(TAG, "Anomaly confirmed, but GPS lock was missing. Skipping upload.");
                }

                // Remove ONLY the matched visual event, leave the rest of the queue intact
                // for rapid successive potholes.
                iterator.remove();
                break;
            }
        }
    }

    private void cleanOldEvents() {
        // Reduced memory footprint by clearing anything older than 1.5 seconds immediately
        long cutoffTime = System.currentTimeMillis() - 1500;
        visionQueue.removeIf(event -> event.timestamp < cutoffTime);
        imuQueue.removeIf(event -> event.timestamp < cutoffTime);
    }
}