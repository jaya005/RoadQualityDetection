package com.example.roadqualitysensor;

import android.content.Context;
import android.content.res.AssetFileDescriptor;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.util.Log;
import org.tensorflow.lite.Interpreter;
import org.tensorflow.lite.nnapi.NnApiDelegate;

import java.io.FileInputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;

public class ObjectDetectorHelper {
    private Interpreter tflite;
    private final int INPUT_SIZE = 640;

    // Pre-allocated memory for neural network input
    private ByteBuffer inputBuffer;
    private int[] intValues;
    private float[][][] outputMap;

    // Pre-allocated graphics objects (Eliminates Garbage Collection stutter)
    private Bitmap squareBitmap;
    private Canvas squareCanvas;
    private Matrix transformMatrix;

    // OFFICIAL RDD2022 CLASS MAPPING FROM YOUR DATASET CONFIG
    private final String[] CLASS_LABELS = {"Longitudinal Crack", "Transverse Crack", "Alligator Crack", "Pothole"};
    private final float CONFIDENCE_THRESHOLD = 0.35f;

    public ObjectDetectorHelper(Context context) {
        try {
            MappedByteBuffer model = loadModelFile(context);
            Interpreter.Options options = new Interpreter.Options();
            options.setNumThreads(4);

            // ENABLE HARDWARE ACCELERATION
            try {
                NnApiDelegate nnApiDelegate = new NnApiDelegate();
                options.addDelegate(nnApiDelegate);
                Log.d("AI_VISION", "NNAPI Hardware Acceleration Enabled!");
            } catch (Exception e) {
                Log.e("AI_VISION", "NNAPI not available, falling back to CPU", e);
            }

            tflite = new Interpreter(model, options);

            // 1. Allocate TFLite buffers exactly ONCE
            inputBuffer = ByteBuffer.allocateDirect(1 * INPUT_SIZE * INPUT_SIZE * 3 * 4);
            inputBuffer.order(ByteOrder.nativeOrder());
            intValues = new int[INPUT_SIZE * INPUT_SIZE];
            outputMap = new float[1][8][8400]; // [Batch][4 coords + 4 classes][Anchors]

            // 2. Allocate Android Graphics objects exactly ONCE
            squareBitmap = Bitmap.createBitmap(INPUT_SIZE, INPUT_SIZE, Bitmap.Config.ARGB_8888);
            squareCanvas = new Canvas(squareBitmap);
            transformMatrix = new Matrix();

            Log.d("AI_VISION", "Nano FP16 Model Loaded Successfully!");
        } catch (Exception e) {
            Log.e("AI_VISION", "Model load failed: " + e.getMessage());
        }
    }

    private MappedByteBuffer loadModelFile(Context context) throws Exception {
        AssetFileDescriptor fileDescriptor = context.getAssets().openFd("nano_fp16.tflite");
        FileInputStream inputStream = new FileInputStream(fileDescriptor.getFileDescriptor());
        FileChannel fileChannel = inputStream.getChannel();
        return fileChannel.map(FileChannel.MapMode.READ_ONLY, fileDescriptor.getStartOffset(), fileDescriptor.getDeclaredLength());
    }

    public Result detect(Bitmap bitmap, int rotation) {
        Result result = new Result();
        if (tflite == null) return result;

        // --- ZERO-ALLOCATION IMAGE FORMATTING ---
        squareCanvas.drawColor(Color.rgb(114, 114, 114)); // YOLO neutral gray padding
        transformMatrix.reset();

        boolean isRotated = (rotation % 180 != 0);
        int rotatedWidth = isRotated ? bitmap.getHeight() : bitmap.getWidth();
        int rotatedHeight = isRotated ? bitmap.getWidth() : bitmap.getHeight();
        float scale = Math.min((float) INPUT_SIZE / rotatedWidth, (float) INPUT_SIZE / rotatedHeight);

        transformMatrix.postTranslate(-bitmap.getWidth() / 2f, -bitmap.getHeight() / 2f);
        transformMatrix.postRotate(rotation);
        transformMatrix.postScale(scale, scale);
        transformMatrix.postTranslate(INPUT_SIZE / 2f, INPUT_SIZE / 2f);

        squareCanvas.drawBitmap(bitmap, transformMatrix, null);

        // --- PIXEL EXTRACTION & NORMALIZATION ---
        inputBuffer.rewind();
        squareBitmap.getPixels(intValues, 0, INPUT_SIZE, 0, 0, INPUT_SIZE, INPUT_SIZE);

        for (int pixel : intValues) {
            inputBuffer.putFloat(((pixel >> 16) & 0xFF) / 255.0f);
            inputBuffer.putFloat(((pixel >> 8) & 0xFF) / 255.0f);
            inputBuffer.putFloat((pixel & 0xFF) / 255.0f);
        }

        // --- INFERENCE ---
        tflite.run(inputBuffer, outputMap);

        // --- FULLY DYNAMIC MULTI-CLASS PARSING LOOP ---
        float maxConfidence = 0.0f;
        int bestIndex = -1;
        int bestClassIndex = -1;

        for (int i = 0; i < 8400; i++) {
            for (int c = 0; c < CLASS_LABELS.length; c++) {
                // YOLOv8 class probabilities begin at row index 4 onwards
                float confidence = outputMap[0][4 + c][i];
                if (confidence > maxConfidence) {
                    maxConfidence = confidence;
                    bestIndex = i;
                    bestClassIndex = c;
                }
            }
        }

        // Trigger detection if confidence crosses threshold
        if (maxConfidence > CONFIDENCE_THRESHOLD && bestIndex != -1) {
            result.detected = true;
            result.confidence = maxConfidence;
            result.label = CLASS_LABELS[bestClassIndex];

            // Extract bounding box dimensions [cx, cy, w, h] from rows 0 to 3
            float cx = outputMap[0][0][bestIndex];
            float cy = outputMap[0][1][bestIndex];
            float w = outputMap[0][2][bestIndex];
            float h = outputMap[0][3][bestIndex];

            result.left = cx - (w / 2f);
            result.top = cy - (h / 2f);
            result.right = cx + (w / 2f);
            result.bottom = cy + (h / 2f);

            Log.d("AI_VISION", result.label + " Detected! Conf: " + String.format("%.2f", maxConfidence));
        }

        return result;
    }

    public static class Result {
        public boolean detected = false;
        public String label = "";
        public float left, top, right, bottom, confidence;
    }
}