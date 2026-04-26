package com.inania.Anthuria;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.DashPathEffect;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PointF;
import android.graphics.RectF;
import android.graphics.Region;
import android.util.AttributeSet;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;

import androidx.annotation.Nullable;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;

/**
 * 2D floor-plan canvas: walls with shared corner points, rooms, openings, furniture, undo/redo.
 */
public class DrawingView extends View {

    public enum Mode {
        /** Layout only: drag corners, no new walls. */
        NONE,
        /** Same as {@link #NONE}: select/move vertices without drawing new walls. */
        SELECT,
        WALL,
        DOOR,
        WINDOW,
        ANGLE,
        DELETE,
        FURNITURE,
        DRAG_FURNITURE,
        /** Two taps: measure distance (no walls). */
        TAPE_MEASURE
    }

    public interface EditorCallback {
        void onWallEditRequested(Wall wall, float lengthMeters);

        void onAngleConfigurationRequested(Wall wallA, Wall wallB, PointF sharedCorner);

        void onWallDeleteConfirmationRequested(Wall wall);
    }

    private static final int MAX_HISTORY = 40;
    private static final float SNAP_RADIUS = 50f;
    private static final float CORNER_HIT_RADIUS = 50f;
    private static final float WALL_PICK_HALF_WIDTH = 55f;
    private static final float MIN_WALL_LENGTH_PX = 12f;
    private static final float OPENING_PICK_RADIUS = 64f;
    private static final float FURNITURE_PICK_PADDING = 24f;
    private static final float ORTHO_SNAP_DEG = 5f;
    private static final float FURNITURE_WALL_SNAP_PX = 72f;
    private static final float AUTO_CLOSE_PX = 50f;
    private static final float GUIDE_ALIGN_PX = 4f;
    private static final float HANDLE_RADIUS = 26f;
    private static final float SCALE_HANDLE_HIT_RADIUS = 22f;
    private static final float MIN_FURNITURE_SIZE_PX = 24f;
    private static final float FURNITURE_ROT_SNAP_DEG = 5f;
    private static final int BLUEPRINT_SCALE = 2;

    private Mode mode = Mode.WALL;
    private @Nullable EditorCallback editorCallback;

    private final List<Room> rooms = new ArrayList<>();
    /** In-progress wall chain (not yet closed into a room). */
    private final List<Wall> chainWalls = new ArrayList<>();
    /** Segments not belonging to any closed room (e.g. after a room was broken). */
    private final List<Wall> orphanWalls = new ArrayList<>();

    private final List<DrawingState> undoStack = new ArrayList<>();
    private final List<DrawingState> redoStack = new ArrayList<>();

    private Bitmap backgroundScan;
    private float pixelsPerMeter = 100f;

    private PointF chainStartVertex;
    private PointF chainLastVertex;
    private PointF previewEnd;

    private float mScaleFactor = 1f;
    private float mPosX;
    private float mPosY;
    private float mLastTouchX;
    private float mLastTouchY;
    private ScaleGestureDetector scaleDetector;
    private GestureDetector gestureDetector;

    private Wall.Opening draggedOpening;
    private Wall openingTargetWall;
    private Wall.Opening.Type pendingOpeningType;

    private final List<PointF> cornerDragPoints = new ArrayList<>();
    private @Nullable PointF cornerDragAnchor;
    private boolean cornerDragActive;

    private Wall angleWallA;
    private Wall angleWallB;

    private final List<Wall> lockedWalls = new ArrayList<>();

    private final List<InteriorItem> aiItems = new ArrayList<>();
    private final List<FurnitureItem> furnitureItems = new ArrayList<>();
    private int furnitureIdSeq;

    private @Nullable FurnitureItem selectedFurniture;
    private float furnitureRotateStartRad;
    private float furnitureRotatePointerAngleStart;

    private Paint wallPaint;
    private Paint chainWallPaint;
    private Paint orphanWallPaint;
    private Paint dimensionPaint;
    private Paint openingPaint;
    private Paint furnitureStrokePaint;
    private Paint furnitureFillPaint;
    private Paint textPaint;
    private Paint editPointPaint;
    private Paint highlightPaint;
    private Paint previewPaint;

    private Wall highlightedWallAngle;

    private float lastFurnitureWorldX;
    private float lastFurnitureWorldY;

    private float lastPanMidX = Float.NaN;
    private float lastPanMidY = Float.NaN;

    private final Matrix worldMatrix = new Matrix();
    private final Paint guidePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final List<GuideLine> smartGuides = new ArrayList<>();
    private final PointF tmpPoint = new PointF();
    private final PointF tmpPoint2 = new PointF();

    private PointF tapeA;
    private PointF tapeB;
    private PointF tapePreview;

    private boolean furnitureRotateHandleDrag;
    /** Rotation (deg) when single-finger rotate handle drag started. */
    private float furnitureHandleRotateBaseDeg;
    /** Finger angle from furniture center when rotate handle drag started. */
    private float furnitureHandleFingerStartDeg;

    private boolean furnitureScaleDrag;
    private int furnitureScaleCornerIndex = -1;
    private final PointF scaleFixedWorld = new PointF();
    private float scaleBackupX;
    private float scaleBackupY;
    private float scaleBackupW;
    private float scaleBackupD;

    private float guideCx = Float.NaN;
    private float guideCy = Float.NaN;

    public static final class WallDistanceInfo {
        public final Wall wall;
        public final float distanceMeters;
        /** Closest point on the wall segment. */
        public final PointF closestOnWall;
        /** Point on the furniture footprint (corner) used for this measurement. */
        public final PointF closestOnFurniture;

        WallDistanceInfo(Wall wall, float distanceMeters, PointF closestOnWall, PointF closestOnFurniture) {
            this.wall = wall;
            this.distanceMeters = distanceMeters;
            this.closestOnWall = closestOnWall;
            this.closestOnFurniture = closestOnFurniture;
        }
    }

    private static final class GuideLine {
        final float x1, y1, x2, y2;

        GuideLine(float x1, float y1, float x2, float y2) {
            this.x1 = x1;
            this.y1 = y1;
            this.x2 = x2;
            this.y2 = y2;
        }
    }

    private static final class DrawingState {
        final List<Room> rooms;
        final List<Wall> chainWalls;
        final List<Wall> orphanWalls;
        final List<InteriorItem> aiItems;
        final List<FurnitureItem> furniture;
        final PointF chainStartVertex;
        final PointF chainLastVertex;
        final int furnitureIdSeq;

        DrawingState(List<Room> rooms,
                     List<Wall> chainWalls,
                     List<Wall> orphanWalls,
                     List<InteriorItem> aiItems,
                     List<FurnitureItem> furniture,
                     PointF chainStartVertex,
                     PointF chainLastVertex,
                     int furnitureIdSeq) {
            IdentityHashMap<PointF, PointF> pointMap = new IdentityHashMap<>();
            this.rooms = new ArrayList<>();
            for (Room r : rooms) {
                this.rooms.add(r.deepCopy(pointMap));
            }
            this.chainWalls = new ArrayList<>();
            for (Wall w : chainWalls) {
                this.chainWalls.add(w.deepCopy(pointMap));
            }
            this.orphanWalls = new ArrayList<>();
            for (Wall w : orphanWalls) {
                this.orphanWalls.add(w.deepCopy(pointMap));
            }
            this.aiItems = new ArrayList<>();
            for (InteriorItem it : aiItems) {
                this.aiItems.add(new InteriorItem(it.name, it.x, it.y, it.width, it.depth, it.rotation));
            }
            this.furniture = new ArrayList<>();
            for (FurnitureItem f : furniture) {
                this.furniture.add(f.copy());
            }
            this.chainStartVertex = copyPoint(chainStartVertex, pointMap);
            this.chainLastVertex = copyPoint(chainLastVertex, pointMap);
            this.furnitureIdSeq = furnitureIdSeq;
        }

        private static PointF copyPoint(PointF p, IdentityHashMap<PointF, PointF> pointMap) {
            if (p == null) return null;
            PointF m = pointMap.get(p);
            if (m != null) return m;
            PointF n = new PointF(p.x, p.y);
            pointMap.put(p, n);
            return n;
        }
    }

    public DrawingView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    private void init() {
        wallPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        wallPaint.setColor(Color.BLACK);
        wallPaint.setStrokeWidth(20f);
        wallPaint.setStrokeCap(Paint.Cap.ROUND);

        chainWallPaint = new Paint(wallPaint);
        orphanWallPaint = new Paint(wallPaint);

        previewPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        previewPaint.setColor(Color.argb(180, 0, 0, 0));
        previewPaint.setStrokeWidth(18f);
        previewPaint.setStrokeCap(Paint.Cap.ROUND);

        highlightPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        highlightPaint.setStyle(Paint.Style.STROKE);
        highlightPaint.setStrokeWidth(26f);
        highlightPaint.setStrokeCap(Paint.Cap.ROUND);

        editPointPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        editPointPaint.setColor(Color.RED);
        editPointPaint.setStyle(Paint.Style.FILL);
        editPointPaint.setAlpha(130);

        dimensionPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        dimensionPaint.setColor(Color.GRAY);
        dimensionPaint.setTextSize(26f);
        dimensionPaint.setFakeBoldText(true);
        dimensionPaint.setTextAlign(Paint.Align.CENTER);

        openingPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        openingPaint.setStyle(Paint.Style.FILL);

        furnitureStrokePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        furnitureStrokePaint.setColor(Color.parseColor("#4ABAED"));
        furnitureStrokePaint.setStyle(Paint.Style.STROKE);
        furnitureStrokePaint.setStrokeWidth(3f);

        furnitureFillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        furnitureFillPaint.setColor(Color.parseColor("#4ABAED"));
        furnitureFillPaint.setStyle(Paint.Style.FILL);
        furnitureFillPaint.setAlpha(40);

        textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        textPaint.setColor(Color.DKGRAY);
        textPaint.setTextSize(20f);
        textPaint.setTextAlign(Paint.Align.CENTER);

        guidePaint.setStyle(Paint.Style.STROKE);
        guidePaint.setStrokeWidth(1.8f);
        guidePaint.setColor(Color.argb(200, 120, 190, 255));
        guidePaint.setPathEffect(new DashPathEffect(new float[]{16f, 12f}, 0f));

        scaleDetector = new ScaleGestureDetector(getContext(), new ScaleListener());
        gestureDetector = new GestureDetector(getContext(), new GestureDetector.SimpleOnGestureListener() {
            @Override
            public void onLongPress(MotionEvent e) {
                if (mode != Mode.WALL && mode != Mode.NONE && mode != Mode.SELECT) return;
                float cx = screenToWorldX(e.getX());
                float cy = screenToWorldY(e.getY());
                PointF corner = findCornerAt(cx, cy);
                if (corner == null) return;
                saveState();
                cornerDragAnchor = corner;
                cornerDragPoints.clear();
                collectPointsEqualTo(corner, cornerDragPoints);
                cornerDragActive = true;
                invalidate();
            }
        });
    }

    public void setEditorCallback(@Nullable EditorCallback callback) {
        this.editorCallback = callback;
    }

    /** @deprecated use {@link #setEditorCallback(EditorCallback)} */
    @Deprecated
    public void setInteractionListener(EditorCallback listener) {
        setEditorCallback(listener);
    }

    public Mode getMode() {
        return mode;
    }

    public void setMode(Mode mode) {
        this.mode = mode;
        cancelTransientInteraction();
        invalidate();
    }

