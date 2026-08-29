package com.example.roadqualitysensor;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.View;

public class BoundingBoxView extends View {
    private Paint boxPaint;
    private Paint textPaint;
    private ObjectDetectorHelper.Result currentResult;

    public BoundingBoxView(Context context, AttributeSet attrs) {
        super(context, attrs);

        // Setup the Red Box
        boxPaint = new Paint();
        boxPaint.setColor(Color.RED);
        boxPaint.setStyle(Paint.Style.STROKE);
        boxPaint.setStrokeWidth(8f);

        // Setup the Text Label
        textPaint = new Paint();
        textPaint.setColor(Color.RED);
        textPaint.setTextSize(55f);
        textPaint.setStyle(Paint.Style.FILL);
        textPaint.setFakeBoldText(true);
    }

    public void setResults(ObjectDetectorHelper.Result result) {
        this.currentResult = result;
        invalidate(); // Triggers the screen to redraw instantly
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        if (currentResult != null && currentResult.detected) {
            // The AI outputs coordinates based on a 640x640 image.
            // We must scale those coordinates to fit your physical phone screen.
//            boolean isNormalized = currentResult.right <= 2.0f && currentResult.bottom <= 2.0f;
//
//            // Dynamically scale based on the model's format
//            float scaleX = isNormalized ? getWidth() : (float) getWidth() / 640f;
//            float scaleY = isNormalized ? getHeight() : (float) getHeight() / 640f;
            float scaleX = (float) getWidth() / 640f;
            float scaleY = (float) getHeight() / 640f;

            float left = currentResult.left * scaleX;
            float top = currentResult.top * scaleY;
            float right = currentResult.right * scaleX;
            float bottom = currentResult.bottom * scaleY;

            // Draw the box and the confidence percentage
            canvas.drawRect(left, top, right, bottom, boxPaint);

            String label = "Pothole " + Math.round(currentResult.confidence * 100) + "%";
            canvas.drawText(label, left, top - 15, textPaint);
        }
    }
}