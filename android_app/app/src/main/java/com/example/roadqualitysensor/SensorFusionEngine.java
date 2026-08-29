package com.example.roadqualitysensor;

import android.util.Log;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import java.util.LinkedList;

public class SensorFusionEngine {
    private static final String TAG = "SensorFusion";
    private static final long MAX_TIME_DIFF_MS = 1500;

    private final LinkedList<VisionEvent> visionQueue = new LinkedList<>();
    private final LinkedList<IMUEvent> imuQueue = new LinkedList<>();

    public static class VisionEvent {
        long timestamp;
        String detectionClass;
        public VisionEvent(long ts, String detClass) { this.timestamp = ts; this.detectionClass = detClass; }
    }

    public static class IMUEvent {
        long timestamp;
        String severity;
        double lat, lng;
        float speed;
        public IMUEvent(long ts, String sev, double lat, double lng, float speed) {
            this.timestamp = ts; this.severity = sev; this.lat = lat; this.lng = lng; this.speed = speed;
        }
    }

    public synchronized void addVisionDetection(String detectionClass) {
        visionQueue.addLast(new VisionEvent(System.currentTimeMillis(), detectionClass));
        cleanOldEvents();
    }

    // UPDATED: Now receives the GPS data from MainActivity
    public synchronized void addIMUDetection(String severity, double lat, double lng, float speedKmh) {
        long currentTime = System.currentTimeMillis();
        imuQueue.addLast(new IMUEvent(currentTime, severity, lat, lng, speedKmh));

        if (!severity.equals("Normal")) {
            evaluateDecisionMatrix(currentTime, severity, lat, lng, speedKmh);
        }
        cleanOldEvents();
    }

    private void evaluateDecisionMatrix(long imuTimestamp, String jerkSeverity, double lat, double lng, float speedKmh) {
        boolean visualMatchFound = false;

        for (VisionEvent vEvent : visionQueue) {
            long timeDifference = imuTimestamp - vEvent.timestamp;

            // If the camera saw a pothole within 1.5 seconds of the physical hit...
            if (timeDifference >= 0 && timeDifference <= MAX_TIME_DIFF_MS) {
                visualMatchFound = true;

                Log.d(TAG, "CONFIRMED ANOMALY! Visual: " + vEvent.detectionClass + " | Physical: " + jerkSeverity);

                // ==========================================
                // CLOUD BRIDGE: PUSH TO FIREBASE
                // ==========================================
                // Ensure we have a valid GPS lock before uploading
                if (lat != 0.0 && lng != 0.0) {
                    DatabaseReference database = FirebaseDatabase.getInstance().getReference("anomalies");
                    AnomalyRecord record = new AnomalyRecord(lat, lng, jerkSeverity, speedKmh);

                    // .push() creates a unique ID for this specific pothole
                    database.push().setValue(record);
                    Log.d(TAG, "Successfully uploaded to Firebase Database!");
                } else {
                    Log.w(TAG, "Anomaly confirmed, but GPS lock was missing. Skipping upload.");
                }

                // Clear the queues to prevent double-counting
                visionQueue.clear();
                imuQueue.clear();
                break;
            }
        }
    }

    private void cleanOldEvents() {
        long cutoffTime = System.currentTimeMillis() - 2000;
        visionQueue.removeIf(event -> event.timestamp < cutoffTime);
        imuQueue.removeIf(event -> event.timestamp < cutoffTime);
    }
}