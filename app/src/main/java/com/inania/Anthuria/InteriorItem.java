package com.inania.Anthuria;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;

public class InteriorItem {
    public String name;
    public float x, y; // в метрах
    public float width, depth; // в метрах
    public float rotation;

    public InteriorItem(String name, float x, float y, float w, float d, float rot) {
        this.name = name;
        this.x = x;
        this.y = y;
        this.width = w;
        this.depth = d;
        this.rotation = rot;
    }

    public void draw(Canvas canvas, Paint paint, float pixelsPerMeter) {
        canvas.save();

        // Переводим метры в пиксели
        float px = x * pixelsPerMeter;
        float py = y * pixelsPerMeter;
        float pw = width * pixelsPerMeter;
        float pd = depth * pixelsPerMeter;

        canvas.translate(px, py);
        canvas.rotate(rotation);

        // Настройка стиля для мебели
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(3f);
        paint.setColor(Color.parseColor("#4ABAED")); // Голубой контур

        // Рисуем прямоугольник мебели относительно центра
        canvas.drawRect(-pw/2, -pd/2, pw/2, pd/2, paint);

        // Опционально: закрашиваем слегка внутри
        paint.setStyle(Paint.Style.FILL);
        paint.setAlpha(30); // Очень прозрачный
        canvas.drawRect(-pw/2, -pd/2, pw/2, pd/2, paint);
        paint.setAlpha(255); // Возвращаем прозрачность

        canvas.restore();
    }
}