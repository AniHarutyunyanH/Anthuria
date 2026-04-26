package com.inania.Anthuria;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;

import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Saves and loads floor plans to/from the app's private internal storage.
 * Layout on disk:
 *   {filesDir}/floor_plans/{id}.json          — full plan JSON
 *   {filesDir}/floor_plans/{id}_preview.png   — preview bitmap
 * The id is the Unix timestamp (ms) at save time, so plans sort newest-first automatically.
 */
public final class FloorPlanStorage {

    private FloorPlanStorage() {}

    private static final String DIR_NAME = "floor_plans";

    public static class PlanEntry {
        public final String id;
        public final File   jsonFile;
        public final File   previewFile;
        public final long   savedAt;
        public final String roomType;

        PlanEntry(String id, File jsonFile, File previewFile, long savedAt, String roomType) {
            this.id          = id;
            this.jsonFile    = jsonFile;
            this.previewFile = previewFile;
            this.savedAt     = savedAt;
            this.roomType    = roomType;
        }
    }

    // -------------------------------------------------------------------------

    public static File plansDir(Context ctx) {
        File dir = new File(ctx.getFilesDir(), DIR_NAME);
        //noinspection ResultOfMethodCallIgnored
        dir.mkdirs();
        return dir;
    }

    /**
     * Persists {@code planJson} and {@code preview} to internal storage.
     *
     * @return the plan id (use it to load or delete the plan later)
     */
    public static String save(Context ctx, JSONObject planJson, Bitmap preview) throws Exception {
        String id  = String.valueOf(System.currentTimeMillis());
        File   dir = plansDir(ctx);

        // JSON
        try (FileOutputStream fos = new FileOutputStream(new File(dir, id + ".json"))) {
            fos.write(planJson.toString(2).getBytes("UTF-8"));
        }

        // Preview PNG
        try (FileOutputStream fos = new FileOutputStream(new File(dir, id + "_preview.png"))) {
            preview.compress(Bitmap.CompressFormat.PNG, 85, fos);
        }

        return id;
    }

    /** All saved plans, sorted newest-first. */
    public static List<PlanEntry> listAll(Context ctx) {
        File   dir   = plansDir(ctx);
        File[] files = dir.listFiles((d, name) -> name.endsWith(".json"));
        if (files == null) return Collections.emptyList();

        List<PlanEntry> list = new ArrayList<>();
        for (File f : files) {
            String id = f.getName().replace(".json", "");
            try {
                JSONObject json    = new JSONObject(readUtf8(f));
                long   savedAt    = json.optLong("savedAt", 0L);
                String roomType   = json.optString("roomType", "—");
                File   previewFile = new File(dir, id + "_preview.png");
                list.add(new PlanEntry(id, f, previewFile, savedAt, roomType));
            } catch (Exception ignored) { /* skip corrupt entries */ }
        }

        Collections.sort(list, (a, b) -> Long.compare(b.savedAt, a.savedAt));
        return list;
    }

    /** Loads the full JSON of a saved plan. */
    public static JSONObject load(Context ctx, String planId) throws Exception {
        File f = new File(plansDir(ctx), planId + ".json");
        return new JSONObject(readUtf8(f));
    }

    /** Loads the preview bitmap, or returns {@code null} if the file doesn't exist. */
    public static Bitmap loadPreview(Context ctx, String planId) {
        File f = new File(plansDir(ctx), planId + "_preview.png");
        return f.exists() ? BitmapFactory.decodeFile(f.getAbsolutePath()) : null;
    }

    /** Permanently deletes the plan and its preview. */
    public static void delete(Context ctx, String planId) {
        File dir = plansDir(ctx);
        //noinspection ResultOfMethodCallIgnored
        new File(dir, planId + ".json").delete();
        //noinspection ResultOfMethodCallIgnored
        new File(dir, planId + "_preview.png").delete();
    }

    // -------------------------------------------------------------------------

    private static String readUtf8(File f) throws Exception {
        try (FileInputStream fis = new FileInputStream(f)) {
            byte[] buf = new byte[(int) f.length()];
            //noinspection ResultOfMethodCallIgnored
            fis.read(buf);
            return new String(buf, "UTF-8");
        }
    }
}