    private void cancelTransientInteraction() {
        draggedOpening = null;
        openingTargetWall = null;
        cornerDragActive = false;
        cornerDragPoints.clear();
        cornerDragAnchor = null;
        previewEnd = null;
        angleWallA = null;
        angleWallB = null;
        highlightedWallAngle = null;
        highlightedWall = null;
        clearGuideProbe();
        tapeA = null;
        tapeB = null;
        tapePreview = null;
        furnitureRotateHandleDrag = false;
        furnitureHandleRotateBaseDeg = 0f;
        furnitureHandleFingerStartDeg = 0f;
        furnitureScaleDrag = false;
        furnitureScaleCornerIndex = -1;
    }

    public void setBackgroundScan(Bitmap bitmap) {
        this.backgroundScan = bitmap;
        invalidate();
    }

    public void startDraggingNewOpening(Wall.Opening.Type type) {
        saveState();
        pendingOpeningType = type;
        draggedOpening = new Wall.Opening(type, 0.5f);
        openingTargetWall = null;
        invalidate();
    }

    public void addFurnitureFromCatalog(List<FurnitureItem> items) {
        saveState();
        float cx = screenToWorldX(getWidth() / 2f);
        float cy = screenToWorldY(getHeight() / 2f);
        float grid = 24f;
        int i = 0;
        for (FurnitureItem template : items) {
            PointF p = new PointF(
                    cx + (i % 3) * grid * 3,
                    cy + (i / 3) * grid * 3);
            FurnitureItem f = new FurnitureItem(
                    ++furnitureIdSeq,
                    template.type,
                    p,
                    template.rotationDeg,
                    template.isDefault,
                    template.count,
                    template.widthPx,
                    template.depthPx);
            applyFurnitureWallSnapAndBounds(f);
            furnitureItems.add(f);
            i++;
        }
        selectedFurniture = furnitureItems.isEmpty() ? null : furnitureItems.get(furnitureItems.size() - 1);
        setMode(Mode.DRAG_FURNITURE);
        invalidate();
    }

    public void undo() {
        if (undoStack.isEmpty()) return;
        redoStack.add(snapshotState());
        restoreState(undoStack.remove(undoStack.size() - 1));
        invalidate();
    }

    public void redo() {
        if (redoStack.isEmpty()) return;
        undoStack.add(snapshotState());
        restoreState(redoStack.remove(redoStack.size() - 1));
        invalidate();
    }

    private DrawingState snapshotState() {
        return new DrawingState(
                rooms, chainWalls, orphanWalls, aiItems, furnitureItems,
                chainStartVertex, chainLastVertex, furnitureIdSeq);
    }

    private void restoreState(DrawingState s) {
        rooms.clear();
        rooms.addAll(s.rooms);
        chainWalls.clear();
        chainWalls.addAll(s.chainWalls);
        orphanWalls.clear();
        orphanWalls.addAll(s.orphanWalls);
        aiItems.clear();
        aiItems.addAll(s.aiItems);
        furnitureItems.clear();
        furnitureItems.addAll(s.furniture);
        chainStartVertex = s.chainStartVertex;
        chainLastVertex = s.chainLastVertex;
        furnitureIdSeq = s.furnitureIdSeq;
        selectedFurniture = null;
    }

    public void saveState() {
        if (undoStack.size() >= MAX_HISTORY) {
            undoStack.remove(0);
        }
        undoStack.add(snapshotState());
        redoStack.clear();
    }

    public void lockWallLength(Wall wall, boolean lock) {
        if (lock && !lockedWalls.contains(wall)) lockedWalls.add(wall);
        else lockedWalls.remove(wall);
    }

    public void updateWallLength(Wall wall, float newLengthMeters) {
        if (wall == null || lockedWalls.contains(wall)) return;
        saveState();
        float targetPx = newLengthMeters * pixelsPerMeter;
        float dx = wall.end.x - wall.start.x;
        float dy = wall.end.y - wall.start.y;
        float cur = (float) Math.hypot(dx, dy);
        if (cur < 1e-3f) return;
        float ratio = targetPx / cur;
        float oldEx = wall.end.x;
        float oldEy = wall.end.y;
        float newEx = wall.start.x + dx * ratio;
        float newEy = wall.start.y + dy * ratio;
        moveAllPointsAt(oldEx, oldEy, newEx, newEy);
        recomputeRoomsAfterGeometryChange();
        invalidate();
    }

    public void updateWallThickness(Wall wall, float thicknessPx) {
        if (wall == null) return;
        saveState();
        wall.setThicknessPx(thicknessPx);
        invalidate();
    }

    /**
     * Keeps {@code wallFixed} direction from {@code corner}; rotates {@code wallRotate}'s free end around {@code corner}
     * so the signed angle from fixed wall to rotating wall equals {@code targetAngleDeg} (degrees, CCW in canvas space).
     */
    public void applyTargetAngleBetweenWalls(Wall wallFixed, Wall wallRotate, PointF corner, float targetAngleDeg) {
        if (wallFixed == null || wallRotate == null || corner == null) return;
        saveState();
        PointF fixedFar = farEndpoint(wallFixed, corner);
        PointF rotateFar = farEndpoint(wallRotate, corner);
        if (fixedFar == null || rotateFar == null) return;

        float alpha = (float) Math.atan2(fixedFar.y - corner.y, fixedFar.x - corner.x);
        float betaNew = alpha + (float) Math.toRadians(targetAngleDeg);
        double d = Math.hypot(rotateFar.x - corner.x, rotateFar.y - corner.y);
        float nx = corner.x + (float) Math.cos(betaNew) * (float) d;
        float ny = corner.y + (float) Math.sin(betaNew) * (float) d;
        moveAllPointsAt(rotateFar.x, rotateFar.y, nx, ny);
        recomputeRoomsAfterGeometryChange();
        invalidate();
    }

    private static PointF farEndpoint(Wall w, PointF corner) {
        if (w.start == corner) return w.end;
        if (w.end == corner) return w.start;
        return null;
    }

    public void deleteWallAfterConfirmed(Wall wall) {
        if (wall == null) return;
        saveState();
        removeWallEverywhere(wall);
        invalidate();
    }

    /** @deprecated use {@link #deleteWallAfterConfirmed(Wall)} */
    @Deprecated
    public void deleteSelectedWall() {
        // kept for compatibility; no selection state in new flow
    }

    public void setDeleteMode(boolean delete) {
        setMode(delete ? Mode.DELETE : Mode.NONE);
    }

    public void setAiFurniture(String jsonString) {
        aiItems.clear();
        try {
            JSONArray array = new JSONArray(jsonString);
            for (int i = 0; i < array.length(); i++) {
                JSONObject obj = array.getJSONObject(i);
                aiItems.add(new InteriorItem(
                        obj.getString("name"),
                        (float) obj.getDouble("x"),
                        (float) obj.getDouble("y"),
                        (float) obj.optDouble("width", 0.5),
                        (float) obj.optDouble("depth", 0.5),
                        (float) obj.optDouble("rotation", 0)
                ));
            }
        } catch (Exception ignored) {
        }
        invalidate();
    }

    public String getRoomDataAsJSON() {
        try {
            JSONArray wallsArray = new JSONArray();
            for (Room room : rooms) {
                for (Wall wall : room.getWalls()) {
                    wallsArray.put(wallToJson(wall));
                }
            }
            for (Wall wall : chainWalls) {
                wallsArray.put(wallToJson(wall));
            }
            for (Wall wall : orphanWalls) {
                wallsArray.put(wallToJson(wall));
            }
            JSONObject root = new JSONObject();
            root.put("walls", wallsArray);
            root.put("pixelsPerMeter", pixelsPerMeter);
            return root.toString();
        } catch (Exception e) {
            return "{\"walls\":[]}";
        }
    }

    public String getManualFurnitureJson() {
        try {
            JSONArray arr = new JSONArray();
            for (FurnitureItem f : furnitureItems) {
                JSONObject o = new JSONObject();
                o.put("name", f.type);
                o.put("type", f.type);
                o.put("x", f.position.x / pixelsPerMeter);
                o.put("y", f.position.y / pixelsPerMeter);
                o.put("width", f.widthPx / pixelsPerMeter);
                o.put("depth", f.depthPx / pixelsPerMeter);
                o.put("rotation", f.rotationDeg);
                o.put("elevation", f.elevationM);
                o.put("attachmentSide", f.attachmentSide);
                arr.put(o);
            }
            return arr.toString();
        } catch (Exception e) {
            return "[]";
        }
    }

    private JSONObject wallToJson(Wall wall) throws Exception {
        JSONObject wObj = new JSONObject();
        wObj.put("x1", wall.start.x / pixelsPerMeter);
        wObj.put("y1", wall.start.y / pixelsPerMeter);
        wObj.put("x2", wall.end.x / pixelsPerMeter);
        wObj.put("y2", wall.end.y / pixelsPerMeter);
        wObj.put("thicknessMeters", wall.thicknessPx / pixelsPerMeter);
        wObj.put("thicknessPx", wall.thicknessPx);
        if (wall.wallColorHex != null) {
            wObj.put("wallColorHex", wall.wallColorHex);
        }
        if (wall.wallTextureId != null) {
            wObj.put("wallTextureId", wall.wallTextureId);
        }
        if (wall.openings != null && !wall.openings.isEmpty()) {
            JSONArray ops = new JSONArray();
            for (Wall.Opening op : wall.openings) {
                JSONObject oObj = new JSONObject();
                oObj.put("type", op.type.name());
                oObj.put("pos", op.positionFactor);
                ops.put(oObj);
            }
            wObj.put("openings", ops);
        }
        return wObj;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        canvas.save();

        // Reset hitboxes for this frame
        currentHitBoxes.clear();

        worldMatrix.reset();
        worldMatrix.postScale(mScaleFactor, mScaleFactor);
        worldMatrix.postTranslate(mPosX, mPosY);
        canvas.concat(worldMatrix);

        if (backgroundScan != null) {
            canvas.drawBitmap(backgroundScan, 0, 0, null);
        }

        // 1. Draw Rooms and Wall-specific dimensions
        for (Room room : rooms) {
            room.draw(canvas, pixelsPerMeter);
            for (Wall wall : room.getWalls()) {
                wall.drawDimension(canvas, dimensionPaint, pixelsPerMeter);
            }
        }

        // 2. Draw Orphan/Chain Walls
        for (Wall wall : orphanWalls) {
            wall.draw(canvas, orphanWallPaint, openingPaint);
            wall.drawDimension(canvas, dimensionPaint, pixelsPerMeter);
        }

        for (Wall wall : chainWalls) {
            wall.draw(canvas, chainWallPaint, openingPaint);
            wall.drawDimension(canvas, dimensionPaint, pixelsPerMeter);
        }

        // 3. Highlight walls being edited or measured
        if (highlightedWall != null) {
            // Use Orange if it's the specific dimension target, else Purple for general selection
            highlightPaint.setColor(Color.parseColor("#CCFF9800"));
            canvas.drawLine(highlightedWall.start.x, highlightedWall.start.y,
                    highlightedWall.end.x, highlightedWall.end.y, highlightPaint);
        }
        if (highlightedWallAngle != null) {
            highlightPaint.setColor(Color.parseColor("#88FF9800"));
            canvas.drawLine(highlightedWallAngle.start.x, highlightedWallAngle.start.y,
                    highlightedWallAngle.end.x, highlightedWallAngle.end.y, highlightPaint);
        }

        // 4. Draw Vertex Handles for Walls
        for (Wall wall : collectAllWallsForHandles()) {
            canvas.drawCircle(wall.start.x, wall.start.y, 12f / mScaleFactor, editPointPaint);
            canvas.drawCircle(wall.end.x, wall.end.y, 12f / mScaleFactor, editPointPaint);
        }

        // 5. Draw Wall Preview
        if (chainLastVertex != null && previewEnd != null && mode == Mode.WALL && !cornerDragActive && draggedOpening == null) {
            canvas.drawLine(chainLastVertex.x, chainLastVertex.y, previewEnd.x, previewEnd.y, previewPaint);
        }

        // 6. Draw AI and Standard Furniture
        for (InteriorItem item : aiItems) {
            item.draw(canvas, furnitureStrokePaint, pixelsPerMeter);
            float py = item.y * pixelsPerMeter;
            float pd = item.depth * pixelsPerMeter;
            canvas.drawText(item.name, item.x * pixelsPerMeter, py + pd / 2f + 22f, textPaint);
        }

        for (FurnitureItem f : furnitureItems) {
            boolean isSelected = (f == selectedFurniture);
            drawFurnitureItem(canvas, f, isSelected);

            // 7. INTERACTIVE DIMENSIONS: If furniture is selected, draw distance labels to walls
            if (isSelected && (mode == Mode.SELECT || mode == Mode.DRAG_FURNITURE)) {
                drawInteractiveDimensions(canvas, f);
            }
        }

        // 8. Draw Openings (Doors/Windows) being dragged
        if (draggedOpening != null) {
            canvas.save();
            if (openingTargetWall != null) {
                float t = draggedOpening.positionFactor;
                float opX = openingTargetWall.start.x + (openingTargetWall.end.x - openingTargetWall.start.x) * t;
                float opY = openingTargetWall.start.y + (openingTargetWall.end.y - openingTargetWall.start.y) * t;
                canvas.translate(opX, opY);
                float ang = (float) Math.toDegrees(Math.atan2(
                        openingTargetWall.end.y - openingTargetWall.start.y,
                        openingTargetWall.end.x - openingTargetWall.start.x));
                canvas.rotate(ang);
                openingPaint.setAlpha(200);
            } else {
                canvas.translate(screenToWorldX(mLastTouchX), screenToWorldY(mLastTouchY));
                openingPaint.setAlpha(140);
            }
            draggedOpening.draw(canvas, openingPaint);
            openingPaint.setAlpha(255);
            canvas.restore();
        }

        drawSmartGuides(canvas);
        drawTapeMeasureOverlay(canvas);

        canvas.restore();
    }

