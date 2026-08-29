//package com.example.roadqualitysensor;
//
//import android.content.Context;
//import android.content.res.AssetFileDescriptor;
//import android.graphics.Bitmap;
//import android.util.Log;
//import org.tensorflow.lite.Interpreter;
//import java.io.FileInputStream;
//import java.nio.ByteBuffer;
//import java.nio.ByteOrder;
//import java.nio.MappedByteBuffer;
//import java.nio.channels.FileChannel;
//
//public class ObjectDetectorHelper {
//    private Interpreter tflite;
//    private final int INPUT_SIZE = 640;
//
//    // Pre-allocated memory for maximum speed (Zero Garbage Collection lag)
//    private ByteBuffer inputBuffer;
//    private int[] intValues;
//    private float[][][] outputMap;
//
//    public ObjectDetectorHelper(Context context) {
//        try {
//            MappedByteBuffer model = loadModelFile(context);
//            Interpreter.Options options = new Interpreter.Options();
//            options.setNumThreads(4);
//
//            // ENABLE HARDWARE ACCELERATION
//            try {
//                // This tells Android to use the phone's dedicated AI chip
//                org.tensorflow.lite.nnapi.NnApiDelegate nnApiDelegate = new org.tensorflow.lite.nnapi.NnApiDelegate();
//                options.addDelegate(nnApiDelegate);
//                Log.d("AI_VISION", "NNAPI Hardware Acceleration Enabled!");
//            } catch (Exception e) {
//                Log.e("AI_VISION", "NNAPI not available, falling back to CPU", e);
//            }
//
//            tflite = new Interpreter(model, options);
//            // ... rest of your setup ... // Use 4 cores to process the Nano model instantly
//            tflite = new Interpreter(model, options);
//
//            // Allocate memory exactly ONCE when the app starts
//            inputBuffer = ByteBuffer.allocateDirect(1 * INPUT_SIZE * INPUT_SIZE * 3 * 4);
//            inputBuffer.order(ByteOrder.nativeOrder());
//            intValues = new int[INPUT_SIZE * INPUT_SIZE];
//
//            // Matches the 4-class RDD2022 setup you trained
//            outputMap = new float[1][8][8400];
//
//            Log.d("AI_VISION", "Nano FP16 Model Loaded Successfully!");
//        } catch (Exception e) {
//            Log.e("AI_VISION", "Model load failed: " + e.getMessage());
//        }
//    }
//
//    private MappedByteBuffer loadModelFile(Context context) throws Exception {
//        // MAKE SURE THIS MATCHES YOUR NEW FILE NAME
//        AssetFileDescriptor fileDescriptor = context.getAssets().openFd("nano_fp16.tflite");
//        FileInputStream inputStream = new FileInputStream(fileDescriptor.getFileDescriptor());
//        FileChannel fileChannel = inputStream.getChannel();
//        return fileChannel.map(FileChannel.MapMode.READ_ONLY, fileDescriptor.getStartOffset(), fileDescriptor.getDeclaredLength());
//    }
//
//    private Bitmap formatImage(Bitmap bitmap) {
//        // Create a blank 640x640 black square
//        Bitmap squareBitmap = Bitmap.createBitmap(INPUT_SIZE, INPUT_SIZE, Bitmap.Config.ARGB_8888);
//        android.graphics.Canvas canvas = new android.graphics.Canvas(squareBitmap);
//        canvas.drawColor(android.graphics.Color.BLACK);
//
//        // Shrink the camera image so it fits inside the box WITHOUT stretching it
//        float scale = Math.min((float) INPUT_SIZE / bitmap.getWidth(), (float) INPUT_SIZE / bitmap.getHeight());
//        int newWidth = Math.round(bitmap.getWidth() * scale);
//        int newHeight = Math.round(bitmap.getHeight() * scale);
//        Bitmap resized = Bitmap.createScaledBitmap(bitmap, newWidth, newHeight, true);
//
//        // Paste the camera image right in the middle of the black square
//        float left = (INPUT_SIZE - newWidth) / 2f;
//        float top = (INPUT_SIZE - newHeight) / 2f;
//        canvas.drawBitmap(resized, left, top, null);
//
//        return squareBitmap;
//    }
//
//    public Result detect(Bitmap bitmap) {
//        Result result = new Result();
//        if (tflite == null) return result;
//
//        Bitmap formattedBitmap = formatImage(bitmap);
//        inputBuffer.rewind();
//        formattedBitmap.getPixels(intValues, 0, INPUT_SIZE, 0, 0, INPUT_SIZE, INPUT_SIZE);
//
//        for (int pixel : intValues) {
//            inputBuffer.putFloat(((pixel >> 16) & 0xFF) / 255.0f);
//            inputBuffer.putFloat(((pixel >> 8) & 0xFF) / 255.0f);
//            inputBuffer.putFloat((pixel & 0xFF) / 255.0f);
//        }
//
//        tflite.run(inputBuffer, outputMap);
//
//        float maxConfidence = 0.0f;
//        int bestClass = -1;
//        int bestIndex = -1; // We need to remember exactly WHERE it saw the max confidence
//
//        for (int i = 0; i < 8400; i++) {
//            for (int r = 4; r < 8; r++) {
//                float confidence = outputMap[0][r][i];
//                if (confidence > maxConfidence) {
//                    maxConfidence = confidence;
//                    bestClass = r - 4;
//                    bestIndex = i;
//                }
//            }
//        }
//        Log.d("AI_VISION", "Conf: " + String.format("%.2f", maxConfidence) + " | Class: " + bestClass);
//        // Trigger on Class 2 (Pothole) above 30%
//        if (maxConfidence > 0 && bestClass == 2) {
//            result.detected = true;
//            result.confidence = maxConfidence;
//
//            // Extract the [x, y, width, height] from rows 0 to 3
//            float cx = outputMap[0][0][bestIndex];
//            float cy = outputMap[0][1][bestIndex];
//            float w = outputMap[0][2][bestIndex];
//            float h = outputMap[0][3][bestIndex];
//
//            // Convert center coordinates to Left/Top/Right/Bottom edges
//            result.left = cx - (w / 2);
//            result.top = cy - (h / 2);
//            result.right = cx + (w / 2);
//            result.bottom = cy + (h / 2);
//        }
//
//        return result;
//    }
//
//    // NEW: Data object to pass back to the UI
//    public static class Result {
//        public boolean detected = false;
//        public float left, top, right, bottom, confidence;
//    }
//}
package com.example.roadqualitysensor;

