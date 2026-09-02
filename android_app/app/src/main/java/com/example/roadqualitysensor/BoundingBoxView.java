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

    // Zero-allocation text building to prevent GC stutter during onDraw
    private final StringBuilder labelBuilder = new StringBuilder();

    public BoundingBoxView(Context context, AttributeSet attrs) {
        super(context, attrs);

        boxPaint = new Paint();
        boxPaint.setColor(Color.RED);
        boxPaint.setStyle(Paint.Style.STROKE);
        boxPaint.setStrokeWidth(8f);

        textPaint = new Paint();
        textPaint.setColor(Color.RED);
        textPaint.setTextSize(55f);
        textPaint.setStyle(Paint.Style.FILL);
        textPaint.setFakeBoldText(true);
    }

    public void setResults(ObjectDetectorHelper.Result result) {
        this.currentResult = result;
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        if (currentResult != null && currentResult.detected) {
            // Because ObjectDetectorHelper letterboxes the image into a 640x640 square,
            // stretching X and Y independently will warp the bounding box.
            // We use Math.max to simulate CameraX's FILL_CENTER scaling behavior.
            float scale = Math.max((float) getWidth() / 640f, (float) getHeight() / 640f);

            // Calculate offsets to center the scaled 640x640 grid over the screen
            float offsetX = (getWidth() - (640f * scale)) / 2f;
            float offsetY = (getHeight() - (640f * scale)) / 2f;

            float left = (currentResult.left * scale) + offsetX;
            float top = (currentResult.top * scale) + offsetY;
            float right = (currentResult.right * scale) + offsetX;
            float bottom = (currentResult.bottom * scale) + offsetY;

            canvas.drawRect(left, top, right, bottom, boxPaint);

            // Reuse StringBuilder instead of allocating a new String every frame
// Inside onDraw(), replace the old labelBuilder logic:
            labelBuilder.setLength(0);
            labelBuilder.append(currentResult.label)
                    .append(" ")
                    .append(Math.round(currentResult.confidence * 100))
                    .append("%");

            canvas.drawText(labelBuilder.toString(), left, top - 15, textPaint);        }
    }
}