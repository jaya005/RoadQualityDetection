package com.example.roadqualitysensor;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.util.Log;
import android.util.Size;
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
    private final String[] REQUIRED_PERMISSIONS = new String[]{
            Manifest.permission.CAMERA,
            Manifest.permission.ACCESS_FINE_LOCATION
    };

    private SensorManager sensorManager;
    private Sensor linearAccelerometer;
    private LocationManager locationManager;

    private TextView jerkDataText;
    private PreviewView viewFinder;
    private BoundingBoxView boundingBoxView;

    private ExecutorService cameraExecutor;
    private ObjectDetectorHelper detectorHelper;
    private SensorFusionEngine fusionEngine = new SensorFusionEngine();

    // Dedicated thread for high-frequency sensor polling
    private HandlerThread sensorThread;
    private Handler sensorHandler;

    private volatile boolean isPotholeDetected = false;
    private String currentAnomalyLabel = "Pothole";
    private double currentLat = 0.0;
    private double currentLng = 0.0;
    private float currentSpeedMps = 0.0f;
    private float lastRawJerk = 0.0f;
    private float lastNormalizedJerk = 0.0f;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        boundingBoxView = findViewById(R.id.boundingBoxView);
        jerkDataText = findViewById(R.id.jerk_data_text);
        viewFinder = findViewById(R.id.viewFinder);

        sensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        if (sensorManager != null) {
            linearAccelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION);
        }

        locationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
        cameraExecutor = Executors.newSingleThreadExecutor();

        // Initialize background thread for sensors
        sensorThread = new HandlerThread("SensorThread");
        sensorThread.start();
        sensorHandler = new Handler(sensorThread.getLooper());

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
            Toast.makeText(this, "Permissions required.", Toast.LENGTH_LONG).show();
            finish();
        }
    }

    private void startGPS() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
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
                        .setTargetResolution(new Size(640, 640)) // Match your model input size
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888) // Avoid YUV conversion
                        .build();

                imageAnalysis.setAnalyzer(cameraExecutor, imageProxy -> {
                    try {
                        Bitmap bitmap = imageProxy.toBitmap();
                        int rotation = imageProxy.getImageInfo().getRotationDegrees();

                        if (detectorHelper != null) {
                            // Pass the unrotated bitmap and rotation to the helper
                            ObjectDetectorHelper.Result result = detectorHelper.detect(bitmap, rotation);

                            if (boundingBoxView != null) {
                                runOnUiThread(() -> boundingBoxView.setResults(result));
                            }



                            // Inside startCamera() -> setAnalyzer:
                            if (result.detected) {
                                fusionEngine.addVisionDetection(result.label);
                                currentAnomalyLabel = result.label; // Store for the UI
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
                Log.e("CameraX", "Binding failed", e);
            }
        }, ContextCompat.getMainExecutor(this));
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        if (event.sensor.getType() == Sensor.TYPE_LINEAR_ACCELERATION) {
            float zAxisAcceleration = event.values[2];
            lastRawJerk = Math.abs(zAxisAcceleration);

            if (currentSpeedMps < 1.38f) {
                lastNormalizedJerk = 0.0f;
                runOnUiThread(this::updateUI);
                return;
            }

            lastNormalizedJerk = lastRawJerk / currentSpeedMps;
            String severity = "Normal";

            if (lastNormalizedJerk > 2.5f) {
                severity = "Severe";
            } else if (lastNormalizedJerk > 1.0f) {
                severity = "Moderate";
            } else if (lastNormalizedJerk > 0.5f) {
                severity = "Minor";
            }

            if (!severity.equals("Normal")) {
                float speedKmh = currentSpeedMps * 3.6f;
                fusionEngine.addIMUDetection(severity, currentLat, currentLng, speedKmh);
            }
            runOnUiThread(this::updateUI);
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {}
    private void updateUI() {
        // Convert m/s to km/h for the UI display
        float speedKmh = currentSpeedMps * 3.6f;

        String displayText = String.format(
                "Speed: %.1f km/h\nRaw Jerk: %.2f\nNorm. Jerk: %.2f",
                speedKmh, lastRawJerk, lastNormalizedJerk
        );

        if (isPotholeDetected) {
            displayText += "\n\n[AI]: ⚠️ " + currentAnomalyLabel.toUpperCase() + " SEEN ⚠️";
        }

        if (jerkDataText != null) {
            jerkDataText.setText(displayText);
        }
    }
    @Override
    protected void onResume() {
        super.onResume();
        if (linearAccelerometer != null) {
            // Use SENSOR_DELAY_GAME for faster polling required for jerk detection
            sensorManager.registerListener(this, linearAccelerometer, SensorManager.SENSOR_DELAY_GAME, sensorHandler);
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
        sensorThread.quitSafely();
    }
}