package com.inania.Anthuria;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.PointF;
import android.net.Uri;

import androidx.core.content.FileProvider;

import android.os.Bundle;
import android.provider.MediaStore;
import android.text.InputType;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

/**
 * Floor plan editor: tools, undo/redo, angle setup, furniture catalog, AI export.
 */
public class EditorActivity extends BaseActivity implements DrawingView.EditorCallback {

    private static final String API_KEY = "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";
    private static final String API_URL = "https://openrouter.ai/api/v1/chat/completions";
    private static final String MODEL_NAME = "google/gemini-2.0-flash-001";
    private static final String PREFS_NAME = "AnthuriaPrefs";
    private static final String KEY_SKIP_DELETE_CONFIRM = "skip_delete_confirm";

    /** Pass a plan id (from {@link FloorPlanStorage}) to open an existing plan for editing. */
    public static final String EXTRA_PLAN_ID = "plan_id";

    private final OkHttpClient client = new OkHttpClient();
    private DrawingView drawingView;
    private Spinner roomTypeSpinner;
    private String aiProposedFurnitureJson = null;

    private float wallHeight = 2.6f;
    private String wallColor = "#E0E0E0";

    private ImageButton btnWallMode;
    private ImageButton btnDoorMode;
    private ImageButton btnWindowMode;
    private ImageButton btnAngle;
    private ImageButton btnAddFurniture;
    private ImageButton btnDeleteMode;
    private ImageButton btnTapeMeasure;
    private ImageButton btnBlueprint;
    private ImageButton btnSave;

    private @Nullable View activeToolButton;

