# Road Quality Sensor

A real-time Android application and machine learning pipeline designed for comprehensive road surface analysis. This project integrates CameraX computer vision tracking with linear accelerometer sensor stream metrics to simultaneously identify visual road damages and classify physical jerk severity.

## Project Structure
* **`android_app/`**: Java-based Android Studio project managing real-time frame analysis, hardware sensor fusion, and UI rendering.
* **`model_training/`**: Python and Kaggle scripts for dataset ingestion and neural network training[cite: 1].

## Machine Learning Pipeline
* **Architecture:** YOLOv8-Nano optimized for mobile environments[cite: 1].
* **Dataset:** RDD-2022 Split, trained across 4 damage classes (D00, D10, D20, D40/Pothole)[cite: 1].
* **Training Specs:** 50 epochs at 640 image size with batch size 16[cite: 1].
* **Deployment:** Exported as a lightweight 6MB Float16 `.tflite` format for edge device inference[cite: 1].

## Android Application Components
* **`MainActivity.java`**: Handles primary application lifecycle and component initialization[cite: 2].
* **`ObjectDetectorHelper.java`**: Consumes the `.tflite` model to run real-time inference on incoming CameraX frames[cite: 2].
* **`SensorFusionEngine.java`**: Synchronizes visual detection data with the linear accelerometer stream to calculate and categorize physical jerk severity[cite: 2].
* **`BoundingBoxView.java`**: Custom view layer for drawing hardware-accelerated bounding boxes over recognized anomalies[cite: 2].
* **`AnomalyRecord.java`**: Data structure for logging combined visual and physical damage events[cite: 2].

## Setup & Installation
1. Navigate to the `model_training/` directory and execute `dip-2.ipynb` to output the `nano_fp16.tflite` model[cite: 1].
2. Place the generated `.tflite` file and your `labels.txt` inside the `android_app/app/src/main/assets/` directory.
3. Open the `android_app` directory in Android Studio.
4. Sync Gradle files and deploy to a physical Android testing device to utilize the camera and accelerometer hardware.
