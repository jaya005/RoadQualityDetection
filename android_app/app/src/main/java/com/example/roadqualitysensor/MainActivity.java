package com.example.roadqualitysensor;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Matrix;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.util.Log;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.google.common.util.concurrent.ListenableFuture;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends AppCompatActivity implements SensorEventListener, LocationListener {

    private static final int REQUEST_CODE_PERMISSIONS = 10;
    // ADDED: GPS Permission required for speed tracking
    private final String[] REQUIRED_PERMISSIONS = new String[]{
            Manifest.permission.CAMERA,
            Manifest.permission.ACCESS_FINE_LOCATION
    };

    // Hardware Sensors
    private SensorManager sensorManager;
    private Sensor linearAccelerometer;
    private LocationManager locationManager;

    // UI Elements
    private TextView jerkDataText;
    private PreviewView viewFinder;
    private BoundingBoxView boundingBoxView;

    // Background Threads & AI
    private ExecutorService cameraExecutor;
    private ObjectDetectorHelper detectorHelper;

    // THE MANAGER: Instantiate the Sensor Fusion Engine
    private SensorFusionEngine fusionEngine = new SensorFusionEngine();

    // State Variables
    private volatile boolean isPotholeDetected = false;
    private double currentLat = 0.0;
    private double currentLng = 0.0;
    private float currentSpeedMps = 0.0f; // Meters per second
    private float lastRawJerk = 0.0f;
    private float lastNormalizedJerk = 0.0f;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // NOW you can find the views
        boundingBoxView = findViewById(R.id.boundingBoxView);
        jerkDataText = findViewById(R.id.jerk_data_text);
        viewFinder = findViewById(R.id.viewFinder);

        // FIX 1: Force the bounding box canvas to render ON TOP of the camera feed
//        boundingBoxView.setElevation(100f);
//        boundingBoxView.bringToFront();
//        jerkDataText.bringToFront();
        // 1. Setup IMU (Using LINEAR_ACCELERATION to ignore gravity natively)
        sensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        if (sensorManager != null) {
            linearAccelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION);
        }

        // 2. Setup GPS
        locationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);

        cameraExecutor = Executors.newSingleThreadExecutor();

        // 3. Request Permissions
        if (allPermissionsGranted()) {
            startCamera();
            startGPS();
        } else {
            ActivityCompat.requestPermissions(this, REQUIRED_PERMISSIONS, REQUEST_CODE_PERMISSIONS);
        }
    }

    private boolean allPermissionsGranted() {
        for (String permission : REQUIRED_PERMISSIONS) {
            if (ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED) {
                return false;
            }
        }
        return true;
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_CODE_PERMISSIONS && allPermissionsGranted()) {
            startCamera();
            startGPS();
        } else {
            Toast.makeText(this, "Camera & Location permissions are required.", Toast.LENGTH_LONG).show();
            finish();
        }
    }

    private void startGPS() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            // Update location every 1000ms (1 second) or 1 meter
            locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1000, 1, this);
        }
    }

    @Override
    public void onLocationChanged(@NonNull Location location) {
        currentLat = location.getLatitude();
        currentLng = location.getLongitude();

        if (location.hasSpeed()) {
            currentSpeedMps = location.getSpeed();
        }
        runOnUiThread(this::updateUI);
    }

    private void startCamera() {
        ListenableFuture<ProcessCameraProvider> cameraProviderFuture = ProcessCameraProvider.getInstance(this);

        cameraProviderFuture.addListener(() -> {
            try {
                detectorHelper = new ObjectDetectorHelper(this);
                ProcessCameraProvider cameraProvider = cameraProviderFuture.get();

                Preview preview = new Preview.Builder().build();
                preview.setSurfaceProvider(viewFinder.getSurfaceProvider());

                ImageAnalysis imageAnalysis = new ImageAnalysis.Builder()
                        .setTargetResolution(new android.util.Size(640, 480))
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        .build();

                imageAnalysis.setAnalyzer(cameraExecutor, imageProxy -> {
                    try {
                        Bitmap bitmap = imageProxy.toBitmap();
                        int rotation = imageProxy.getImageInfo().getRotationDegrees();

                        Matrix matrix = new Matrix();
                        matrix.postRotate(rotation);
                        Bitmap rotatedBitmap = Bitmap.createBitmap(
                                bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(), matrix, true
                        );

                        if (detectorHelper != null) {
                            ObjectDetectorHelper.Result result = detectorHelper.detect(rotatedBitmap);

                            if (boundingBoxView != null) {
                                runOnUiThread(() -> boundingBoxView.setResults(result));
                            }

                            // AI -> MANAGER WIRE UP
                            if (result.detected) {
                                // Send the visual detection to the SensorFusionEngine queue
                                fusionEngine.addVisionDetection("Pothole");
                            }

                            if (isPotholeDetected != result.detected) {
                                isPotholeDetected = result.detected;
                                runOnUiThread(this::updateUI);
                            }
                        }
                    } catch (Exception e) {
                        Log.e("CameraX", "Frame processing error", e);
                    } finally {
                        imageProxy.close();
                    }
                });

                CameraSelector cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA;
                cameraProvider.unbindAll();
                cameraProvider.bindToLifecycle(this, cameraSelector, preview, imageAnalysis);

            } catch (ExecutionException | InterruptedException e) {
                Log.e("CameraX", "Use case binding failed", e);
            }
        }, ContextCompat.getMainExecutor(this));
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        if (event.sensor.getType() == Sensor.TYPE_LINEAR_ACCELERATION) {
            // Z-axis is up/down movement.
            // Because we use LINEAR_ACCELERATION, gravity is already removed!
            float zAxisAcceleration = event.values[2];
            lastRawJerk = Math.abs(zAxisAcceleration);

            // ==========================================
            // RESEARCH FEATURE 1: ZERO-SPEED MASKING
            // ==========================================
            // If driving slower than 1.38 m/s (approx 5 km/h), ignore all bumps.
            // This prevents false positives when stopped at a red light.
            if (currentSpeedMps < 1.38f) {
                lastNormalizedJerk = 0.0f;
                runOnUiThread(this::updateUI);
                return;
            }

            lastNormalizedJerk = lastRawJerk / currentSpeedMps;

            String severity = "Normal";

            // These thresholds are now dynamic relative to vehicle speed!
            if (lastNormalizedJerk > 2.5f) {
                severity = "Severe";
            } else if (lastNormalizedJerk > 1.0f) {
                severity = "Moderate";
            } else if (lastNormalizedJerk > 0.5f) {
                severity = "Minor";
            }

            // IMU -> MANAGER WIRE UP
            // IMU -> MANAGER WIRE UP
            if (!severity.equals("Normal")) {
                Log.d("PHYSICS_ENGINE", "Physical Bump Detected: " + severity);

                // Convert speed to km/h for the database
                float speedKmh = currentSpeedMps * 3.6f;
                fusionEngine.addIMUDetection(severity, currentLat, currentLng, speedKmh);
            }

            runOnUiThread(this::updateUI);
        }
    }

    private void updateUI() {
        // Convert m/s to km/h for the UI display
        float speedKmh = currentSpeedMps * 3.6f;

        String displayText = String.format(
                "Speed: %.1f km/h\nRaw Jerk: %.2f\nNorm. Jerk: %.2f",
                speedKmh, lastRawJerk, lastNormalizedJerk
        );

        if (isPotholeDetected) {
            displayText += "\n\n[AI]: ⚠️ POTHOLE SEEN ⚠️";
        }

        jerkDataText.setText(displayText);
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {}

    @Override
    protected void onResume() {
        super.onResume();
        if (linearAccelerometer != null) {
            sensorManager.registerListener(this, linearAccelerometer, SensorManager.SENSOR_DELAY_NORMAL);
        }
        startGPS();
    }

    @Override
    protected void onPause() {
        super.onPause();
        sensorManager.unregisterListener(this);
        locationManager.removeUpdates(this);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        cameraExecutor.shutdown();
    }
}