import android.content.Context;
import android.content.res.AssetFileDescriptor;
import android.graphics.Bitmap;
import android.util.Log;
import org.tensorflow.lite.Interpreter;
import java.io.FileInputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;

public class ObjectDetectorHelper {
    private Interpreter tflite;
    private final int INPUT_SIZE = 640;

    // Pre-allocated memory for maximum speed (Zero Garbage Collection lag)
    private ByteBuffer inputBuffer;
    private int[] intValues;
    private float[][][] outputMap;

    public ObjectDetectorHelper(Context context) {
        try {
            MappedByteBuffer model = loadModelFile(context);
            Interpreter.Options options = new Interpreter.Options();
            options.setNumThreads(4);

            // ENABLE HARDWARE ACCELERATION
            try {
                // This tells Android to use the phone's dedicated AI chip
                org.tensorflow.lite.nnapi.NnApiDelegate nnApiDelegate = new org.tensorflow.lite.nnapi.NnApiDelegate();
                options.addDelegate(nnApiDelegate);
                Log.d("AI_VISION", "NNAPI Hardware Acceleration Enabled!");
            } catch (Exception e) {
                Log.e("AI_VISION", "NNAPI not available, falling back to CPU", e);
            }

            tflite = new Interpreter(model, options);

            // Allocate memory exactly ONCE when the app starts
            inputBuffer = ByteBuffer.allocateDirect(1 * INPUT_SIZE * INPUT_SIZE * 3 * 4);
            inputBuffer.order(ByteOrder.nativeOrder());
            intValues = new int[INPUT_SIZE * INPUT_SIZE];

            // Matches the 4-class RDD2022 setup you trained (1 batch, 8 rows, 8400 columns)
            outputMap = new float[1][8][8400];

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

    private Bitmap formatImage(Bitmap bitmap) {
        // Create a blank 640x640 black square
        Bitmap squareBitmap = Bitmap.createBitmap(INPUT_SIZE, INPUT_SIZE, Bitmap.Config.ARGB_8888);
        android.graphics.Canvas canvas = new android.graphics.Canvas(squareBitmap);
        canvas.drawColor(android.graphics.Color.BLACK);

        // Shrink the camera image so it fits inside the box WITHOUT stretching it
        float scale = Math.min((float) INPUT_SIZE / bitmap.getWidth(), (float) INPUT_SIZE / bitmap.getHeight());
        int newWidth = Math.round(bitmap.getWidth() * scale);
        int newHeight = Math.round(bitmap.getHeight() * scale);
        Bitmap resized = Bitmap.createScaledBitmap(bitmap, newWidth, newHeight, true);

        // Paste the camera image right in the middle of the black square
        float left = (INPUT_SIZE - newWidth) / 2f;
        float top = (INPUT_SIZE - newHeight) / 2f;
        canvas.drawBitmap(resized, left, top, null);

        return squareBitmap;
    }

    public Result detect(Bitmap bitmap) {
        Result result = new Result();
        if (tflite == null) return result;

        Bitmap formattedBitmap = formatImage(bitmap);
        inputBuffer.rewind();
        formattedBitmap.getPixels(intValues, 0, INPUT_SIZE, 0, 0, INPUT_SIZE, INPUT_SIZE);

        // Normalize pixels from 0-255 to 0.0-1.0
        for (int pixel : intValues) {
            inputBuffer.putFloat(((pixel >> 16) & 0xFF) / 255.0f);
            inputBuffer.putFloat(((pixel >> 8) & 0xFF) / 255.0f);
            inputBuffer.putFloat((pixel & 0xFF) / 255.0f);
        }

        tflite.run(inputBuffer, outputMap);

        float maxPotholeConfidence = 0.0f;
        int bestIndex = -1;

        // YOLOv8 outputs 8 rows: [0-3] are bounding box coordinates, [4-7] are the 4 classes.
        // Pothole is Class 3. Therefore, its confidence scores are sitting in Row 7 (4 + 3 = 7).
        int potholeRow = 7;

        for (int i = 0; i < 8400; i++) {
            float confidence = outputMap[0][potholeRow][i];

            // Find the strongest POTHOLE prediction in the current camera frame
            if (confidence > maxPotholeConfidence) {
                maxPotholeConfidence = confidence;
                bestIndex = i;
            }
        }

        // Only trigger the detection if the AI is at least 30% confident it sees a pothole
        if (maxPotholeConfidence > 0.10f && bestIndex != -1) {
            result.detected = true;
            result.confidence = maxPotholeConfidence;

            // Extract the [center_x, center_y, width, height] from rows 0 to 3
            float cx = outputMap[0][0][bestIndex];
            float cy = outputMap[0][1][bestIndex];
            float w = outputMap[0][2][bestIndex];
            float h = outputMap[0][3][bestIndex];

            // Convert center coordinates to Left/Top/Right/Bottom edges for the UI Canvas
            result.left = cx - (w / 2);
            result.top = cy - (h / 2);
            result.right = cx + (w / 2);
            result.bottom = cy + (h / 2);

            Log.d("AI_VISION", "Pothole Found! Conf: " + String.format("%.2f", maxPotholeConfidence));
        }

        return result;
    }

    // Data object passed back to MainActivity to draw the box on the screen
    public static class Result {
        public boolean detected = false;
        public float left, top, right, bottom, confidence;
    }
}