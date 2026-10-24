package com.rampey.materialtv;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.View;

/** Static, allocation-free temperature range renderer for the Home weather widget. */
final class TemperatureRangeView extends View {
    private final Paint track = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint halo = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint dot = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF trackBounds = new RectF();
    private final RectF fillBounds = new RectF();
    private final float lineHalf, dotRadius, haloRadius;
    private float left, right, centerY;
    private double position = Double.NaN;
    private int trackColor, fillColor, dotColor;

    TemperatureRangeView(Context context) {
        super(context);
        float density = getResources().getDisplayMetrics().density;
        lineHalf = 1.75f * density;
        dotRadius = 4.5f * density;
        haloRadius = 7f * density;
        track.setStyle(Paint.Style.FILL);
        fill.setStyle(Paint.Style.FILL);
        halo.setStyle(Paint.Style.FILL);
        dot.setStyle(Paint.Style.FILL);
    }

    void setTemperatures(double current, double min, double max) {
        double next = WeatherPolicy.position(current, min, max);
        if (Double.compare(position, next) == 0) return;
        position = next;
        invalidate();
    }

    void setColors(int muted, int accent, int ink) {
        if (trackColor == muted && fillColor == accent && dotColor == ink) return;
        trackColor = muted;
        fillColor = accent;
        dotColor = ink;
        track.setColor(muted);
        fill.setColor(accent);
        halo.setColor((accent & 0x00ffffff) | 0x33000000);
        dot.setColor(ink);
        invalidate();
    }

    @Override protected void onSizeChanged(int w, int h, int oldW, int oldH) {
        super.onSizeChanged(w, h, oldW, oldH);
        left = haloRadius;
        right = w - haloRadius;
        centerY = h / 2f;
        trackBounds.set(left, centerY - lineHalf, right, centerY + lineHalf);
    }

    @Override protected void onDraw(Canvas canvas) {
        if (Double.isNaN(position) || right <= left || getHeight() < 2 * haloRadius) return;
        float x = left + (float) position * (right - left);
        canvas.drawRoundRect(trackBounds, lineHalf, lineHalf, track);
        fillBounds.set(left, centerY - lineHalf, x, centerY + lineHalf);
        if (x > left) canvas.drawRoundRect(fillBounds, lineHalf, lineHalf, fill);
        canvas.drawCircle(x, centerY, haloRadius, halo);
        canvas.drawCircle(x, centerY, dotRadius, dot);
    }
}
