package com.inania.Anthuria;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PointF;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;

public class Wall {
    public PointF start, end;
    /** Wall stroke thickness in plan pixels (default 20). */
    public float thicknessPx = 20f;
    /** Optional ARGB hex for 3D / sync (e.g. #c0c0c0). */
    public String wallColorHex;
    /** Optional texture id for wallpaper (e.g. wallpaper1). */
    public String wallTextureId;
    public List<Opening> openings = new ArrayList<>();
    /**
     * Color used to erase the wall line at door/window positions.
     * Defaults to white (light canvas); set to the canvas background color for dark mode
     * via {@link com.inania.Anthuria.DrawingView#applyTheme(boolean)}.
     */
    public int cutoutColor = Color.WHITE;

    public static class Opening {
        public enum Type { DOOR, WINDOW }
        public Type type;
        public float positionFactor;
        public float widthPx = 80f;

        public Opening(Type type, float positionFactor) {
            this.type = type;
            this.positionFactor = positionFactor;
        }

        public Opening copy() {
            Opening o = new Opening(type, positionFactor);
            o.widthPx = widthPx;
            return o;
        }

        public void draw(Canvas canvas, Paint paint) {
            canvas.save();
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(4f);

            if (type == Type.DOOR) {
                paint.setColor(Color.parseColor("#A52A2A"));
                canvas.drawLine(-widthPx/2, 0, -widthPx/2, -widthPx, paint);
                canvas.drawArc(-widthPx/2 - widthPx, -widthPx, -widthPx/2 + widthPx, widthPx, 0, -90, false, paint);
                paint.setStyle(Paint.Style.STROKE);
                paint.setStrokeWidth(3f);
                paint.setColor(Color.argb(160, 100, 180, 255));
                paint.setPathEffect(new android.graphics.DashPathEffect(new float[]{10f, 8f}, 0f));
                float r = widthPx;
                canvas.drawArc(-widthPx / 2f - r, -r, -widthPx / 2f + r, r, 0f, -90f, false, paint);
                paint.setPathEffect(null);
            } else if (type == Type.WINDOW) {
                paint.setColor(Color.BLUE);
                canvas.drawRect(-widthPx/2, -5, widthPx/2, 5, paint);
                canvas.drawLine(-widthPx/2, 0, widthPx/2, 0, paint);
            }
            canvas.restore();
        }
    }

    /**
     * Uses the given {@link PointF} instances by reference so connected walls can share corners.
     */
    public Wall(PointF start, PointF end) {
        this.start = start;
        this.end = end;
    }

    /**
     * Deep-clones this wall, mapping shared {@link PointF} keys through {@code pointMap}
     * so coincident corners stay coincident in the copy.
     */
    public Wall deepCopy(IdentityHashMap<PointF, PointF> pointMap) {
        PointF ns = mapPoint(start, pointMap);
        PointF ne = mapPoint(end, pointMap);
        Wall w = new Wall(ns, ne);
        w.thicknessPx = thicknessPx;
        w.wallColorHex = wallColorHex;
        w.wallTextureId = wallTextureId;
        for (Opening op : openings) {
            w.openings.add(op.copy());
        }
        return w;
    }

    private static PointF mapPoint(PointF p, IdentityHashMap<PointF, PointF> pointMap) {
        PointF mapped = pointMap.get(p);
        if (mapped == null) {
            mapped = new PointF(p.x, p.y);
            pointMap.put(p, mapped);
        }
        return mapped;
    }

    public void draw(Canvas canvas, Paint wallPaint, Paint openingPaint) {
        // Рисуем стену с учетом толщины
        wallPaint.setStrokeWidth(thicknessPx);
        canvas.drawLine(start.x, start.y, end.x, end.y, wallPaint);

        for (Opening op : openings) {
            float opX = start.x + (end.x - start.x) * op.positionFactor;
            float opY = start.y + (end.y - start.y) * op.positionFactor;

            canvas.save();
            canvas.translate(opX, opY);
            float angle = (float) Math.toDegrees(Math.atan2(end.y - start.y, end.x - start.x));
            canvas.rotate(angle);

            openingPaint.setColor(cutoutColor);
            openingPaint.setStyle(Paint.Style.FILL);
            canvas.drawRect(-op.widthPx/2, -thicknessPx/2 - 2, op.widthPx/2, thicknessPx/2 + 2, openingPaint);

            op.draw(canvas, openingPaint);
            canvas.restore();
        }
    }

    public void drawDimension(Canvas canvas, Paint paint, float pixelsPerMeter) {
        float dx = end.x - start.x;
        float dy = end.y - start.y;
        float lengthPx = (float) Math.sqrt(dx * dx + dy * dy);
        if (lengthPx < 30) return;

        float lengthMeters = lengthPx / pixelsPerMeter;
        String text = String.format("%.2f m", lengthMeters);

        float midX = (start.x + end.x) / 2;
        float midY = (start.y + end.y) / 2;
        float angle = (float) Math.toDegrees(Math.atan2(dy, dx));

        canvas.save();
        canvas.translate(midX, midY);
        canvas.rotate(angle);
        canvas.drawText(text, 0, -thicknessPx - 10, paint);
        canvas.restore();
    }

    // Проверка попадания в точки для перетаскивания
    public PointF getTouchPoint(float x, float y, float radius) {
        if (dist(x, y, start.x, start.y) < radius) return start;
        if (dist(x, y, end.x, end.y) < radius) return end;
        return null;
    }

    private float dist(float x1, float y1, float x2, float y2) {
        return (float) Math.sqrt(Math.pow(x1 - x2, 2) + Math.pow(y1 - y2, 2));
    }

    public float getThicknessPx() {
        return thicknessPx;
    }

    public void setThicknessPx(float thicknessPx) {
        this.thicknessPx = Math.max(2f, thicknessPx);
    }
}