    /**
     * Helper to draw the interactive distance text and populate hitboxes.
     */
    private void drawInteractiveDimensions(Canvas canvas, FurnitureItem f) {
        List<Wall> nearbyWalls = findNearestWalls(f); // Logic to find up to 4 closest walls

        for (Wall wall : nearbyWalls) {
            float distPx = calculateDistance(f.getCenter(), wall);
            float distMeters = distPx / pixelsPerMeter;
            String text = String.format(java.util.Locale.US, "%.2f m", distMeters);

            // Find midpoint between furniture edge and wall for text placement
            PointF labelPos = getDimensionLabelPoint(f, wall);

            android.graphics.Rect bounds = new android.graphics.Rect();
            dimensionPaint.getTextBounds(text, 0, text.length(), bounds);

            // Draw background for readability
            RectF bgRect = new RectF(
                    labelPos.x - bounds.width()/2f - 10,
                    labelPos.y - bounds.height()/2f - 10,
                    labelPos.x + bounds.width()/2f + 10,
                    labelPos.y + bounds.height()/2f + 10
            );

            Paint bgPaint = new Paint();
            bgPaint.setColor(Color.WHITE);
            bgPaint.setAlpha(220);
            canvas.drawRoundRect(bgRect, 5, 5, bgPaint);

            // Highlight text if this specific wall is being edited
            if (wall == highlightedWall) {
                dimensionPaint.setColor(Color.parseColor("#FF9800"));
                dimensionPaint.setFakeBoldText(true);
            } else {
                dimensionPaint.setColor(Color.BLACK);
                dimensionPaint.setFakeBoldText(false);
            }

            canvas.drawText(text, labelPos.x - bounds.width()/2f, labelPos.y + bounds.height()/2f, dimensionPaint);

            // Register hitbox for onTouchEvent
            currentHitBoxes.add(new DimensionHitBox(new RectF(bgRect), f, wall, distMeters));
        }
    }
    public void moveFurnitureByDistance(FurnitureItem item, Wall targetWall, float newDistanceMeters) {
        if (item == null || targetWall == null) return;

        PointF P = item.getCenter();
        PointF A = targetWall.start;
        PointF B = targetWall.end;

        // Vector AB (The Wall)
        float ABx = B.x - A.x;
        float ABy = B.y - A.y;

        // Vector AP (Start of wall to Furniture)
        float APx = P.x - A.x;
        float APy = P.y - A.y;

        // Magnitude squared of AB
        float ab_mag_squared = (ABx * ABx) + (ABy * ABy);
        if (ab_mag_squared == 0) return; // Wall is a point, prevent division by zero

        // Project point P onto line AB to find closest point C
        // t is the parameterized position on the segment [0, 1]
        float t = ((APx * ABx) + (APy * ABy)) / ab_mag_squared;
        t = Math.max(0, Math.min(1, t)); // Clamp to segment

        // Closest point C on the wall
        float Cx = A.x + t * ABx;
        float Cy = A.y + t * ABy;

        // Normal vector N (from Wall point C to Furniture point P)
        float Nx = P.x - Cx;
        float Ny = P.y - Cy;

        // Current distance in pixels
        float currentDistPx = (float) Math.sqrt((Nx * Nx) + (Ny * Ny));

        // Target distance in pixels
        float targetDistPx = newDistanceMeters * pixelsPerMeter; // Ensure pixelsPerMeter is defined in your view

        // Calculate Delta
        float deltaPx = targetDistPx - currentDistPx;

        // Normalize N to get a unit direction vector
        float Ux, Uy;
        if (currentDistPx > 0.01f) {
            Ux = Nx / currentDistPx;
            Uy = Ny / currentDistPx;
        } else {
            // If furniture is exactly ON the line, calculate normal strictly from the line vector (perpendicular)
            Ux = -ABy;
            Uy = ABx;
            float uMag = (float) Math.sqrt(Ux * Ux + Uy * Uy);
            Ux /= uMag;
            Uy /= uMag;
        }

        // Shift coordinates
        float newX = item.position.x + (Ux * deltaPx);
        float newY = item.position.y + (Uy * deltaPx);

        // Save previous state for boundary check
        PointF oldPos = new PointF(item.position.x, item.position.y);
        item.position.set(newX, newY);

        // TODO: Implement isInsideRoom(item) using Path.op or Region.contains
        // if (!isInsideRoom(item)) {
        //     item.position.set(oldPos.x, oldPos.y); // Revert if out of bounds
        // }

        highlightedWall = null; // Clear highlight
        invalidate(); // Redraw
    }
    private void drawSmartGuides(Canvas canvas) {
        if (Float.isNaN(guideCx) || Float.isNaN(guideCy)) return;
        float cx = guideCx;
        float cy = guideCy;
        float eps = GUIDE_ALIGN_PX;
        for (Wall w : collectAllWallsForHandles()) {
            for (PointF p : new PointF[]{w.start, w.end}) {
                if (Math.abs(cx - p.x) < eps) {
                    canvas.drawLine(p.x, -200000f, p.x, 200000f, guidePaint);
                }
                if (Math.abs(cy - p.y) < eps) {
                    canvas.drawLine(-200000f, p.y, 200000f, p.y, guidePaint);
                }
            }
        }
        for (FurnitureItem fi : furnitureItems) {
            if (fi == selectedFurniture) continue;
            float ox = fi.position.x;
            float oy = fi.position.y;
            if (Math.abs(cx - ox) < eps) {
                canvas.drawLine(ox, -200000f, ox, 200000f, guidePaint);
            }
            if (Math.abs(cy - oy) < eps) {
                canvas.drawLine(-200000f, oy, 200000f, oy, guidePaint);
            }
        }
    }

    private void drawTapeMeasureOverlay(Canvas canvas) {
        if (tapeA == null) return;
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setColor(Color.parseColor("#E65100"));
        p.setStrokeWidth(4f);
        PointF end = tapeB != null ? tapeB : tapePreview;
        if (end != null) {
            canvas.drawLine(tapeA.x, tapeA.y, end.x, end.y, p);
            float d = dist(tapeA.x, tapeA.y, end.x, end.y) / pixelsPerMeter;
            float mx = (tapeA.x + end.x) / 2f;
            float my = (tapeA.y + end.y) / 2f;
            p.setStyle(Paint.Style.FILL);
            p.setTextSize(28f);
            p.setTextAlign(Paint.Align.CENTER);
            canvas.drawText(String.format("%.2f m", d), mx, my - 18f, p);
        }
    }

    private void setGuideProbe(float x, float y) {
        guideCx = x;
        guideCy = y;
    }

    private void clearGuideProbe() {
        guideCx = Float.NaN;
        guideCy = Float.NaN;
    }

