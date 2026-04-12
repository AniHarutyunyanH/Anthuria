package com.inania.Anthuria;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PointF;

/**
 * Класс Furniture: универсальный объект мебели или открывания (дверь, окно)
 * Используется для 2D-представления на DrawingView.
 */
public class Furniture {

    // Тип объекта
    public enum Type {
        DOOR,
        WINDOW,
        BED,
        SOFA,
        TABLE,
        CHAIR,
        WARDROBE,
        DESK,
        STOVE,
        FRIDGE,
        SINK,
        BATH,
        TOILET,
        TV_STAND
    }

    public Type type;          // тип объекта
    public PointF position;    // центр объекта (x, y) в пикселях
    public float rotation = 0; // вращение в градусах
    public float width = 80f;  // ширина (px)
    public float height = 40f; // глубина/высота (px)

    /**
     * Конструктор для базовых объектов
     */
    public Furniture(Type type, PointF position, float width, float height, float rotation) {
        this.type = type;
        this.position = position;
        this.width = width;
        this.height = height;
        this.rotation = rotation;
    }

    /**
     * Конструктор упрощённый (без rotation)
     */
    public Furniture(Type type, PointF position, float width, float height) {
        this(type, position, width, height, 0f);
    }

    /**
     * Конструктор по умолчанию для дверей и окон
     */
    public Furniture(Type type, PointF position) {
        this.type = type;
        this.position = position;

        switch(type) {
            case DOOR:
                this.width = 80f;
                this.height = 10f;
                break;
            case WINDOW:
                this.width = 60f;
                this.height = 10f;
                break;
            default:
                this.width = 80f;
                this.height = 40f;
        }
    }

    /**
     * Рисует объект на Canvas
     */
    public void draw(Canvas canvas, Paint paint) {
        canvas.save();
        canvas.translate(position.x, position.y);
        canvas.rotate(rotation);

        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(5f);

        switch(type) {
            case DOOR:
                paint.setColor(Color.parseColor("#A52A2A")); // Brown
                // Прямоугольник двери
                canvas.drawRect(0, -height/2, width, height/2, paint);
                // Дуга открывания двери
                canvas.drawArc(-width/2, -width/2, width/2, width/2, 0, -90, false, paint);
                break;

            case WINDOW:
                paint.setColor(Color.BLUE);
                canvas.drawRect(-width/2, -height/2, width/2, height/2, paint);
                break;

            case BED:
                paint.setColor(Color.MAGENTA);
                canvas.drawRect(-width/2, -height/2, width/2, height/2, paint);
                break;

            case SOFA:
                paint.setColor(Color.RED);
                canvas.drawRect(-width/2, -height/2, width/2, height/2, paint);
                break;

            case TABLE:
                paint.setColor(Color.DKGRAY);
                canvas.drawRect(-width/2, -height/2, width/2, height/2, paint);
                break;

            case CHAIR:
                paint.setColor(Color.LTGRAY);
                canvas.drawRect(-width/2, -height/2, width/2, height/2, paint);
                break;

            case WARDROBE:
                paint.setColor(Color.rgb(139,69,19)); // brown
                canvas.drawRect(-width/2, -height/2, width/2, height/2, paint);
                break;

            case DESK:
                paint.setColor(Color.BLACK);
                canvas.drawRect(-width/2, -height/2, width/2, height/2, paint);
                break;

            case STOVE:
                paint.setColor(Color.DKGRAY);
                canvas.drawRect(-width/2, -height/2, width/2, height/2, paint);
                paint.setStyle(Paint.Style.FILL);
                paint.setColor(Color.rgb(60, 60, 60));
                canvas.drawCircle(0, 0, Math.min(width, height) * 0.15f, paint);
                paint.setStyle(Paint.Style.STROKE);
                break;

            case FRIDGE:
                paint.setColor(Color.rgb(220, 220, 230));
                canvas.drawRect(-width/2, -height/2, width/2, height/2, paint);
                break;

            case SINK:
                paint.setColor(Color.CYAN);
                canvas.drawRect(-width/2, -height/2, width/2, height/2, paint);
                break;

            case BATH:
                paint.setColor(Color.rgb(200, 220, 255));
                canvas.drawRect(-width/2, -height/2, width/2, height/2, paint);
                break;

            case TOILET:
                paint.setColor(Color.WHITE);
                canvas.drawRect(-width/2, -height/2, width/2, height/2, paint);
                break;

            case TV_STAND:
                paint.setColor(Color.rgb(50, 50, 80));
                canvas.drawRect(-width/2, -height/2, width/2, height/2, paint);
                break;

            default:
                paint.setColor(Color.GREEN);
                canvas.drawRect(-width/2, -height/2, width/2, height/2, paint);
        }

        canvas.restore();
    }
}