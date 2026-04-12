package com.inania.Anthuria;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PointF;
import android.graphics.RectF;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;

public class Room {
    private List<Wall> walls;
    private Paint fillPaint;
    private Paint strokePaint;
    private Paint openingPaint;
    private Paint areaPaint;

    public Room(List<Wall> walls) {
        this.walls = walls;

        fillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        fillPaint.setColor(Color.BLUE);
        fillPaint.setAlpha(40); // Чуть прозрачнее для эстетики
        fillPaint.setStyle(Paint.Style.FILL);

        strokePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        strokePaint.setColor(Color.BLACK);
        strokePaint.setStyle(Paint.Style.STROKE);
        strokePaint.setStrokeWidth(12f);
        strokePaint.setStrokeCap(Paint.Cap.ROUND);

        openingPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        openingPaint.setStyle(Paint.Style.FILL);

        areaPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        areaPaint.setColor(Color.parseColor("#333333"));
        areaPaint.setTextSize(32f);
        areaPaint.setTextAlign(Paint.Align.CENTER);
        areaPaint.setFakeBoldText(true);
    }

    public List<Wall> getWalls() {
        return walls;
    }

    /**
     * Deep copy of this room's walls; shared corners in the source graph remain shared in the copy.
     */
    public Room deepCopy(IdentityHashMap<PointF, PointF> pointMap) {
        List<Wall> copy = new ArrayList<>(walls.size());
        for (Wall w : walls) {
            copy.add(w.deepCopy(pointMap));
        }
        return new Room(copy);
    }

    public void draw(Canvas canvas) {
        draw(canvas, 100f);
    }

    /**
     * @param pixelsPerMeter scale from canvas pixels to meters (same as {@link com.inania.Anthuria.DrawingView}).
     */
    public void draw(Canvas canvas, float pixelsPerMeter) {
        if (walls == null || walls.isEmpty()) return;

        Path floorPath = new Path();
        floorPath.moveTo(walls.get(0).start.x, walls.get(0).start.y);
        for (Wall wall : walls) {
            floorPath.lineTo(wall.end.x, wall.end.y);
        }
        floorPath.close();
        canvas.drawPath(floorPath, fillPaint);

        for (Wall wall : walls) {
            wall.draw(canvas, strokePaint, openingPaint);
        }

        double areaM2 = computeAreaSqMeters(pixelsPerMeter);
        if (areaM2 > 0.01 && walls.size() >= 3) {
            PointF c = computeCentroid();
            if (c != null) {
                canvas.drawText(String.format("%.1f m²", areaM2), c.x, c.y, areaPaint);
            }
        }
    }

    /** Shoelace formula; polygon from wall ring (ordered). */
    public double computeAreaSqMeters(float pixelsPerMeter) {
        if (walls == null || walls.size() < 3) return 0;
        List<PointF> verts = new ArrayList<>();
        verts.add(new PointF(walls.get(0).start.x, walls.get(0).start.y));
        for (Wall w : walls) {
            verts.add(new PointF(w.end.x, w.end.y));
        }
        int n = verts.size();
        if (n < 3) return 0;
        double sum = 0;
        for (int i = 0; i < n; i++) {
            PointF a = verts.get(i);
            PointF b = verts.get((i + 1) % n);
            sum += a.x * b.y - b.x * a.y;
        }
        double areaPx2 = Math.abs(sum) * 0.5;
        double mPerPx = 1.0 / pixelsPerMeter;
        return areaPx2 * mPerPx * mPerPx;
    }

    public PointF computeCentroid() {
        if (walls == null || walls.isEmpty()) return null;
        Path path = new Path();
        path.moveTo(walls.get(0).start.x, walls.get(0).start.y);
        for (Wall w : walls) {
            path.lineTo(w.end.x, w.end.y);
        }
        path.close();
        RectF b = new RectF();
        path.computeBounds(b, true);
        return new PointF(b.centerX(), b.centerY());
    }
}