    private Wall pendingDeleteWall;
    private Wall angleWallA;
    private Wall angleWallB;
    private PointF angleCorner;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_editor);

        drawingView = findViewById(R.id.drawing_view);
        drawingView.setEditorCallback(this);

        roomTypeSpinner = findViewById(R.id.room_type_spinner);
        MaterialButton btnView3D = findViewById(R.id.btn_3d);
        ExtendedFloatingActionButton btnAi = findViewById(R.id.btn_ai_generate);
        ImageButton btnBack = findViewById(R.id.btn_back);

        btnWallMode = findViewById(R.id.btn_wall_mode);
        btnDoorMode = findViewById(R.id.btn_door_mode);
        btnWindowMode = findViewById(R.id.btn_window_mode);
        btnAngle = findViewById(R.id.btn_angle);
        btnAddFurniture = findViewById(R.id.btn_add_furniture);
        btnDeleteMode = findViewById(R.id.btn_delete_mode);
        btnTapeMeasure = findViewById(R.id.btn_tape_measure);
        btnBlueprint = findViewById(R.id.btn_blueprint);
        btnSave = findViewById(R.id.btn_save);

        MaterialButton btnUndo = findViewById(R.id.btn_undo);
        MaterialButton btnRedo = findViewById(R.id.btn_redo);
        btnRedo.setIconResource(android.R.drawable.ic_menu_rotate);

        String[] roomTypes = {"Кухня", "Спальня", "Гостиная", "Ванная"};
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, roomTypes);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        roomTypeSpinner.setAdapter(adapter);

        setupBackground();
        drawingView.setMode(DrawingView.Mode.WALL);
        updateToolSelection(btnWallMode);

        String planId = getIntent().getStringExtra(EXTRA_PLAN_ID);
        if (planId != null) loadPlanFromStorage(planId);

        btnBack.setOnClickListener(v -> finish());

        drawingView.setOnDimensionClickListener((item, targetWall, currentDistance) -> showEditDimensionDialog(item, targetWall, currentDistance));

        btnWallMode.setOnClickListener(v -> {
            if (drawingView.getMode() == DrawingView.Mode.WALL && v == activeToolButton) {
                drawingView.setMode(DrawingView.Mode.NONE);
                clearToolHighlight();
                Toast.makeText(this, R.string.tool_off, Toast.LENGTH_SHORT).show();
            } else {
                drawingView.setMode(DrawingView.Mode.WALL);
                updateToolSelection(v);
                Toast.makeText(this, R.string.wall_mode, Toast.LENGTH_SHORT).show();
            }
        });

        btnDoorMode.setOnClickListener(v -> {
            if (drawingView.getMode() == DrawingView.Mode.DOOR && v == activeToolButton) {
                drawingView.setMode(DrawingView.Mode.NONE);
                clearToolHighlight();
                return;
            }
            drawingView.setMode(DrawingView.Mode.DOOR);
            updateToolSelection(v);
            drawingView.startDraggingNewOpening(Wall.Opening.Type.DOOR);
        });

        btnWindowMode.setOnClickListener(v -> {
            if (drawingView.getMode() == DrawingView.Mode.WINDOW && v == activeToolButton) {
                drawingView.setMode(DrawingView.Mode.NONE);
                clearToolHighlight();
                return;
            }
            drawingView.setMode(DrawingView.Mode.WINDOW);
            updateToolSelection(v);
            drawingView.startDraggingNewOpening(Wall.Opening.Type.WINDOW);
        });

        btnAngle.setOnClickListener(v -> {
            if (drawingView.getMode() == DrawingView.Mode.ANGLE && v == activeToolButton) {
                drawingView.setMode(DrawingView.Mode.NONE);
                clearToolHighlight();
                return;
            }
            drawingView.setMode(DrawingView.Mode.ANGLE);
            updateToolSelection(v);
            Toast.makeText(this, R.string.angle_mode_hint, Toast.LENGTH_SHORT).show();
        });

        btnAddFurniture.setOnClickListener(v -> {
            DrawingView.Mode m = drawingView.getMode();
            if ((m == DrawingView.Mode.FURNITURE || m == DrawingView.Mode.DRAG_FURNITURE) && v == activeToolButton) {
                drawingView.setMode(DrawingView.Mode.NONE);
                clearToolHighlight();
                return;
            }
            drawingView.setMode(DrawingView.Mode.FURNITURE);
            updateToolSelection(v);
            showFurnitureBottomSheet();
        });

        btnDeleteMode.setOnClickListener(v -> {
            if (drawingView.getMode() == DrawingView.Mode.DELETE && v == activeToolButton) {
                drawingView.setMode(DrawingView.Mode.NONE);
                clearToolHighlight();
            } else {
                drawingView.setMode(DrawingView.Mode.DELETE);
                updateToolSelection(v);
                Toast.makeText(this, R.string.delete_mode, Toast.LENGTH_SHORT).show();
            }
        });

        btnAi.setOnClickListener(v -> sendPlanToAi(roomTypeSpinner.getSelectedItem().toString()));
        btnView3D.setOnClickListener(v -> open3DView());
        btnUndo.setOnClickListener(v -> drawingView.undo());
        btnRedo.setOnClickListener(v -> drawingView.redo());

        btnTapeMeasure.setOnClickListener(v -> {
            if (drawingView.getMode() == DrawingView.Mode.TAPE_MEASURE && v == activeToolButton) {
                drawingView.setMode(DrawingView.Mode.NONE);
                clearToolHighlight();
                Toast.makeText(this, R.string.tape_measure_off, Toast.LENGTH_SHORT).show();
            } else {
                drawingView.setMode(DrawingView.Mode.TAPE_MEASURE);
                updateToolSelection(v);
                Toast.makeText(this, R.string.tape_measure_hint, Toast.LENGTH_LONG).show();
            }
        });

        btnBlueprint.setOnClickListener(v -> shareBlueprint());
        btnSave.setOnClickListener(v -> saveFloorPlan());
    }

    private void showEditDimensionDialog(FurnitureItem item, Wall targetWall, float currentDistance) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(R.string.edit_distance);

        final EditText input = new EditText(this);
        input.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        input.setText(String.format(java.util.Locale.US, "%.2f", currentDistance));

        LinearLayout container = new LinearLayout(this);
        container.setPadding(50, 20, 50, 0);
        container.addView(input);
        builder.setView(container);

        builder.setPositiveButton(android.R.string.ok, (dialog, which) -> {
            try {
                float newDistance = Float.parseFloat(input.getText().toString());
                drawingView.moveFurnitureByDistance(item, targetWall, newDistance);
            } catch (NumberFormatException e) {
                Toast.makeText(EditorActivity.this, R.string.number_format_error, Toast.LENGTH_SHORT).show();
                drawingView.setHighlightedWall(null);
            }
        });

        builder.setNegativeButton(R.string.cancel, (dialog, which) -> {
            dialog.cancel();
            drawingView.setHighlightedWall(null);
        });

        builder.setOnCancelListener(dialog -> drawingView.setHighlightedWall(null));
        builder.show();
    }

    private void shareBlueprint() {
        try {
            Bitmap bmp = drawingView.generateBlueprint();
            File f = new File(getCacheDir(), "blueprint_share.png");
            try (FileOutputStream fos = new FileOutputStream(f)) {
                bmp.compress(Bitmap.CompressFormat.PNG, 100, fos);
            }
            Uri uri = FileProvider.getUriForFile(this, getPackageName() + ".fileprovider", f);
            Intent share = new Intent(Intent.ACTION_SEND);
            share.setType("image/png");
            share.putExtra(Intent.EXTRA_STREAM, uri);
            share.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(Intent.createChooser(share, getString(R.string.share_blueprint)));
        } catch (Exception e) {
            Toast.makeText(this, "Blueprint export failed", Toast.LENGTH_SHORT).show();
        }
    }

    private void updateToolSelection(View activeBtn) {
        activeToolButton = activeBtn;
        boolean isDark = (getResources().getConfiguration().uiMode
                & android.content.res.Configuration.UI_MODE_NIGHT_MASK)
                == android.content.res.Configuration.UI_MODE_NIGHT_YES;
        int highlight = isDark ? Color.parseColor("#1A3A5C") : Color.parseColor("#BBDEFB");
        int normal = Color.TRANSPARENT;
        btnWallMode.setBackgroundColor(normal);
        btnDoorMode.setBackgroundColor(normal);
        btnWindowMode.setBackgroundColor(normal);
        btnAngle.setBackgroundColor(normal);
        btnAddFurniture.setBackgroundColor(normal);
        btnDeleteMode.setBackgroundColor(normal);
        btnTapeMeasure.setBackgroundColor(normal);
        btnBlueprint.setBackgroundColor(normal);
        if (activeBtn != null) {
            activeBtn.setBackgroundColor(highlight);
        }
    }

    private void clearToolHighlight() {
        activeToolButton = null;
        int normal = Color.TRANSPARENT;
        btnWallMode.setBackgroundColor(normal);
        btnDoorMode.setBackgroundColor(normal);
        btnWindowMode.setBackgroundColor(normal);
        btnAngle.setBackgroundColor(normal);
        btnAddFurniture.setBackgroundColor(normal);
        btnDeleteMode.setBackgroundColor(normal);
        btnTapeMeasure.setBackgroundColor(normal);
        btnBlueprint.setBackgroundColor(normal);
    }

    @Override
    public void onWallEditRequested(Wall wall, float currentLengthMeters) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(R.string.wall_params);

        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(48, 32, 48, 16);

        final EditText input = new EditText(this);
        input.setHint(R.string.length_m);
        input.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        input.setText(String.format("%.2f", currentLengthMeters));
        layout.addView(input);

        final EditText thickInput = new EditText(this);
        thickInput.setHint(R.string.thickness_px);
        thickInput.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        thickInput.setText(String.format("%.0f", wall.getThicknessPx()));
        layout.addView(thickInput);

        final CheckBox lockCheck = new CheckBox(this);
        lockCheck.setText(R.string.lock_size);
        layout.addView(lockCheck);

        builder.setView(layout);
        builder.setPositiveButton(R.string.apply, (d, w) -> {
            try {
                String valStr = input.getText().toString().replace(",", ".");
                float newVal = Float.parseFloat(valStr);
                drawingView.updateWallLength(wall, newVal);
                drawingView.lockWallLength(wall, lockCheck.isChecked());
                String ts = thickInput.getText().toString().replace(",", ".").trim();
                if (!ts.isEmpty()) {
                    float tpx = Float.parseFloat(ts);
                    drawingView.updateWallThickness(wall, tpx);
                }
            } catch (Exception e) {
                Toast.makeText(this, R.string.number_format_error, Toast.LENGTH_SHORT).show();
            }
        });
        builder.setNegativeButton(R.string.cancel, null);
        builder.show();
    }

    @Override
    public void onAngleConfigurationRequested(Wall wallA, Wall wallB, PointF sharedCorner) {
        angleWallA = wallA;
        angleWallB = wallB;
        angleCorner = sharedCorner;
        showAngleDialog();
    }

    private void showAngleDialog() {
        if (angleWallA == null || angleWallB == null || angleCorner == null) return;

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(48, 24, 48, 16);

        TextView hint = new TextView(this);
        hint.setText(R.string.rotate_wall_q);
        root.addView(hint);

        RadioGroup group = new RadioGroup(this);
        RadioButton rbA = new RadioButton(this);
        rbA.setText(R.string.rotate_wall_a);
        rbA.setChecked(true);
        RadioButton rbB = new RadioButton(this);
        rbB.setText(R.string.rotate_wall_b);
        group.addView(rbA);
        group.addView(rbB);
        root.addView(group);

        TextView angLabel = new TextView(this);
        angLabel.setText(R.string.target_angle_label);
        angLabel.setPadding(0, 24, 0, 8);
        root.addView(angLabel);

        EditText angleInput = new EditText(this);
        angleInput.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        float cur = currentAngleBetweenAtCorner(angleWallA, angleWallB, angleCorner);
        angleInput.setText(String.format("%.1f", cur));
        root.addView(angleInput);

        new AlertDialog.Builder(this)
                .setTitle(R.string.wall_angle)
                .setView(root)
                .setPositiveButton(R.string.apply, (d, w) -> {
                    try {
                        float target = Float.parseFloat(angleInput.getText().toString().replace(",", "."));
                        Wall wallToRotate = rbA.isChecked() ? angleWallA : angleWallB;
                        Wall wallFixed = rbA.isChecked() ? angleWallB : angleWallA;
                        drawingView.applyTargetAngleBetweenWalls(wallFixed, wallToRotate, angleCorner, target);
                        drawingView.setMode(DrawingView.Mode.NONE);
                        clearToolHighlight();
                    } catch (Exception e) {
                        Toast.makeText(this, R.string.invalid_angle, Toast.LENGTH_SHORT).show();
                    }
                })
                .setNegativeButton(R.string.cancel, (d, w) -> {
                    drawingView.setMode(DrawingView.Mode.NONE);
                    clearToolHighlight();
                })
                .show();
    }

    private static float currentAngleBetweenAtCorner(Wall a, Wall b, PointF c) {
        PointF fa = far(a, c);
        PointF fb = far(b, c);
        if (fa == null || fb == null) return 90f;
        double aa = Math.atan2(fa.y - c.y, fa.x - c.x);
        double ab = Math.atan2(fb.y - c.y, fb.x - c.x);
        double diff = ab - aa;
        while (diff <= -Math.PI) diff += 2 * Math.PI;
        while (diff > Math.PI) diff -= 2 * Math.PI;
        return (float) Math.toDegrees(diff);
    }

    private static PointF far(Wall w, PointF c) {
        if (w.start == c) return w.end;
        if (w.end == c) return w.start;
        return null;
    }

    @Override
    public void onWallDeleteConfirmationRequested(Wall wall) {
        pendingDeleteWall = wall;
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        if (prefs.getBoolean(KEY_SKIP_DELETE_CONFIRM, false)) {
            drawingView.deleteWallAfterConfirmed(wall);
            pendingDeleteWall = null;
            return;
        }
        showDeleteConfirmationDialog();
    }

    private void showDeleteConfirmationDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(R.string.confirmation);
        builder.setMessage(R.string.delete_wall_msg);

        final CheckBox dontAskAgain = new CheckBox(this);
        dontAskAgain.setText(R.string.dont_ask_again);
        builder.setView(dontAskAgain);

        builder.setPositiveButton(R.string.delete, (dialog, which) -> {
            if (dontAskAgain.isChecked()) {
                getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
                        .edit().putBoolean(KEY_SKIP_DELETE_CONFIRM, true).apply();
            }
            if (pendingDeleteWall != null) {
                drawingView.deleteWallAfterConfirmed(pendingDeleteWall);
            }
            pendingDeleteWall = null;
        });
        builder.setNegativeButton(R.string.cancel, (dialog, which) -> pendingDeleteWall = null);
        builder.show();
    }

    private void showFurnitureBottomSheet() {
        BottomSheetDialog sheet = new BottomSheetDialog(this);
        ScrollView scroll = new ScrollView(this);
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(32, 16, 32, 32);

        TextView title = new TextView(this);
        title.setTextSize(18f);
        title.setText(R.string.add_furniture);
        content.addView(title);

        String roomLabel = roomTypeSpinner.getSelectedItem().toString();
        String catalogKey = FurnitureCatalog.catalogKeyForSpinnerLabel(roomLabel);
        List<String> typeIds = FurnitureCatalog.typesForRoom(catalogKey);
        List<CatalogRow> rows = new ArrayList<>();

        for (String typeId : typeIds) {
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setPadding(0, 12, 0, 12);

            CheckBox cb = new CheckBox(this);
            cb.setText(FurnitureCatalog.humanLabel(typeId));
            cb.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

            EditText count = new EditText(this);
            count.setHint(R.string.quantity);
            count.setText("1");
            count.setInputType(InputType.TYPE_CLASS_NUMBER);
            row.addView(cb);
            row.addView(count);
            content.addView(row);
            rows.add(new CatalogRow(typeId, cb, count));
        }

        MaterialButton apply = new MaterialButton(this);
        apply.setText(R.string.apply);
        apply.setOnClickListener(v -> {
            List<FurnitureItem> toAdd = new ArrayList<>();
            for (CatalogRow r : rows) {
                if (!r.check.isChecked()) continue;
                int n = 1;
                try { n = Math.max(1, Math.min(20, Integer.parseInt(r.count.getText().toString().trim()))); } catch (Exception ignored) {}
                float[] wh = FurnitureLayout.defaultSizePx(r.typeId);
                for (int i = 0; i < n; i++) toAdd.add(new FurnitureItem(0, r.typeId, new PointF(0, 0), 0f, false, 1, wh[0], wh[1]));
            }
            if (toAdd.isEmpty()) { Toast.makeText(this, R.string.select_items, Toast.LENGTH_SHORT).show(); return; }
            drawingView.addFurnitureFromCatalog(toAdd);
            sheet.dismiss();
            updateToolSelection(btnAddFurniture);
        });
        content.addView(apply);
        scroll.addView(content);
        sheet.setContentView(scroll);
        sheet.show();
    }

    private static final class CatalogRow {
        final String typeId;
        final CheckBox check;
        final EditText count;
        CatalogRow(String typeId, CheckBox check, EditText count) { this.typeId = typeId; this.check = check; this.count = count; }
    }

    private void open3DView() {
        try {
            String manual = drawingView.getManualFurnitureJson();
            String merged = mergeFurnitureJson(aiProposedFurnitureJson, manual);
            Intent intent = new Intent(this, Room3DActivity.class);
            intent.putExtra("json_plan", drawingView.getRoomDataAsJSON());
            intent.putExtra("json_furniture", merged);
            intent.putExtra("wall_height", wallHeight);
            intent.putExtra("wall_color", wallColor);
            startActivity(intent);
        } catch (Exception e) {
            Toast.makeText(this, R.string.error_3d_prep, Toast.LENGTH_SHORT).show();
        }
    }

    private static String mergeFurnitureJson(String aiJson, String manualJson) {
        try {
            JSONArray out = new JSONArray();
            if (aiJson != null && aiJson.trim().startsWith("[")) {
                JSONArray a = new JSONArray(aiJson);
                for (int i = 0; i < a.length(); i++) out.put(a.get(i));
            }
            if (manualJson != null && manualJson.trim().startsWith("[")) {
                JSONArray m = new JSONArray(manualJson);
                for (int i = 0; i < m.length(); i++) out.put(m.get(i));
            }
            return out.toString();
        } catch (Exception e) {
            return (manualJson != null && !manualJson.isEmpty()) ? manualJson : "[]";
        }
    }

    private void setupBackground() {
        String imageUriString = getIntent().getStringExtra("imageUri");
        if (imageUriString != null) {
            try {
                Bitmap bitmap = MediaStore.Images.Media.getBitmap(this.getContentResolver(), Uri.parse(imageUriString));
                drawingView.setBackgroundScan(bitmap);
            } catch (Exception e) {
                Log.e("EDITOR", "Failed to load background image", e);
            }
        }
    }

    private void sendPlanToAi(String roomType) {
        String planJson = drawingView.getRoomDataAsJSON();
        String prompt = "Act as an interior designer. Room plan (JSON, meters): " + planJson +
                ". Suggest furniture layout for a '" + roomType + "'. " +
                "Response MUST be ONLY a JSON array of objects with: type, x, y, width, depth, height, color, rotation.";

        JSONObject payload = new JSONObject();
        try {
            payload.put("model", MODEL_NAME);
            JSONArray messages = new JSONArray();
            messages.put(new JSONObject().put("role", "user").put("content", prompt));
            payload.put("messages", messages);
        } catch (Exception e) { e.printStackTrace(); }

        RequestBody body = RequestBody.create(payload.toString(), MediaType.get("application/json; charset=utf-8"));
        Request request = new Request.Builder()
                .url(API_URL)
                .addHeader("Authorization", "Bearer " + API_KEY)
                .post(body)
                .build();

        Toast.makeText(this, R.string.ai_analyzing, Toast.LENGTH_SHORT).show();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                runOnUiThread(() -> Toast.makeText(EditorActivity.this, R.string.connection_error, Toast.LENGTH_SHORT).show());
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                if (response.isSuccessful() && response.body() != null) {
                    try {
                        String data = response.body().string();
                        JSONObject json = new JSONObject(data);
                        String aiContent = json.getJSONArray("choices").getJSONObject(0).getJSONObject("message").getString("content");
                        processAiResult(aiContent);
                    } catch (Exception e) { Log.e("AI", "JSON parsing failed", e); }
                }
            }
        });
    }

    private void processAiResult(String aiText) {
        String cleanedJson = aiText;
        if (aiText.contains("[")) cleanedJson = aiText.substring(aiText.indexOf("["), aiText.lastIndexOf("]") + 1);
        final String finalJson = cleanedJson;
        runOnUiThread(() -> {
            aiProposedFurnitureJson = finalJson;
            Toast.makeText(this, R.string.ai_ready, Toast.LENGTH_LONG).show();
        });
    }

    private void saveFloorPlan() {
        Toast.makeText(this, R.string.saving, Toast.LENGTH_SHORT).show();
        new Thread(() -> {
            try {
                JSONObject planData = drawingView.getFloorPlanJson();
                planData.put("roomType",   roomTypeSpinner.getSelectedItem().toString());
                planData.put("wallHeight", wallHeight);
                planData.put("wallColor",  wallColor);

                Bitmap preview = drawingView.exportPreviewBitmap();
                FloorPlanStorage.save(EditorActivity.this, planData, preview);

                runOnUiThread(() -> Toast.makeText(this, R.string.saved, Toast.LENGTH_SHORT).show());
            } catch (Exception e) {
                runOnUiThread(() -> Toast.makeText(this, getString(R.string.error) + e.getMessage(), Toast.LENGTH_SHORT).show());
            }
        }).start();
    }

    private void loadPlanFromStorage(String planId) {
        new Thread(() -> {
            try {
                JSONObject json = FloorPlanStorage.load(EditorActivity.this, planId);
                runOnUiThread(() -> {
                    try {
                        drawingView.loadFloorPlanJson(json);
                        String saved = json.optString("roomType", "");
                        for (int i = 0; i < roomTypeSpinner.getCount(); i++) {
                            if (saved.equals(roomTypeSpinner.getItemAtPosition(i).toString())) {
                                roomTypeSpinner.setSelection(i);
                                break;
                            }
                        }
                    } catch (Exception e) { Toast.makeText(this, R.string.load_error, Toast.LENGTH_SHORT).show(); }
                });
            } catch (Exception e) {
                runOnUiThread(() -> Toast.makeText(this, R.string.file_not_found, Toast.LENGTH_SHORT).show());
            }
        }).start();
    }
}