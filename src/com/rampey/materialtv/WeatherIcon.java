package com.rampey.materialtv;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.view.View;

/** Small static Material-toned weather mark; no downloaded icons or animation loop. */
final class WeatherIcon extends View {
    private static final int CLEAR = 0, PARTLY = 1, CLOUD = 2, RAIN = 3, STORM = 4,
            FOG = 5, GENERIC = 6, SNOW = 7;
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private int kind = GENERIC, accent, muted;
    private boolean day = true;

    WeatherIcon(Context context) { super(context); }

    void setColors(int accent, int muted) {
        this.accent = accent;
        this.muted = muted;
        invalidate();
    }

    void setWeather(int code, int isDay) {
        day = isDay != 0;
        if (code == 0) kind = CLEAR;
        else if (code == 1 || code == 2) kind = PARTLY;
        else if (code == 3) kind = CLOUD;
        else if (code == 45 || code == 48) kind = FOG;
        else if (code >= 95 && code <= 99) kind = STORM;
        else if (code >= 71 && code <= 77 || code == 85 || code == 86) kind = SNOW;
        else if (code >= 51 && code <= 67 || code >= 80 && code <= 82) kind = RAIN;
        else kind = GENERIC;
        invalidate();
    }

    void setRainMark() {
        kind = RAIN;
        invalidate();
    }

    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        int save = canvas.save();
        canvas.translate((getWidth() - Math.min(getWidth(), getHeight())) / 2f,
                (getHeight() - Math.min(getWidth(), getHeight())) / 2f);
        canvas.scale(Math.min(getWidth(), getHeight()) / 48f, Math.min(getWidth(), getHeight()) / 48f);
        paint.setStrokeCap(Paint.Cap.ROUND);
        paint.setStrokeJoin(Paint.Join.ROUND);
        if (kind == CLEAR) {
            sky(canvas, 23, 24, 9);
        } else if (kind == PARTLY) {
            sky(canvas, 18, 18, 7);
            cloud(canvas);
        } else if (kind == CLOUD || kind == RAIN || kind == STORM || kind == FOG
                || kind == GENERIC || kind == SNOW) {
            cloud(canvas);
            if (kind == RAIN) {
                paint.setColor(accent);
                paint.setStyle(Paint.Style.STROKE);
                paint.setStrokeWidth(3);
                canvas.drawLine(17, 36, 14, 41, paint);
                canvas.drawLine(26, 36, 23, 41, paint);
                canvas.drawLine(35, 36, 32, 41, paint);
            } else if (kind == STORM) {
                paint.setColor(accent);
                paint.setStyle(Paint.Style.FILL);
                Path bolt = new Path();
                bolt.moveTo(26, 34); bolt.lineTo(20, 42); bolt.lineTo(26, 40);
                bolt.lineTo(23, 47); bolt.lineTo(34, 37); bolt.lineTo(28, 38); bolt.close();
                canvas.drawPath(bolt, paint);
            } else if (kind == FOG) {
                paint.setColor(muted);
                paint.setStyle(Paint.Style.STROKE);
                paint.setStrokeWidth(2.5f);
                canvas.drawLine(12, 38, 36, 38, paint);
                canvas.drawLine(17, 43, 32, 43, paint);
            } else if (kind == SNOW) {
                paint.setColor(accent);
                paint.setStyle(Paint.Style.STROKE);
                paint.setStrokeWidth(2.5f);
                canvas.drawLine(18, 38, 18, 44, paint);
                canvas.drawLine(15, 41, 21, 41, paint);
                canvas.drawLine(33, 38, 33, 44, paint);
                canvas.drawLine(30, 41, 36, 41, paint);
            }
        }
        canvas.restoreToCount(save);
    }

    private void sky(Canvas canvas, float x, float y, float radius) {
        paint.setColor(accent);
        paint.setStyle(Paint.Style.FILL);
        if (day) {
            canvas.drawCircle(x, y, radius, paint);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(2.6f);
            for (int i = 0; i < 8; i++) {
                double angle = i * Math.PI / 4;
                canvas.drawLine(x + (float) Math.cos(angle) * (radius + 4),
                        y + (float) Math.sin(angle) * (radius + 4),
                        x + (float) Math.cos(angle) * (radius + 8),
                        y + (float) Math.sin(angle) * (radius + 8), paint);
            }
        } else {
            Path moon = new Path();
            moon.addCircle(x, y, radius + 2, Path.Direction.CW);
            Path cut = new Path();
            cut.addCircle(x + 5, y - 4, radius + 1, Path.Direction.CW);
            moon.op(cut, Path.Op.DIFFERENCE);
            canvas.drawPath(moon, paint);
        }
    }

    private void cloud(Canvas canvas) {
        paint.setColor(muted);
        paint.setStyle(Paint.Style.FILL);
        canvas.drawCircle(20, 26, 8, paint);
        canvas.drawCircle(29, 22, 10, paint);
        canvas.drawCircle(37, 28, 7, paint);
        canvas.drawRoundRect(11, 25, 43, 35, 5, 5, paint);
    }
}
