package com.inania.Anthuria;

import android.graphics.PointF;
import android.graphics.RectF;

/**
 * Placed furniture on the 2D plan. {@link #position} is the footprint center in canvas pixels.
 * {@link #attachmentSide}: 0=back (−Y local), 1=right (+X), 2=front (+Y), 3=left (−X) — the side that sticks to walls.
 */
public class FurnitureItem {

    public final int id;
    public String type;
    public PointF position;
    public float rotationDeg;
    public boolean isDefault;
    public int count;
    public float widthPx;
    public float depthPx;
    /** 0–3: back, right, front, left in local footprint space before {@link #rotationDeg}. */
    public int attachmentSide;
    /** Vertical offset in meters (for 3D elevation: shelves, wall cabinets). */
    public float elevationM;

    public FurnitureItem(int id, String type, PointF position, float rotationDeg,
                         boolean isDefault, int count, float widthPx, float depthPx) {
        this.id = id;
        this.type = type;
        this.position = position;
        this.rotationDeg = rotationDeg;
        this.isDefault = isDefault;
        this.count = count;
        this.widthPx = widthPx;
        this.depthPx = depthPx;
        this.attachmentSide = 0;
        this.elevationM = 0f;
    }

    // --- ДОБАВЛЕННЫЕ МЕТОДЫ ДЛЯ ИСПРАВЛЕНИЯ ОШИБОК ---

    /**
     * Возвращает центральную точку. Теперь DrawingView увидит этот метод.
     */
    public PointF getCenter() {
        return position;
    }

    /**
     * Возвращает границы объекта (нужно для расчетов коллизий)
     */
    public RectF getBounds() {
        return new RectF(
                position.x - widthPx / 2f,
                position.y - depthPx / 2f,
                position.x + widthPx / 2f,
                position.y + depthPx / 2f
        );
    }

    /**
     * Установка новых размеров (используется при редактировании)
     */
    public void setSize(float newWidth, float newDepth) {
        this.widthPx = Math.max(10f, newWidth);
        this.depthPx = Math.max(10f, newDepth);
    }

    // --- КОНЕЦ ДОБАВЛЕННЫХ МЕТОДОВ ---

    public FurnitureItem copy() {
        FurnitureItem c = new FurnitureItem(id, type, new PointF(position.x, position.y),
                rotationDeg, isDefault, count, widthPx, depthPx);
        c.attachmentSide = attachmentSide;
        c.elevationM = elevationM;
        return c;
    }

    public void cycleAttachmentSide() {
        attachmentSide = (attachmentSide + 1) & 3;
    }

    public float centerX() {
        return position.x;
    }

    public float centerY() {
        return position.y;
    }

    /** Plan width in pixels (local X extent of the footprint). */
    public float getWidthPx() {
        return widthPx;
    }

    /** Plan depth in pixels (local Y extent; floor-plan "height"). */
    public float getDepthPx() {
        return depthPx;
    }

    /** Rotation in degrees (canvas space). */
    public float getRotationDeg() {
        return rotationDeg;
    }
}