    /**
     * Renders the plan to a high-resolution PNG (white background, no background scan).
     */
    public Bitmap generateBlueprint() {
        int w = Math.max(1, getWidth());
        int h = Math.max(1, getHeight());
        int bw = w * BLUEPRINT_SCALE;
        int bh = h * BLUEPRINT_SCALE;
        Bitmap bmp = Bitmap.createBitmap(bw, bh, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(bmp);
        c.drawColor(Color.WHITE);
        float s = mScaleFactor * BLUEPRINT_SCALE;
        float tx = mPosX * BLUEPRINT_SCALE;
        float ty = mPosY * BLUEPRINT_SCALE;
        c.save();
        Matrix m = new Matrix();
        m.postScale(s, s);
        m.postTranslate(tx, ty);
        c.concat(m);
        drawPlanOnly(c);
        c.restore();
        return bmp;
    }

    private void drawPlanOnly(Canvas canvas) {
        for (Room room : rooms) {
            room.draw(canvas, pixelsPerMeter);
            for (Wall wall : room.getWalls()) {
                wall.drawDimension(canvas, dimensionPaint, pixelsPerMeter);
            }
        }
        for (Wall wall : orphanWalls) {
            wall.draw(canvas, orphanWallPaint, openingPaint);
            wall.drawDimension(canvas, dimensionPaint, pixelsPerMeter);
        }
        for (Wall wall : chainWalls) {
            wall.draw(canvas, chainWallPaint, openingPaint);
            wall.drawDimension(canvas, dimensionPaint, pixelsPerMeter);
        }
        for (InteriorItem item : aiItems) {
            item.draw(canvas, furnitureStrokePaint, pixelsPerMeter);
        }
        for (FurnitureItem f : furnitureItems) {
            drawFurnitureItem(canvas, f, false);
        }
        if (chainLastVertex != null && previewEnd != null && mode == Mode.WALL) {
            canvas.drawLine(chainLastVertex.x, chainLastVertex.y, previewEnd.x, previewEnd.y, previewPaint);
        }
    }

    /** Distances from furniture footprint corners to the four nearest distinct wall segments. */
    public List<WallDistanceInfo> getDistanceToNearestWalls(FurnitureItem f) {
        List<WallDistanceInfo> out = new ArrayList<>();
        if (f == null) return out;
        List<PointF> corners = furnitureCornersWorld(f);
        List<Wall> walls = collectAllWallsForHandles();
        boolean[] used = new boolean[walls.size()];
        for (int k = 0; k < 4 && out.size() < 4; k++) {
            float best = Float.MAX_VALUE;
            int bi = -1;
            PointF bestFoot = null;
            PointF bestCorner = null;
            for (int i = 0; i < walls.size(); i++) {
                if (used[i]) continue;
                Wall w = walls.get(i);
                for (PointF corner : corners) {
                    float t = projectOntoSegmentT(corner.x, corner.y, w.start, w.end);
                    float px = w.start.x + t * (w.end.x - w.start.x);
                    float py = w.start.y + t * (w.end.y - w.start.y);
                    float d = dist(corner.x, corner.y, px, py);
                    if (d < best) {
                        best = d;
                        bi = i;
                        bestFoot = new PointF(px, py);
                        bestCorner = corner;
                    }
                }
            }
            if (bi >= 0 && bestFoot != null && bestCorner != null) {
                used[bi] = true;
                out.add(new WallDistanceInfo(walls.get(bi), best / pixelsPerMeter, bestFoot,
                        new PointF(bestCorner.x, bestCorner.y)));
            } else {
                break;
            }
        }
        return out;
    }

    private List<PointF> furnitureCornersWorld(FurnitureItem f) {
        float hw = f.widthPx / 2f;
        float hd = f.depthPx / 2f;
        float[][] local = {{-hw, -hd}, {hw, -hd}, {hw, hd}, {-hw, hd}};
        double rad = Math.toRadians(f.rotationDeg);
        float cos = (float) Math.cos(rad);
        float sin = (float) Math.sin(rad);
        List<PointF> list = new ArrayList<>(4);
        for (float[] lc : local) {
            float wx = lc[0] * cos - lc[1] * sin + f.position.x;
            float wy = lc[0] * sin + lc[1] * cos + f.position.y;
            list.add(new PointF(wx, wy));
        }
        return list;
    }

    /** Snaps {@code f} so the active attachment edge is flush on the nearest wall. */
    public void snapToNearestWall(FurnitureItem f) {
        if (f == null) return;
        saveState();
        snapFurnitureWithAttachment(f);
        invalidate();
    }

    private void drawFurnitureItem(Canvas canvas, FurnitureItem f, boolean selected) {
        canvas.save();
        canvas.translate(f.position.x, f.position.y);
        canvas.rotate(f.rotationDeg);
        furnitureFillPaint.setAlpha(45);
        canvas.drawRoundRect(
                new RectF(-f.widthPx / 2f, -f.depthPx / 2f, f.widthPx / 2f, f.depthPx / 2f),
                6f, 6f, furnitureFillPaint);
        furnitureFillPaint.setAlpha(255);
        canvas.drawRoundRect(
                new RectF(-f.widthPx / 2f, -f.depthPx / 2f, f.widthPx / 2f, f.depthPx / 2f),
                6f, 6f, furnitureStrokePaint);
        float hw = f.widthPx / 2f;
        float hd = f.depthPx / 2f;
        Paint sidePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        sidePaint.setColor(Color.argb(200, 255, 180, 80));
        sidePaint.setStrokeWidth(5f);
        switch (f.attachmentSide & 3) {
            case 0:
                canvas.drawLine(-hw, -hd, hw, -hd, sidePaint);
                break;
            case 1:
                canvas.drawLine(hw, -hd, hw, hd, sidePaint);
                break;
            case 2:
                canvas.drawLine(-hw, hd, hw, hd, sidePaint);
                break;
            default:
                canvas.drawLine(-hw, -hd, -hw, hd, sidePaint);
                break;
        }
        if (selected) {
            highlightPaint.setColor(Color.parseColor("#884ABAED"));
            float pad = 6f;
            canvas.drawRect(-f.widthPx / 2f - pad, -f.depthPx / 2f - pad,
                    f.widthPx / 2f + pad, f.depthPx / 2f + pad, highlightPaint);
            Paint hp = new Paint(Paint.ANTI_ALIAS_FLAG);
            hp.setColor(Color.parseColor("#4ABAED"));
            hp.setStyle(Paint.Style.FILL);
            Path rotArrow = new Path();
            rotArrow.moveTo(-12f, -hd - 48f);
            rotArrow.lineTo(0f, -hd - 64f);
            rotArrow.lineTo(12f, -hd - 48f);
            rotArrow.close();
            canvas.drawPath(rotArrow, hp);
            canvas.drawCircle(0f, -hd - 55f, 6f, hp);
            float[][] lc = {{-hw, -hd}, {hw, -hd}, {hw, hd}, {-hw, hd}};
            for (int i = 0; i < 4; i++) {
                canvas.drawCircle(lc[i][0], lc[i][1], 10f, hp);
            }
            canvas.drawCircle(hw + 32f, -hd - 32f, 14f, hp);
            canvas.drawCircle(-hw - 32f, -hd - 32f, 14f, hp);
            Paint lp = new Paint(Paint.ANTI_ALIAS_FLAG);
            lp.setColor(Color.WHITE);
            lp.setTextSize(18f);
            lp.setTextAlign(Paint.Align.CENTER);
            canvas.drawText("X", hw + 32f, -hd - 27f, lp);
            canvas.drawText("F", -hw - 32f, -hd - 27f, lp);
        }
        canvas.restore();
        String label = f.type != null ? f.type.replace('_', ' ') : "";
        canvas.drawText(label, f.position.x, f.position.y + f.depthPx / 2f + 26f, textPaint);
        if (selected) {
            drawFurnitureWallDistances(canvas, f);
        }
    }

    private void drawFurnitureWallDistances(Canvas canvas, FurnitureItem f) {
        List<WallDistanceInfo> infos = getDistanceToNearestWalls(f);
        Paint dim = new Paint(Paint.ANTI_ALIAS_FLAG);
        dim.setColor(Color.argb(220, 80, 80, 80));
        dim.setStrokeWidth(2f);
        dim.setTextSize(22f);
        for (WallDistanceInfo wi : infos) {
            float x0 = wi.closestOnFurniture.x;
            float y0 = wi.closestOnFurniture.y;
            canvas.drawLine(x0, y0, wi.closestOnWall.x, wi.closestOnWall.y, dim);
            float mx = (x0 + wi.closestOnWall.x) / 2f;
            float my = (y0 + wi.closestOnWall.y) / 2f;
            canvas.drawText(String.format("%.2fm", wi.distanceMeters), mx, my - 8f, dim);
        }
    }

    private List<Wall> collectAllWallsForHandles() {
        List<Wall> all = new ArrayList<>();
        for (Room r : rooms) all.addAll(r.getWalls());
        all.addAll(chainWalls);
        all.addAll(orphanWalls);
        return all;
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        final int action = event.getActionMasked();
        scaleDetector.onTouchEvent(event);

        final int pointerCount = event.getPointerCount();

        // 1. МУЛЬТИТАЧ (Панорамирование и вращение мебели)
        if (pointerCount >= 2) {
            float mx = (event.getX(0) + event.getX(1)) * 0.5f;
            float my = (event.getY(0) + event.getY(1)) * 0.5f;
            if (action == MotionEvent.ACTION_POINTER_DOWN || action == MotionEvent.ACTION_DOWN) {
                lastPanMidX = mx;
                lastPanMidY = my;
            } else if (action == MotionEvent.ACTION_MOVE) {
                if (!Float.isNaN(lastPanMidX)) {
                    mPosX += mx - lastPanMidX;
                    mPosY += my - lastPanMidY;
                }
                lastPanMidX = mx;
                lastPanMidY = my;
                invalidate();
            }
            if (mode == Mode.DRAG_FURNITURE && selectedFurniture != null && action == MotionEvent.ACTION_POINTER_DOWN) {
                furnitureRotateStartRad = (float) Math.toRadians(selectedFurniture.rotationDeg);
                furnitureRotatePointerAngleStart = pointerAngle(event);
            }
            if (action == MotionEvent.ACTION_MOVE && mode == Mode.DRAG_FURNITURE
                    && selectedFurniture != null && pointerCount >= 2) {
                float rotBefore = selectedFurniture.rotationDeg;
                float a = pointerAngle(event);
                float delta = a - furnitureRotatePointerAngleStart;
                selectedFurniture.rotationDeg = (float) Math.toDegrees(furnitureRotateStartRad + delta);
                while (selectedFurniture.rotationDeg > 180f) selectedFurniture.rotationDeg -= 360f;
                while (selectedFurniture.rotationDeg < -180f) selectedFurniture.rotationDeg += 360f;
                applyRotationSnapToWalls(selectedFurniture);
                if (!isFurnitureInsideAnyRoom(selectedFurniture)) {
                    selectedFurniture.rotationDeg = rotBefore;
                }
                invalidate();
            }
            if (action == MotionEvent.ACTION_POINTER_UP) {
                lastPanMidX = Float.NaN;
            }
            return true;
        }

        if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
            lastPanMidX = Float.NaN;
        }

        if (scaleDetector.isInProgress()) {
            return true;
        }

        // 2. ПОДГОТОВКА КООРДИНАТ
        mLastTouchX = event.getX();
        mLastTouchY = event.getY();

        float wx = screenToWorldX(event.getX());
        float wy = screenToWorldY(event.getY());

        // 3. НОВАЯ ЛОГИКА: КЛИК ПО ТЕКСТУ РАЗМЕРОВ
        // Проверяем только при одном касании и нажатии вниз
        if (action == MotionEvent.ACTION_DOWN && pointerCount == 1) {
            for (DimensionHitBox hitBox : currentHitBoxes) {
                // Создаем область касания с учетом зума (mScaleFactor)
                RectF touchArea = new RectF(hitBox.bounds);
                // Расширяем область на 20 пикселей для удобства нажатия
                float padding = 20f / mScaleFactor;
                touchArea.inset(-padding, -padding);

                if (touchArea.contains(wx, wy)) {
                    if (dimensionClickListener != null) {
                        highlightedWall = hitBox.targetWall;
                        dimensionClickListener.onDimensionClicked(hitBox.item, hitBox.targetWall, hitBox.currentDistance);
                        invalidate();
                    }
                    return true; // Событие поглощено, дальше не идет
                }
            }
        }

        // 4. СТАНДАРТНЫЕ ЖЕСТЫ И РЕЖИМЫ
        boolean handled = gestureDetector.onTouchEvent(event);

        switch (mode) {
            case NONE:
            case SELECT:
                return onTouchNoneMode(event, wx, wy, handled);
            case WALL:
                return onTouchWallMode(event, wx, wy, handled);
            case DOOR:
            case WINDOW:
                return onTouchOpeningMode(event, wx, wy);
            case ANGLE:
                return onTouchAngleMode(event, wx, wy);
            case DELETE:
                return onTouchDeleteMode(event, wx, wy);
            case FURNITURE:
                return true;
            case DRAG_FURNITURE:
                return onTouchDragFurniture(event, wx, wy);
            case TAPE_MEASURE:
                return onTouchTapeMeasure(event, wx, wy);
            default:
                return true;
        }
    }
    private boolean onTouchTapeMeasure(MotionEvent event, float wx, float wy) {
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                if (tapeB != null) {
                    tapeA = new PointF(wx, wy);
                    tapeB = null;
                } else if (tapeA == null) {
                    tapeA = new PointF(wx, wy);
                }
                tapePreview = new PointF(wx, wy);
                invalidate();
                return true;
            case MotionEvent.ACTION_MOVE:
                tapePreview = new PointF(wx, wy);
                invalidate();
                return true;
            case MotionEvent.ACTION_UP:
                tapePreview = null;
                if (tapeA != null && tapeB == null) {
                    tapeB = new PointF(wx, wy);
                }
                invalidate();
                return true;
            default:
                return true;
        }
    }

    /**
     * True if world point is within {@code maxDistPx} of any wall segment.
     */
    public boolean isNearWall(float worldX, float worldY, float maxDistPx) {
        for (Wall w : collectAllWallsForHandles()) {
            if (distanceToSegment(worldX, worldY, w.start, w.end) <= maxDistPx) {
                return true;
            }
        }
        return false;
    }

    private boolean onTouchNoneMode(MotionEvent event, float wx, float wy, boolean gestureHandled) {
        if (cornerDragActive) {
            return handleCornerDragTouch(event, wx, wy);
        }
        if (event.getActionMasked() == MotionEvent.ACTION_DOWN) {
            highlightedWall = null;
            PointF corner = findCornerAt(wx, wy);
            if (corner != null) {
                saveState();
                cornerDragAnchor = corner;
                cornerDragPoints.clear();
                collectPointsEqualTo(corner, cornerDragPoints);
                cornerDragActive = true;
                invalidate();
                return true;
            }
        }
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                Wall wallHit = pickWallForEdit(wx, wy);
                if (wallHit != null && !isNearCorner(wx, wy, wallHit)) {
                    highlightedWall = wallHit;
                    float len = lengthMeters(wallHit);
                    if (editorCallback != null) {
                        editorCallback.onWallEditRequested(wallHit, len);
                    }
                    invalidate();
                    return true;
                }
                return true;
            default:
                return true;
        }
    }

    private boolean handleCornerDragTouch(MotionEvent event, float wx, float wy) {
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                return true;
            case MotionEvent.ACTION_MOVE:
                float[] snapped = snapWorldPoint(wx, wy, cornerDragAnchor);
                PointF neighbor = firstNeighborOpposite(cornerDragAnchor);
                float tx = snapped[0];
                float ty = snapped[1];
                if (neighbor != null) {
                    float[] ortho = snapSegmentEndToOrtho(neighbor.x, neighbor.y, tx, ty);
                    tx = ortho[0];
                    ty = ortho[1];
                }
                for (PointF p : cornerDragPoints) {
                    p.x = tx;
                    p.y = ty;
                }
                setGuideProbe(tx, ty);
                recomputeRoomsAfterGeometryChange();
                invalidate();
                return true;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                mergeCornerIfNeeded(cornerDragAnchor, cornerDragPoints);
                cornerDragActive = false;
                cornerDragPoints.clear();
                cornerDragAnchor = null;
                clearGuideProbe();
                recomputeRoomsAfterGeometryChange();
                invalidate();
                return true;
            default:
                return true;
        }
    }

    private PointF firstNeighborOpposite(PointF corner) {
        for (Wall w : collectAllWallsForHandles()) {
            if (w.start == corner) return w.end;
            if (w.end == corner) return w.start;
        }
        return null;
    }

    private boolean onTouchWallMode(MotionEvent event, float wx, float wy, boolean gestureHandled) {
        if (cornerDragActive) {
            return handleCornerDragTouch(event, wx, wy);
        }

        if (event.getActionMasked() == MotionEvent.ACTION_DOWN) {
            highlightedWall = null;
            PointF corner = findCornerAt(wx, wy);
            if (corner != null) {
                saveState();
                cornerDragAnchor = corner;
                cornerDragPoints.clear();
                collectPointsEqualTo(corner, cornerDragPoints);
                cornerDragActive = true;
                invalidate();
                return true;
            }
        }

        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                Wall wallHit = pickWallForEdit(wx, wy);
                if (wallHit != null && !isNearCorner(wx, wy, wallHit)) {
                    highlightedWall = wallHit;
                    float len = lengthMeters(wallHit);
                    if (editorCallback != null) {
                        editorCallback.onWallEditRequested(wallHit, len);
                    }
                    invalidate();
                    return true;
                }
                if (!gestureHandled) {
                    beginOrContinueWallChain(wx, wy);
                }
                return true;
            case MotionEvent.ACTION_MOVE:
                if (previewEnd != null && chainLastVertex != null) {
                    float[] s = snapWorldPoint(wx, wy, null);
                    float[] o = snapSegmentEndToOrtho(chainLastVertex.x, chainLastVertex.y, s[0], s[1]);
                    previewEnd.set(o[0], o[1]);
                    setGuideProbe(previewEnd.x, previewEnd.y);
                    invalidate();
                }
                return true;
            case MotionEvent.ACTION_UP:
                if (previewEnd != null) {
                    finishWallSegment(wx, wy);
                }
                clearGuideProbe();
                return true;
            default:
                return true;
        }
    }

    private boolean isNearCorner(float wx, float wy, Wall wall) {
        return dist(wx, wy, wall.start.x, wall.start.y) < CORNER_HIT_RADIUS
                || dist(wx, wy, wall.end.x, wall.end.y) < CORNER_HIT_RADIUS;
    }

    private Wall pickWallForEdit(float wx, float wy) {
        Wall best = null;
        float bestD = WALL_PICK_HALF_WIDTH;
        for (Wall w : collectAllWallsForHandles()) {
            float d = distanceToSegment(wx, wy, w.start, w.end);
            if (d < bestD) {
                bestD = d;
                best = w;
            }
        }
        return best;
    }

    private void beginOrContinueWallChain(float wx, float wy) {
        float[] s = snapWorldPoint(wx, wy, null);
        float sx = s[0], sy = s[1];
        if (chainLastVertex == null) {
            saveState();
            PointF p = new PointF(sx, sy);
            chainStartVertex = p;
            chainLastVertex = p;
            previewEnd = new PointF(sx, sy);
        } else {
            previewEnd = new PointF(sx, sy);
        }
        invalidate();
    }
    // Add to DrawingView.java
    public interface OnDimensionClickListener {
        void onDimensionClicked(FurnitureItem item, Wall targetWall, float currentDistanceMeters);
    }

    private OnDimensionClickListener dimensionClickListener;

    public void setOnDimensionClickListener(OnDimensionClickListener listener) {
        this.dimensionClickListener = listener;
    }

    // Temporary storage for hit detection (cleared and rebuilt every frame)
    private static class DimensionHitBox {
        RectF bounds;
        FurnitureItem item;
        Wall targetWall;
        float currentDistance;

        DimensionHitBox(RectF bounds, FurnitureItem item, Wall targetWall, float currentDistance) {
            this.bounds = bounds;
            this.item = item;
            this.targetWall = targetWall;
            this.currentDistance = currentDistance;
        }
    }

    private List<DimensionHitBox> currentHitBoxes = new ArrayList<>();
    // Store the highlighted wall to give visual feedback during editing
    private Wall highlightedWall = null;

    public void setHighlightedWall(Wall wall) {
        this.highlightedWall = wall;
        invalidate();
    }
    private void finishWallSegment(float wx, float wy) {
        float[] s = snapWorldPoint(wx, wy, chainLastVertex);
        float ex = s[0], ey = s[1];
        if (chainLastVertex != null) {
            float[] o = snapSegmentEndToOrtho(chainLastVertex.x, chainLastVertex.y, ex, ey);
            ex = o[0];
            ey = o[1];
        }
        PointF target = new PointF(ex, ey);
        PointF existing = findVertexNear(ex, ey, SNAP_RADIUS, chainLastVertex);
        if (existing != null) {
            target = existing;
        }

        if (chainLastVertex != null && chainStartVertex != null && chainWalls.size() >= 2) {
            float dClose = dist(target.x, target.y, chainStartVertex.x, chainStartVertex.y);
            if (dClose < AUTO_CLOSE_PX) {
                target = chainStartVertex;
            }
        }

        if (chainLastVertex != null && chainStartVertex != null
                && chainWalls.size() >= 2
                && target == chainStartVertex) {
            saveState();
            Wall closing = new Wall(chainLastVertex, chainStartVertex);
            chainWalls.add(closing);
            rooms.add(new Room(new ArrayList<>(chainWalls)));
            chainWalls.clear();
            chainStartVertex = null;
            chainLastVertex = null;
            previewEnd = null;
            invalidate();
            return;
        }

        if (chainLastVertex != null && dist(chainLastVertex.x, chainLastVertex.y, target.x, target.y) < MIN_WALL_LENGTH_PX) {
            previewEnd = null;
            invalidate();
            return;
        }

        if (chainLastVertex != null) {
            saveState();
            Wall segment = new Wall(chainLastVertex, target);
            chainWalls.add(segment);
            chainLastVertex = target;
        }
        previewEnd = null;
        invalidate();
    }

    private boolean onTouchOpeningMode(MotionEvent event, float wx, float wy) {
        if (draggedOpening == null) {
            pendingOpeningType = mode == Mode.DOOR ? Wall.Opening.Type.DOOR : Wall.Opening.Type.WINDOW;
            draggedOpening = new Wall.Opening(pendingOpeningType, 0.5f);
        }
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
            case MotionEvent.ACTION_MOVE:
                updateOpeningDrag(wx, wy);
                invalidate();
                return true;
            case MotionEvent.ACTION_UP:
                if (openingTargetWall != null) {
                    openingTargetWall.openings.add(draggedOpening);
                }
                draggedOpening = null;
                openingTargetWall = null;
                invalidate();
                return true;
            default:
                return true;
        }
    }

    private void updateOpeningDrag(float x, float y) {
        Wall best = null;
        float bestD = 160f;
        float bestT = 0.5f;
        for (Wall w : collectAllWallsForHandles()) {
            float dx = w.end.x - w.start.x;
            float dy = w.end.y - w.start.y;
            float l2 = dx * dx + dy * dy;
            float t = l2 < 1e-6f ? 0f : Math.max(0, Math.min(1, ((x - w.start.x) * dx + (y - w.start.y) * dy) / l2));
            float px = w.start.x + t * dx;
            float py = w.start.y + t * dy;
            float d = dist(x, y, px, py);
            if (d < bestD) {
                bestD = d;
                best = w;
                bestT = t;
            }
        }
        openingTargetWall = best;
        if (draggedOpening != null) {
            draggedOpening.positionFactor = bestT;
        }
    }

    private boolean onTouchAngleMode(MotionEvent event, float wx, float wy) {
        if (event.getActionMasked() != MotionEvent.ACTION_UP) {
            return true;
        }
        Wall w = pickWallForEdit(wx, wy);
        if (w == null) return true;
        if (angleWallA == null) {
            angleWallA = w;
            highlightedWallAngle = w;
            invalidate();
            return true;
        }
        if (w == angleWallA) {
            return true;
        }
        angleWallB = w;
        PointF corner = sharedCorner(angleWallA, angleWallB);
        if (corner == null) {
            angleWallB = null;
            invalidate();
            return true;
        }
        if (editorCallback != null) {
            editorCallback.onAngleConfigurationRequested(angleWallA, angleWallB, corner);
        }
        angleWallA = null;
        angleWallB = null;
        highlightedWallAngle = null;
        invalidate();
        return true;
    }

    private boolean onTouchDeleteMode(MotionEvent event, float wx, float wy) {
        if (event.getActionMasked() != MotionEvent.ACTION_UP) return true;
        FurnitureItem f = pickFurniture(wx, wy);
        if (f != null) {
            saveState();
            furnitureItems.remove(f);
            if (selectedFurniture == f) selectedFurniture = null;
            invalidate();
            return true;
        }
        OpeningPick oh = pickOpening(wx, wy);
        if (oh != null) {
            saveState();
            oh.wall.openings.remove(oh.opening);
            invalidate();
            return true;
        }
        Wall wall = pickWallForEdit(wx, wy);
        if (wall != null) {
            if (editorCallback != null) {
                editorCallback.onWallDeleteConfirmationRequested(wall);
            }
            return true;
        }
        return true;
    }

    private boolean onTouchDragFurniture(MotionEvent event, float wx, float wy) {
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                furnitureRotateHandleDrag = false;
                furnitureScaleDrag = false;
                FurnitureItem hit = pickFurnitureWithHandles(wx, wy);
                if (hit != null && hit == selectedFurniture && hitDeleteHandle(hit, wx, wy)) {
                    saveState();
                    furnitureItems.remove(hit);
                    selectedFurniture = null;
                    invalidate();
                    return true;
                }
                if (hit != null && hitFlipHandle(hit, wx, wy)) {
                    saveState();
                    selectedFurniture = hit;
                    hit.cycleAttachmentSide();
                    invalidate();
                    return true;
                }
                if (hit != null && hitRotateHandle(hit, wx, wy)) {
                    saveState();
                    selectedFurniture = hit;
                    furnitureRotateHandleDrag = true;
                    furnitureHandleRotateBaseDeg = hit.rotationDeg;
                    furnitureHandleFingerStartDeg = (float) Math.toDegrees(
                            Math.atan2(wy - hit.position.y, wx - hit.position.x));
                    invalidate();
                    return true;
                }
                if (hit != null) {
                    int sc = hitScaleCorner(hit, wx, wy);
                    if (sc >= 0) {
                        saveState();
                        selectedFurniture = hit;
                        furnitureScaleDrag = true;
                        furnitureScaleCornerIndex = sc;
                        scaleBackupX = hit.position.x;
                        scaleBackupY = hit.position.y;
                        scaleBackupW = hit.widthPx;
                        scaleBackupD = hit.depthPx;
                        furnitureCornerLocalToWorld(hit, (sc + 2) % 4, scaleFixedWorld);
                        invalidate();
                        return true;
                    }
                }
                if (hit != null) {
                    selectedFurniture = hit;
                    saveState();
                    lastFurnitureWorldX = wx;
                    lastFurnitureWorldY = wy;
                    invalidate();
                    return true;
                }
                selectedFurniture = null;
                invalidate();
                return true;
            case MotionEvent.ACTION_MOVE:
                if (furnitureScaleDrag && selectedFurniture != null && event.getPointerCount() == 1) {
                    FurnitureItem f = selectedFurniture;
                    float ncx = (scaleFixedWorld.x + wx) * 0.5f;
                    float ncy = (scaleFixedWorld.y + wy) * 0.5f;
                    float vdx = wx - ncx;
                    float vdy = wy - ncy;
                    double rads = Math.toRadians(-f.rotationDeg);
                    float cos = (float) Math.cos(rads);
                    float sin = (float) Math.sin(rads);
                    float lx = vdx * cos - vdy * sin;
                    float ly = vdx * sin + vdy * cos;
                    float newW = 2f * Math.abs(lx);
                    float newD = 2f * Math.abs(ly);
                    newW = Math.max(MIN_FURNITURE_SIZE_PX, newW);
                    newD = Math.max(MIN_FURNITURE_SIZE_PX, newD);
                    f.position.set(ncx, ncy);
                    f.widthPx = newW;
                    f.depthPx = newD;
                    if (!isFurnitureInsideAnyRoom(f)) {
                        f.position.set(scaleBackupX, scaleBackupY);
                        f.widthPx = scaleBackupW;
                        f.depthPx = scaleBackupD;
                    }
                    invalidate();
                    return true;
                }
                if (furnitureRotateHandleDrag && selectedFurniture != null && event.getPointerCount() == 1) {
                    float rotBefore = selectedFurniture.rotationDeg;
                    float cur = (float) Math.toDegrees(
                            Math.atan2(wy - selectedFurniture.position.y, wx - selectedFurniture.position.x));
                    selectedFurniture.rotationDeg = furnitureHandleRotateBaseDeg + (cur - furnitureHandleFingerStartDeg);
                    while (selectedFurniture.rotationDeg > 180f) selectedFurniture.rotationDeg -= 360f;
                    while (selectedFurniture.rotationDeg < -180f) selectedFurniture.rotationDeg += 360f;
                    applyRotationSnapToWalls(selectedFurniture);
                    if (!isFurnitureInsideAnyRoom(selectedFurniture)) {
                        selectedFurniture.rotationDeg = rotBefore;
                    }
                    setGuideProbe(selectedFurniture.position.x, selectedFurniture.position.y);
                    invalidate();
                    return true;
                }
                if (event.getPointerCount() == 1 && selectedFurniture != null) {
                    float ox = selectedFurniture.position.x;
                    float oy = selectedFurniture.position.y;
                    selectedFurniture.position.x += wx - lastFurnitureWorldX;
                    selectedFurniture.position.y += wy - lastFurnitureWorldY;
                    if (!isFurnitureInsideAnyRoom(selectedFurniture)) {
                        selectedFurniture.position.set(ox, oy);
                    } else {
                        applyFurnitureWallSnapAndBounds(selectedFurniture);
                        if (!isFurnitureInsideAnyRoom(selectedFurniture)) {
                            selectedFurniture.position.set(ox, oy);
                        } else {
                            lastFurnitureWorldX = wx;
                            lastFurnitureWorldY = wy;
                        }
                    }
                    setGuideProbe(selectedFurniture.position.x, selectedFurniture.position.y);
                    invalidate();
                }
                return true;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                furnitureRotateHandleDrag = false;
                furnitureScaleDrag = false;
                furnitureScaleCornerIndex = -1;
                clearGuideProbe();
                if (selectedFurniture != null) {
                    applyFurnitureWallSnapAndBounds(selectedFurniture);
                    invalidate();
                }
                return true;
            default:
                return true;
        }
    }

    private FurnitureItem pickFurnitureWithHandles(float wx, float wy) {
        for (int i = furnitureItems.size() - 1; i >= 0; i--) {
            FurnitureItem f = furnitureItems.get(i);
            if (hitRotateHandle(f, wx, wy) || hitDeleteHandle(f, wx, wy) || hitFlipHandle(f, wx, wy)) {
                return f;
            }
            if (hitScaleCorner(f, wx, wy) >= 0) {
                return f;
            }
            if (hitTestFurniture(f, wx, wy)) {
                return f;
            }
        }
        return null;
    }

    private void furnitureCornerLocalToWorld(FurnitureItem f, int cornerIdx, PointF out) {
        float hw = f.widthPx / 2f;
        float hd = f.depthPx / 2f;
        float lx;
        float ly;
        switch (cornerIdx & 3) {
            case 0:
                lx = -hw;
                ly = -hd;
                break;
            case 1:
                lx = hw;
                ly = -hd;
                break;
            case 2:
                lx = hw;
                ly = hd;
                break;
            default:
                lx = -hw;
                ly = hd;
                break;
        }
        localDeltaToWorld(f, lx, ly, out);
    }

    private int hitScaleCorner(FurnitureItem f, float wx, float wy) {
        if (f != selectedFurniture) {
            return -1;
        }
        for (int i = 0; i < 4; i++) {
            furnitureCornerLocalToWorld(f, i, tmpPoint);
            if (dist(wx, wy, tmpPoint.x, tmpPoint.y) <= SCALE_HANDLE_HIT_RADIUS) {
                return i;
            }
        }
        return -1;
    }

    private boolean hitRotateHandle(FurnitureItem f, float wx, float wy) {
        PointF p = new PointF();
        float hd = f.depthPx / 2f;
        localDeltaToWorld(f, 0, -hd - 55f, p);
        return dist(wx, wy, p.x, p.y) <= HANDLE_RADIUS + 6f;
    }

    private boolean hitDeleteHandle(FurnitureItem f, float wx, float wy) {
        PointF p = new PointF();
        float hw = f.widthPx / 2f;
        float hd = f.depthPx / 2f;
        localDeltaToWorld(f, hw + 32f, -hd - 32f, p);
        return dist(wx, wy, p.x, p.y) <= HANDLE_RADIUS;
    }

    private boolean hitFlipHandle(FurnitureItem f, float wx, float wy) {
        PointF p = new PointF();
        float hw = f.widthPx / 2f;
        float hd = f.depthPx / 2f;
        localDeltaToWorld(f, -hw - 32f, -hd - 32f, p);
        return dist(wx, wy, p.x, p.y) <= HANDLE_RADIUS;
    }

    private float screenToWorldX(float sx) {
        return (sx - mPosX) / mScaleFactor;
    }

    private float screenToWorldY(float sy) {
        return (sy - mPosY) / mScaleFactor;
    }

    private float lengthMeters(Wall w) {
        return (float) (Math.hypot(w.end.x - w.start.x, w.end.y - w.start.y) / pixelsPerMeter);
    }

    private PointF findCornerAt(float x, float y) {
        for (Wall w : collectAllWallsForHandles()) {
            if (dist(x, y, w.start.x, w.start.y) < CORNER_HIT_RADIUS) return w.start;
            if (dist(x, y, w.end.x, w.end.y) < CORNER_HIT_RADIUS) return w.end;
        }
        return null;
    }

    private void collectPointsEqualTo(PointF anchor, List<PointF> out) {
        for (Wall w : collectAllWallsForHandles()) {
            if (w.start == anchor) out.add(w.start);
            if (w.end == anchor) out.add(w.end);
        }
    }

    private void moveAllPointsAt(float x, float y, float nx, float ny) {
        List<PointF> pts = new ArrayList<>();
        collectPointsAtCoordinates(x, y, pts);
        for (PointF p : pts) {
            p.x = nx;
            p.y = ny;
        }
    }

    private void collectPointsAtCoordinates(float x, float y, List<PointF> out) {
        final float eps = 0.5f;
        for (Wall w : collectAllWallsForHandles()) {
            if (Math.abs(w.start.x - x) < eps && Math.abs(w.start.y - y) < eps) {
                if (!out.contains(w.start)) out.add(w.start);
            }
            if (Math.abs(w.end.x - x) < eps && Math.abs(w.end.y - y) < eps) {
                if (!out.contains(w.end)) out.add(w.end);
            }
        }
    }

    private PointF findVertexNear(float x, float y, float radius, PointF except) {
        PointF best = null;
        float bestD = radius;
        for (Wall w : collectAllWallsForHandles()) {
            for (PointF c : new PointF[]{w.start, w.end}) {
                if (c == except) continue;
                float d = dist(x, y, c.x, c.y);
                if (d < bestD) {
                    bestD = d;
                    best = c;
                }
            }
        }
        return best;
    }

    /** Snap world (x,y) to nearest existing vertex within {@link #SNAP_RADIUS}, optionally excluding a point. */
    private float[] snapWorldPoint(float x, float y, PointF exclude) {
        PointF v = findVertexNear(x, y, SNAP_RADIUS, exclude);
        if (v != null) {
            return new float[]{v.x, v.y};
        }
        return new float[]{x, y};
    }

    private void mergeCornerIfNeeded(PointF anchor, List<PointF> dragged) {
        if (anchor == null || dragged.isEmpty()) return;
        PointF target = findVertexNear(anchor.x, anchor.y, SNAP_RADIUS, anchor);
        if (target == null || target == anchor) return;
        for (Wall w : collectAllWallsForHandles()) {
            if (dragged.contains(w.start)) w.start = target;
            if (dragged.contains(w.end)) w.end = target;
        }
    }

    private PointF sharedCorner(Wall a, Wall b) {
        if (a.start == b.start || a.start == b.end) return a.start;
        if (a.end == b.start || a.end == b.end) return a.end;
        return null;
    }

    private void removeWallEverywhere(Wall wall) {
        for (Room room : new ArrayList<>(rooms)) {
            List<Wall> rw = room.getWalls();
            if (rw.contains(wall)) {
                rw.remove(wall);
                rooms.remove(room);
                for (Wall rem : rw) {
                    if (!orphanWalls.contains(rem)) {
                        orphanWalls.add(rem);
                    }
                }
            }
        }
        chainWalls.remove(wall);
        orphanWalls.remove(wall);
        lockedWalls.remove(wall);
    }

    /**
     * After moving corners, dissolve any room whose wall ring no longer matches head-to-tail connectivity.
     */
    private void recomputeRoomsAfterGeometryChange() {
        for (Room room : new ArrayList<>(rooms)) {
            if (!isClosedPolygon(room.getWalls())) {
                rooms.remove(room);
                for (Wall w : room.getWalls()) {
                    if (!orphanWalls.contains(w)) {
                        orphanWalls.add(w);
                    }
                }
            }
        }
    }

    private boolean isClosedPolygon(List<Wall> walls) {
        int n = walls.size();
        if (n < 3) return false;
        for (int i = 0; i < n; i++) {
            Wall cur = walls.get(i);
            Wall nxt = walls.get((i + 1) % n);
            if (cur.end != nxt.start) {
                return false;
            }
        }
        return true;
    }

    private FurnitureItem pickFurniture(float wx, float wy) {
        for (int i = furnitureItems.size() - 1; i >= 0; i--) {
            FurnitureItem f = furnitureItems.get(i);
            if (hitTestFurniture(f, wx, wy)) return f;
        }
        return null;
    }

    private boolean hitTestFurniture(FurnitureItem f, float wx, float wy) {
        double rad = Math.toRadians(-f.rotationDeg);
        float cos = (float) Math.cos(rad);
        float sin = (float) Math.sin(rad);
        float lx = wx - f.position.x;
        float ly = wy - f.position.y;
        float localX = lx * cos - ly * sin;
        float localY = lx * sin + ly * cos;
        float hw = f.widthPx / 2f + FURNITURE_PICK_PADDING;
        float hd = f.depthPx / 2f + FURNITURE_PICK_PADDING;
        return localX >= -hw && localX <= hw && localY >= -hd && localY <= hd;
    }

    private static final class OpeningPick {
        final Wall wall;
        final Wall.Opening opening;

        OpeningPick(Wall wall, Wall.Opening opening) {
            this.wall = wall;
            this.opening = opening;
        }
    }

    private OpeningPick pickOpening(float x, float y) {
        for (Wall w : collectAllWallsForHandles()) {
            for (Wall.Opening op : w.openings) {
                float t = op.positionFactor;
                float ox = w.start.x + (w.end.x - w.start.x) * t;
                float oy = w.start.y + (w.end.y - w.start.y) * t;
                if (dist(x, y, ox, oy) < OPENING_PICK_RADIUS) {
                    return new OpeningPick(w, op);
                }
            }
        }
        return null;
    }

    private static float dist(float x1, float y1, float x2, float y2) {
        return (float) Math.hypot(x1 - x2, y1 - y2);
    }

    private static float distanceToSegment(float px, float py, PointF a, PointF b) {
        float l2 = (float) Math.hypot(b.x - a.x, b.y - a.y);
        l2 *= l2;
        if (l2 < 1e-6f) return dist(px, py, a.x, a.y);
        float t = ((px - a.x) * (b.x - a.x) + (py - a.y) * (b.y - a.y)) / l2;
        t = Math.max(0, Math.min(1, t));
        float qx = a.x + t * (b.x - a.x);
        float qy = a.y + t * (b.y - a.y);
        return dist(px, py, qx, qy);
    }

    private static float pointerAngle(MotionEvent e) {
        float x0 = e.getX(0);
        float y0 = e.getY(0);
        float x1 = e.getX(1);
        float y1 = e.getY(1);
        return (float) Math.atan2(y1 - y0, x1 - x0);
    }

    /** Snap free end B toward horizontal/vertical from fixed A when within {@link #ORTHO_SNAP_DEG}. */
    private float[] snapSegmentEndToOrtho(float ax, float ay, float bx, float by) {
        float dx = bx - ax;
        float dy = by - ay;
        double len = Math.hypot(dx, dy);
        if (len < 1e-3) {
            return new float[]{bx, by};
        }
        double angDeg = Math.toDegrees(Math.atan2(dy, dx));
        double bestDelta = 999;
        double snappedDeg = angDeg;
        for (double target : new double[]{0d, 90d, -90d, 180d, -180d}) {
            double d = Math.abs(Math.IEEEremainder(angDeg - target, 360));
            if (d > 180) d = 360 - d;
            if (d < bestDelta) {
                bestDelta = d;
                snappedDeg = target;
            }
        }
        if (bestDelta > ORTHO_SNAP_DEG) {
            return new float[]{bx, by};
        }
        double rad = Math.toRadians(snappedDeg);
        return new float[]{
                ax + (float) (Math.cos(rad) * len),
                ay + (float) (Math.sin(rad) * len)
        };
    }

    private void applyFurnitureWallSnapAndBounds(FurnitureItem f) {
        float ox = f.position.x;
        float oy = f.position.y;
        float or = f.rotationDeg;
        snapFurnitureWithAttachment(f);
        if (!isFurnitureInsideAnyRoom(f)) {
            f.position.set(ox, oy);
            f.rotationDeg = or;
        }
    }

    private void attachmentEdgeMidLocal(FurnitureItem f, PointF outLocal) {
        float hw = f.widthPx / 2f;
        float hd = f.depthPx / 2f;
        switch (f.attachmentSide & 3) {
            case 0:
                outLocal.set(0, -hd);
                break;
            case 1:
                outLocal.set(hw, 0);
                break;
            case 2:
                outLocal.set(0, hd);
                break;
            default:
                outLocal.set(-hw, 0);
                break;
        }
    }

    private void localDeltaToWorld(FurnitureItem f, float lx, float ly, PointF out) {
        double rad = Math.toRadians(f.rotationDeg);
        float c = (float) Math.cos(rad);
        float s = (float) Math.sin(rad);
        out.x = lx * c - ly * s + f.position.x;
        out.y = lx * s + ly * c + f.position.y;
    }

    private void snapFurnitureWithAttachment(FurnitureItem f) {
        PointF localMid = new PointF();
        attachmentEdgeMidLocal(f, localMid);
        PointF edgeW = new PointF();
        localDeltaToWorld(f, localMid.x, localMid.y, edgeW);
        Wall nearest = null;
        float bestD = FURNITURE_WALL_SNAP_PX * 1.5f;
        float bestT = 0f;
        for (Wall w : collectAllWallsForHandles()) {
            float t = projectOntoSegmentT(edgeW.x, edgeW.y, w.start, w.end);
            float px = w.start.x + t * (w.end.x - w.start.x);
            float py = w.start.y + t * (w.end.y - w.start.y);
            float d = dist(edgeW.x, edgeW.y, px, py);
            if (d < bestD) {
                bestD = d;
                nearest = w;
                bestT = t;
            }
        }
        if (nearest != null) {
            float px = nearest.start.x + bestT * (nearest.end.x - nearest.start.x);
            float py = nearest.start.y + bestT * (nearest.end.y - nearest.start.y);
            f.position.x += px - edgeW.x;
            f.position.y += py - edgeW.y;
            float ux = nearest.end.x - nearest.start.x;
            float uy = nearest.end.y - nearest.start.y;
            float ul = (float) Math.hypot(ux, uy);
            if (ul > 1e-3f) {
                ux /= ul;
                uy /= ul;
            }
            f.rotationDeg = (float) Math.toDegrees(Math.atan2(uy, ux)) + 90f;
        }
    }

    private static float projectOntoSegmentT(float x, float y, PointF a, PointF b) {
        float dx = b.x - a.x;
        float dy = b.y - a.y;
        float l2 = dx * dx + dy * dy;
        if (l2 < 1e-6f) return 0f;
        return Math.max(0f, Math.min(1f, ((x - a.x) * dx + (y - a.y) * dy) / l2));
    }

    /**
     * True if every corner of the furniture footprint lies inside the room polygon (closed path).
     */
    public boolean isInsideRoom(FurnitureItem item, Room room) {
        if (item == null || room == null || room.getWalls().size() < 3) {
            return false;
        }
        Path floor = buildRoomFloorPath(room);
        for (PointF c : furnitureCornersWorld(item)) {
            if (!regionContainsPathPoint(floor, c.x, c.y)) {
                return false;
            }
        }
        return true;
    }

    private boolean isFurnitureInsideAnyRoom(FurnitureItem f) {
        if (rooms.isEmpty()) {
            return true;
        }
        for (Room r : rooms) {
            if (isInsideRoom(f, r)) {
                return true;
            }
        }
        return false;
    }

    private static Path buildRoomFloorPath(Room room) {
        Path path = new Path();
        List<Wall> w = room.getWalls();
        if (w.isEmpty()) {
            return path;
        }
        path.moveTo(w.get(0).start.x, w.get(0).start.y);
        for (Wall wall : w) {
            path.lineTo(wall.end.x, wall.end.y);
        }
        path.close();
        return path;
    }

    private static boolean regionContainsPathPoint(Path closedPath, float x, float y) {
        RectF bounds = new RectF();
        closedPath.computeBounds(bounds, true);
        Region region = new Region();
        if (!region.setPath(closedPath, new Region(
                (int) Math.floor(bounds.left - 2),
                (int) Math.floor(bounds.top - 2),
                (int) Math.ceil(bounds.right + 2),
                (int) Math.ceil(bounds.bottom + 2)))) {
            return false;
        }
        return region.contains((int) x, (int) y);
    }

    private boolean pointInRoomFloor(Room room, float x, float y) {
        if (room.getWalls().isEmpty()) {
            return false;
        }
        Path floor = buildRoomFloorPath(room);
        return regionContainsPathPoint(floor, x, y);
    }

    private void applyRotationSnapToWalls(FurnitureItem f) {
        float r = f.rotationDeg;
        float best = r;
        float bestDiff = FURNITURE_ROT_SNAP_DEG + 1f;
        for (Wall w : collectAllWallsForHandles()) {
            float a = (float) Math.toDegrees(Math.atan2(w.end.y - w.start.y, w.end.x - w.start.x));
            for (float target : new float[]{a, a + 90f}) {
                float diff = angularParallelDiffDeg(r, target);
                if (diff < bestDiff && diff <= FURNITURE_ROT_SNAP_DEG) {
                    bestDiff = diff;
                    best = target;
                }
            }
        }
        if (bestDiff <= FURNITURE_ROT_SNAP_DEG) {
            f.rotationDeg = normalizeDeg180(best);
        }
    }

    /** Smallest angle between orientation {@code r} and parallel class of {@code wallLineDeg}. */
    private static float angularParallelDiffDeg(float r, float wallLineDeg) {
        float d = Math.abs((float) Math.IEEEremainder(r - wallLineDeg, 180));
        if (d > 90f) {
            d = 180f - d;
        }
        return d;
    }
    /**
     * Находит до 4-х ближайших сегментов стен к мебели.
     */
    private List<Wall> findNearestWalls(FurnitureItem f) {
        List<Wall> allWalls = collectAllWallsForHandles(); // Берем все стены из комнат и одиночные
        List<Wall> nearest = new ArrayList<>();
        PointF center = f.getCenter();

        // Сортируем стены по расстоянию до центра мебели
        allWalls.sort((w1, w2) -> {
            float d1 = calculateDistance(center, w1);
            float d2 = calculateDistance(center, w2);
            return Float.compare(d1, d2);
        });

        // Берем первые 4 (или меньше, если стен мало)
        for (int i = 0; i < Math.min(4, allWalls.size()); i++) {
            nearest.add(allWalls.get(i));
        }
        return nearest;
    }

    /**
     * Вычисляет кратчайшее расстояние от точки до отрезка (стены).
     */
    private float calculateDistance(PointF p, Wall wall) {
        float x1 = wall.start.x;
        float y1 = wall.start.y;
        float x2 = wall.end.x;
        float y2 = wall.end.y;

        float l2 = (x2 - x1) * (x2 - x1) + (y2 - y1) * (y2 - y1);
        if (l2 == 0) return (float) Math.hypot(p.x - x1, p.y - y1);

        float t = ((p.x - x1) * (x2 - x1) + (p.y - y1) * (y2 - y1)) / l2;
        t = Math.max(0, Math.min(1, t));

        float projX = x1 + t * (x2 - x1);
        float projY = y1 + t * (y2 - y1);

        return (float) Math.hypot(p.x - projX, p.y - projY);
    }

    /**
     * Определяет точку на холсте, где будет рисоваться текст размера.
     * Обычно это середина перпендикуляра между мебелью и стеной.
     */
    private PointF getDimensionLabelPoint(FurnitureItem f, Wall wall) {
        PointF p = f.getCenter();

        // Находим ближайшую точку на стене (проекцию)
        float x1 = wall.start.x, y1 = wall.start.y;
        float x2 = wall.end.x, y2 = wall.end.y;
        float l2 = (x2 - x1) * (x2 - x1) + (y2 - y1) * (y2 - y1);
        float t = ((p.x - x1) * (x2 - x1) + (p.y - y1) * (y2 - y1)) / l2;
        t = Math.max(0, Math.min(1, t));

        float projX = x1 + t * (x2 - x1);
        float projY = y1 + t * (y2 - y1);

        // Возвращаем среднюю точку между центром мебели и точкой на стене
        return new PointF((p.x + projX) / 2f, (p.y + projY) / 2f);
    }
    private static float normalizeDeg180(float deg) {
        float x = (float) Math.IEEEremainder(deg, 360f);
        if (x > 180f) {
            x -= 360f;
        }
        if (x < -180f) {
            x += 360f;
        }
        return x;
    }

    private class ScaleListener extends ScaleGestureDetector.SimpleOnScaleGestureListener {
        @Override
        public boolean onScaleBegin(ScaleGestureDetector detector) {
            return true;
        }

        @Override
        public boolean onScale(ScaleGestureDetector detector) {
            float factor = detector.getScaleFactor();
            float newScale = Math.max(0.12f, Math.min(mScaleFactor * factor, 10f));
            float focusX = detector.getFocusX();
            float focusY = detector.getFocusY();
            float worldX = (focusX - mPosX) / mScaleFactor;
            float worldY = (focusY - mPosY) / mScaleFactor;
            mScaleFactor = newScale;
            mPosX = focusX - worldX * mScaleFactor;
            mPosY = focusY - worldY * mScaleFactor;
            invalidate();
            return true;
        }
    }

    // -------------------------------------------------------------------------
    // Theme support
    // -------------------------------------------------------------------------

    /**
     * Color palette for the 2D canvas. Instantiate with {@code isDark=true} for night mode.
     * All color values are Android ARGB integers (as returned by {@link Color#parseColor}).
     */
    public static final class CanvasTheme {
        public final int background;
        public final int wall;
        public final int wallPreview;
        public final int dimensionText;
        public final int roomText;
        public final int furnitureStroke;
        public final int guide;

        public CanvasTheme(boolean isDark) {
            if (isDark) {
                background    = Color.parseColor("#14141F");
                wall          = Color.parseColor("#D0D0E8");
                wallPreview   = Color.argb(180, 208, 208, 232);
                dimensionText = Color.parseColor("#9090A8");
                roomText      = Color.parseColor("#B0B0CC");
                furnitureStroke = Color.parseColor("#4ABAED");
                guide         = Color.argb(200, 80, 150, 204);
            } else {
                background    = Color.parseColor("#FAFAFA");
                wall          = Color.BLACK;
                wallPreview   = Color.argb(180, 0, 0, 0);
                dimensionText = Color.GRAY;
                roomText      = Color.DKGRAY;
                furnitureStroke = Color.parseColor("#4ABAED");
                guide         = Color.argb(200, 120, 190, 255);
            }
        }
    }

    /**
     * Applies a light or dark color palette to all canvas paints and triggers a redraw.
     * Call this from the Activity whenever the system night mode changes — no data is lost.
     */
    public void applyTheme(boolean isDark) {
        CanvasTheme t = new CanvasTheme(isDark);
        setBackgroundColor(t.background);

        wallPaint.setColor(t.wall);
        chainWallPaint.setColor(t.wall);
        orphanWallPaint.setColor(t.wall);
        previewPaint.setColor(t.wallPreview);
        dimensionPaint.setColor(t.dimensionText);
        textPaint.setColor(t.roomText);
        furnitureStrokePaint.setColor(t.furnitureStroke);
        guidePaint.setColor(t.guide);

        // Propagate to rooms (they have their own internal paints)
        for (Room room : rooms) {
            room.applyTheme(isDark, t.background);
        }
        // Sync cutout color for loose walls (not part of any room)
        for (Wall w : orphanWalls) w.cutoutColor = t.background;
        for (Wall w : chainWalls)  w.cutoutColor = t.background;

        invalidate();
    }

    // -------------------------------------------------------------------------
    // Floor plan serialization
    // -------------------------------------------------------------------------

    /**
     * Serializes the entire floor plan (rooms, loose walls, furniture) to a JSON object.
     * Coordinates are in canvas pixels; metric equivalents are included alongside.
     *
     * @throws org.json.JSONException if serialization fails (should never happen with valid data)
     */
    public JSONObject getFloorPlanJson() throws Exception {
        JSONObject root = new JSONObject();
        root.put("pixelsPerMeter", pixelsPerMeter);
        root.put("savedAt", System.currentTimeMillis());

        // --- Closed rooms ---
        JSONArray roomsArr = new JSONArray();
        for (Room room : rooms) {
            JSONObject roomObj = new JSONObject();
            JSONArray wallsArr = new JSONArray();
            for (Wall w : room.getWalls()) {
                wallsArr.put(wallToJson(w));
            }
            roomObj.put("walls", wallsArr);
            roomObj.put("areaSqMeters", room.computeAreaSqMeters(pixelsPerMeter));
            roomsArr.put(roomObj);
        }
        root.put("rooms", roomsArr);

        // --- Loose walls (chain + orphan, not yet forming a closed room) ---
        JSONArray looseArr = new JSONArray();
        for (Wall w : chainWalls)  looseArr.put(wallToJson(w));
        for (Wall w : orphanWalls) looseArr.put(wallToJson(w));
        root.put("looseWalls", looseArr);

        // --- Furniture ---
        JSONArray furnArr = new JSONArray();
        for (FurnitureItem f : furnitureItems) {
            JSONObject fObj = new JSONObject();
            fObj.put("id",             f.id);
            fObj.put("type",           f.type);
            fObj.put("x",              f.position.x);
            fObj.put("y",              f.position.y);
            fObj.put("widthPx",        f.widthPx);
            fObj.put("depthPx",        f.depthPx);
            fObj.put("widthM",         f.widthPx / pixelsPerMeter);
            fObj.put("depthM",         f.depthPx / pixelsPerMeter);
            fObj.put("rotationDeg",    f.rotationDeg);
            fObj.put("attachmentSide", f.attachmentSide);
            fObj.put("elevationM",     f.elevationM);
            furnArr.put(fObj);
        }
        root.put("furniture", furnArr);

        return root;
    }

    /**
     * Renders the current view to a Bitmap suitable for use as a save preview.
     * Uses a white background regardless of the active canvas theme so the preview
     * always looks clean in thumbnails and notifications.
     */
    public Bitmap exportPreviewBitmap() {
        return generateBlueprint();
    }

    // -------------------------------------------------------------------------
    // Floor plan loading
    // -------------------------------------------------------------------------

    /**
     * Restores the canvas from a JSON object previously produced by {@link #getFloorPlanJson()}.
     * Pushes the current state onto the undo stack before clearing, so the user can undo the load.
     */
    public void loadFloorPlanJson(JSONObject json) throws Exception {
        saveState();

        rooms.clear();
        chainWalls.clear();
        orphanWalls.clear();
        furnitureItems.clear();
        aiItems.clear();
        chainStartVertex = null;
        chainLastVertex  = null;
        selectedFurniture = null;

        float ppm = (float) json.optDouble("pixelsPerMeter", 100f);
        pixelsPerMeter = ppm;

        // Closed rooms
        JSONArray roomsArr = json.optJSONArray("rooms");
        if (roomsArr != null) {
            for (int i = 0; i < roomsArr.length(); i++) {
                JSONObject roomObj = roomsArr.getJSONObject(i);
                JSONArray  wallsArr = roomObj.optJSONArray("walls");
                if (wallsArr == null) continue;
                List<Wall> walls = deserializeWalls(wallsArr, ppm);
                if (!walls.isEmpty()) rooms.add(new Room(walls));
            }
        }

        // Loose walls (chain / orphan)
        JSONArray looseArr = json.optJSONArray("looseWalls");
        if (looseArr != null) {
            orphanWalls.addAll(deserializeWalls(looseArr, ppm));
        }

        // Furniture
        JSONArray furnArr = json.optJSONArray("furniture");
        int maxId = 0;
        if (furnArr != null) {
            for (int i = 0; i < furnArr.length(); i++) {
                JSONObject fObj = furnArr.getJSONObject(i);
                int   fId  = fObj.optInt("id", furnitureIdSeq + i);
                float x    = (float) fObj.optDouble("x", 0);
                float y    = (float) fObj.optDouble("y", 0);
                float wPx  = (float) fObj.optDouble("widthPx", 100);
                float dPx  = (float) fObj.optDouble("depthPx", 100);
                float rot  = (float) fObj.optDouble("rotationDeg", 0);
                FurnitureItem f = new FurnitureItem(fId, fObj.optString("type", ""),
                        new PointF(x, y), rot, false, 1, wPx, dPx);
                f.attachmentSide = fObj.optInt("attachmentSide", 0);
                f.elevationM     = (float) fObj.optDouble("elevationM", 0);
                furnitureItems.add(f);
                if (fId > maxId) maxId = fId;
            }
        }
        furnitureIdSeq = maxId + 1;

        invalidate();
    }

    /**
     * Deserializes a JSON array of wall objects, reconnecting shared corners via a coordinate
     * cache so coincident endpoints use the same {@link PointF} instance (required for the
     * corner-drag editor to work correctly after load).
     */
    private List<Wall> deserializeWalls(JSONArray arr, float ppm) throws Exception {
        java.util.HashMap<String, PointF> pts = new java.util.HashMap<>();
        List<Wall> result = new ArrayList<>();

        for (int i = 0; i < arr.length(); i++) {
            JSONObject wObj = arr.getJSONObject(i);
            // wallToJson() stores coordinates in meters (divided by ppm)
            float x1 = (float) (wObj.getDouble("x1") * ppm);
            float y1 = (float) (wObj.getDouble("y1") * ppm);
            float x2 = (float) (wObj.getDouble("x2") * ppm);
            float y2 = (float) (wObj.getDouble("y2") * ppm);

            Wall w = new Wall(cachedPoint(pts, x1, y1), cachedPoint(pts, x2, y2));
            w.thicknessPx = (float) wObj.optDouble("thicknessPx", 20f);
            String hex = wObj.optString("wallColorHex", "");
            if (!hex.isEmpty()) w.wallColorHex = hex;
            String tex = wObj.optString("wallTextureId", "");
            if (!tex.isEmpty()) w.wallTextureId = tex;

            JSONArray ops = wObj.optJSONArray("openings");
            if (ops != null) {
                for (int j = 0; j < ops.length(); j++) {
                    JSONObject opObj = ops.getJSONObject(j);
                    Wall.Opening.Type type = Wall.Opening.Type.valueOf(opObj.getString("type"));
                    // wallToJson() stores positionFactor as "pos", widthPx may be absent
                    float pos = (float) opObj.optDouble("pos", opObj.optDouble("positionFactor", 0.5));
                    Wall.Opening op = new Wall.Opening(type, pos);
                    op.widthPx = (float) opObj.optDouble("widthPx", 80f);
                    w.openings.add(op);
                }
            }
            result.add(w);
        }
        return result;
    }

    private static PointF cachedPoint(java.util.HashMap<String, PointF> cache, float x, float y) {
        String key = Math.round(x) + "," + Math.round(y);
        PointF p = cache.get(key);
        if (p == null) { p = new PointF(x, y); cache.put(key, p); }
        return p;
    }
}
