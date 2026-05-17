package com.inania.Anthuria;

import android.annotation.SuppressLint;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.text.InputType;
import android.util.Base64;
import android.util.Log;
import android.view.Gravity;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.webkit.ConsoleMessage;
import android.webkit.JavascriptInterface;
import android.webkit.JsPromptResult;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.widget.EditText;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;

import com.google.android.material.floatingactionbutton.FloatingActionButton;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

public class Room3DActivity extends BaseActivity
        implements Room3DBridge.Callback, LocaleManager.LocaleAware {

    private WebView webView;
    private FloatingActionButton fabAddFurniture;
    private ValueCallback<Uri[]> filePathCallback;
    private ActivityResultLauncher<String> imagePickerLauncher;
    private TexturePickerBottomSheet texturePicker;
    private String targetMeshId = "floor";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        imagePickerLauncher = registerForActivityResult(
                new ActivityResultContracts.GetContent(),
                uri -> {
                    if (filePathCallback != null) {
                        filePathCallback.onReceiveValue(
                                uri != null ? new Uri[]{uri} : new Uri[0]);
                        filePathCallback = null;
                    }
                });
        getWindow().setFlags(
                WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
                WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED);

        FrameLayout root = new FrameLayout(this);
        webView = new WebView(this);
        webView.setBackgroundColor(Color.TRANSPARENT);
        root.addView(webView, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        fabAddFurniture = new FloatingActionButton(this);
        fabAddFurniture.setImageResource(android.R.drawable.ic_input_add);
        FrameLayout.LayoutParams fabLp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        fabLp.gravity = Gravity.BOTTOM | Gravity.END;
        fabLp.setMargins(0, 0, 48, 48);
        root.addView(fabAddFurniture, fabLp);
        fabAddFurniture.setOnClickListener(v -> showFurnitureCatalogSheet());

        texturePicker = new TexturePickerBottomSheet(this, new TexturePickerBottomSheet.Listener() {
            @NonNull
            @Override
            public String getTargetMeshId() {
                return targetMeshId;
            }

            @Override
            public void onTextureSelected(@NonNull java.io.File webpFile, @NonNull String meshId) {
                String dataUrl = TextureManager.fileToDataUrlBase64(webpFile);
                if (dataUrl == null) {
                    Toast.makeText(Room3DActivity.this, R.string.texture_save_failed, Toast.LENGTH_SHORT).show();
                    return;
                }
                float repU = "floor".equals(meshId) ? 10f : 2f;
                float repV = "floor".equals(meshId) ? 10f : 2f;
                Room3DBridge.updateMeshTexture(webView, meshId, dataUrl, repU, repV);
            }
        });

        setContentView(root);

        String jsonPlan      = getIntent().getStringExtra("json_plan");
        String jsonFurniture = getIntent().getStringExtra("json_furniture");
        float  wallHeight    = getIntent().getFloatExtra("wall_height", 2.7f);
        String wallColor     = getIntent().getStringExtra("wall_color");
        Log.i("jsonplan",jsonPlan);
        Log.i("jsonFur",jsonFurniture);
        if (jsonPlan == null || jsonPlan.isEmpty()) {
            Toast.makeText(this, R.string.error_3d_prep, Toast.LENGTH_LONG).show();
            finish();
            return;
        }
        initWebView(jsonPlan, jsonFurniture, wallHeight, wallColor);
    }

    @SuppressLint({"SetJavaScriptEnabled", "AddJavascriptInterface"})
    private void initWebView(String jsonPlan, String jsonFurniture,
                             float wallHeight, String wallColor) {
        WebSettings s = webView.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setAllowFileAccess(true);
        WebView.setWebContentsDebuggingEnabled(true);

        webView.addJavascriptInterface(
                new Room3DBridge(this, () -> runOnUiThread(this::finish)),
                "Android3D");

        webView.setWebChromeClient(new WebChromeClient() {
            @Override public boolean onConsoleMessage(ConsoleMessage m) {
                Log.d("JS", m.message()); return true;
            }
            @Override
            public boolean onShowFileChooser(WebView view, ValueCallback<Uri[]> cb,
                                             FileChooserParams params) {
                if (filePathCallback != null) {
                    filePathCallback.onReceiveValue(new Uri[0]);
                }
                filePathCallback = cb;
                imagePickerLauncher.launch("image/*");
                return true;
            }
            @Override
            public boolean onJsPrompt(WebView view, String url, String message,
                                      String defaultValue, JsPromptResult result) {
                EditText input = new EditText(Room3DActivity.this);
                input.setText(defaultValue);
                input.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
                new AlertDialog.Builder(Room3DActivity.this)
                        .setTitle(message).setView(input)
                        .setPositiveButton("OK",     (d, w) -> result.confirm(input.getText().toString()))
                        .setNegativeButton("Cancel", (d, w) -> result.cancel())
                        .setOnCancelListener(d -> result.cancel())
                        .show();
                return true;
            }
        });

        String safeFurn  = (jsonFurniture != null && !jsonFurniture.isEmpty()) ? jsonFurniture : "[]";
        String safeColor = (wallColor    != null && !wallColor.isEmpty())    ? wallColor    : "#CCCCCC";

        // Base64-encode JSON so any characters (quotes, backslashes, etc.) are safe in JS
        String planB64 = Base64.encodeToString(jsonPlan.getBytes(), Base64.NO_WRAP);
        String furnB64 = Base64.encodeToString(safeFurn.getBytes(), Base64.NO_WRAP);

        boolean isDark = getSharedPreferences(AnthuriaApp.PREFS_NAME, MODE_PRIVATE)
                .getBoolean(AnthuriaApp.KEY_DARK_MODE, false);

        String bridgeJs = readAssetText(this, "room3d/three_js_bridge.js");
        String extJs = readAssetText(this, "room3d/scene_extensions.js");
        StringBuilder extBlock = new StringBuilder();
        if (bridgeJs != null) extBlock.append("<script>\n").append(bridgeJs).append("\n</script>\n");
        if (extJs != null) extBlock.append("<script>\n").append(extJs).append("\n</script>\n");

        String html = getHtmlContent()
                .replace("__EXT_JS_BLOCK__", extBlock.toString())
                .replace("__PLAN_B64__",   planB64)
                .replace("__FURN_B64__",   furnB64)
                .replace("__WALL_HEIGHT__", String.valueOf(wallHeight))
                .replace("__WALL_COLOR__",  safeColor)
                .replace("__DARK_MODE__",   isDark ? "true" : "false");

        webView.loadDataWithBaseURL("http://localhost", html, "text/html", "UTF-8", null);
    }

    @Override
    public void onSceneReady() {
        // WebView scene initialized — optional hook for analytics
    }

    @Override
    public void onOpenTexturePicker(String meshId) {
        targetMeshId = (meshId != null && !meshId.isEmpty()) ? meshId : "floor";
        runOnUiThread(() -> texturePicker.show());
    }

    @Override
    public void onFurnitureSelected(int index, String type) {
        runOnUiThread(() -> {
            webView.evaluateJavascript(
                    "javascript:if(typeof selectObject==='function'&&furnMeshes[" + index + "])"
                            + "selectObject(furnMeshes[" + index + "]);", null);
            Toast.makeText(this, getString(R.string.room3d_furniture_selected, type), Toast.LENGTH_SHORT).show();
        });
    }

    @Override
    public void onAppLocaleChanged(String languageCode) {
        if (fabAddFurniture != null) {
            fabAddFurniture.setContentDescription(getString(R.string.room3d_add_furniture));
        }
    }

    private void showFurnitureCatalogSheet() {
        String[] types = {"table", "cabinet", "sofa", "generic"};
        String[] labels = {
                getString(R.string.room3d_cat_table),
                getString(R.string.room3d_cat_cabinet),
                getString(R.string.room3d_cat_sofa),
                getString(R.string.room3d_cat_generic)
        };
        new AlertDialog.Builder(this)
                .setTitle(R.string.room3d_catalog_title)
                .setItems(labels, (d, which) ->
                        Room3DBridge.spawnCatalogItem(webView, types[which], 1.2f, 0.6f, 0.85f))
                .show();
    }

    private static String readAssetText(android.content.Context ctx, String path) {
        try {
            BufferedReader r = new BufferedReader(new InputStreamReader(
                    ctx.getAssets().open(path), StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = r.readLine()) != null) sb.append(line).append('\n');
            r.close();
            return sb.toString();
        } catch (Exception e) {
            return null;
        }
    }

    @Override protected void onDestroy() {
        super.onDestroy();
        if (webView != null) webView.destroy();
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  HTML + Three.js scene
    // ─────────────────────────────────────────────────────────────────────────
    private String getHtmlContent() {
        return "<!DOCTYPE html>\n" +
                "<html lang=\"ru\">\n" +
                "<head>\n" +
                "    <meta charset=\"UTF-8\">\n" +
                "    <meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no\">\n" +
                "    <style>\n" +
                "        :root {\n" +
                "            --primary: #4ABAED;\n" +
                "            --primary-hover: #3a9bd0;\n" +
                "            --bg-dark: rgba(20, 25, 30, 0.85);\n" +
                "            --panel-bg: rgba(25, 30, 35, 0.95);\n" +
                "            --text-light: #f5f5f5;\n" +
                "            --text-muted: #aaa;\n" +
                "            --border: rgba(255, 255, 255, 0.1);\n" +
                "        }\n" +
                "\n" +
                "        body {\n" +
                "            margin: 0; overflow: hidden; background: #111;\n" +
                "            font-family: 'Segoe UI', Roboto, Helvetica, Arial, sans-serif;\n" +
                "            touch-action: none; color: var(--text-light); user-select: none;\n" +
                "            -webkit-user-select: none;\n" +
                "        }\n" +
                "\n" +
                "        #loading {\n" +
                "            position: absolute; top: 0; left: 0; width: 100%; height: 100%;\n" +
                "            background: #111; z-index: 2000; display: flex; justify-content: center; align-items: center;\n" +
                "            font-size: 24px; font-weight: bold; transition: opacity 0.5s;\n" +
                "        }\n" +
                "\n" +
                "        /* --- ВЕРХНЯЯ ПАНЕЛЬ --- */\n" +
                "        #top-bar {\n" +
                "            position: absolute; top: 15px; left: 15px; z-index: 50; display: flex; gap: 8px;\n" +
                "        }\n" +
                "\n" +
                "        button.action-btn {\n" +
                "            padding: 8px 12px; background: var(--bg-dark); color: #fff;\n" +
                "            border: 1px solid var(--border); border-radius: 8px;\n" +
                "            font-weight: 600; font-size: 13px; cursor: pointer; backdrop-filter: blur(8px); transition: 0.2s;\n" +
                "        }\n" +
                "        button.action-btn:hover { background: rgba(255,255,255,0.15); }\n" +
                "        button.action-btn.active { background: var(--primary); border-color: var(--primary); }\n" +
                "\n" +
                "        /* --- ПАНЕЛЬ РЕДАКТИРОВАНИЯ --- */\n" +
                "        #edit-panel {\n" +
                "            position: absolute; top: -100%; left: 50%; transform: translateX(-50%);\n" +
                "            z-index: 100; background: var(--panel-bg); padding: 15px;\n" +
                "            border-radius: 0 0 16px 16px; backdrop-filter: blur(12px);\n" +
                "            box-shadow: 0 10px 30px rgba(0,0,0,0.5); border: 1px solid var(--border); border-top: none;\n" +
                "            width: 90%; max-width: 400px; max-height: 85vh; overflow-y: auto;\n" +
                "            transition: top 0.3s cubic-bezier(0.175, 0.885, 0.32, 1.275);\n" +
                "            display: flex; flex-direction: column; gap: 15px;\n" +
                "        }\n" +
                "        #edit-panel.active { top: 0; }\n" +
                "        \n" +
                "        /* Скроллбар для панели */\n" +
                "        #edit-panel::-webkit-scrollbar { width: 6px; }\n" +
                "        #edit-panel::-webkit-scrollbar-track { background: transparent; }\n" +
                "        #edit-panel::-webkit-scrollbar-thumb { background: rgba(255,255,255,0.2); border-radius: 3px; }\n" +
                "\n" +
                "        .panel-header { display: flex; justify-content: space-between; align-items: center; border-bottom: 1px solid var(--border); padding-bottom: 10px; }\n" +
                "        .panel-title { font-size: 16px; font-weight: bold; margin: 0; }\n" +
                "        .close-btn { background: rgba(255,255,255,0.1); border: none; color: #fff; width: 30px; height: 30px; border-radius: 50%; cursor: pointer; font-weight: bold; }\n" +
                "\n" +
                "        .tabs { display: flex; gap: 5px; background: rgba(0,0,0,0.3); padding: 5px; border-radius: 8px; }\n" +
                "        .tab-btn { flex: 1; padding: 8px; background: transparent; color: var(--text-muted); border: none; border-radius: 6px; cursor: pointer; font-weight: 600; font-size: 13px; transition: all 0.2s; }\n" +
                "        .tab-btn.active { background: var(--primary); color: #fff; }\n" +
                "\n" +
                "        /* Лейауты вкладок */\n" +
                "        .tab-content { display: none; }\n" +
                "        .grid-layout.active { display: grid; grid-template-columns: repeat(auto-fill, minmax(45px, 1fr)); gap: 10px; }\n" +
                "        .flex-layout.active { display: flex; flex-direction: column; gap: 10px; }\n" +
                "\n" +
                "        .color-swatch, .tex-swatch {\n" +
                "            width: 100%; aspect-ratio: 1; border-radius: 8px; cursor: pointer;\n" +
                "            border: 2px solid transparent; box-shadow: 0 2px 5px rgba(0,0,0,0.3); transition: transform 0.1s;\n" +
                "        }\n" +
                "        .color-swatch:active, .tex-swatch:active { transform: scale(0.9); }\n" +
                "        .add-btn { background: rgba(255,255,255,0.1); color: #fff; font-size: 24px; display: flex; justify-content: center; align-items: center; border: 1px dashed rgba(255,255,255,0.3); }\n" +
                "        \n" +
                "        /* Стили параметров (Edit Everything) */\n" +
                "        .prop-group { background: rgba(0,0,0,0.2); padding: 12px; border-radius: 8px; border: 1px solid rgba(255,255,255,0.05); }\n" +
                "        .prop-title { font-size: 12px; text-transform: uppercase; color: var(--primary); margin-bottom: 10px; display: block; font-weight: bold;}\n" +
                "        .prop-row { display: flex; justify-content: space-between; align-items: center; margin-bottom: 8px; }\n" +
                "        .prop-row label { font-size: 13px; color: #ddd; }\n" +
                "        \n" +
                "        .param-input { width: 65px; padding: 6px; background: rgba(255,255,255,0.1); border: 1px solid rgba(255,255,255,0.2); border-radius: 4px; color: #fff; font-family: monospace; text-align: right; transition: border 0.2s;}\n" +
                "        .param-input:focus { outline: none; border-color: var(--primary); background: rgba(0,0,0,0.5); }\n" +
                "        \n" +
                "        .param-select { width: 80px; padding: 5px; background: rgba(255,255,255,0.1); border: 1px solid rgba(255,255,255,0.2); border-radius: 4px; color: #fff; font-size: 12px; }\n" +
                "        .param-select option { background: #222; color: #fff; }\n" +
                "\n" +
                "        .btn-full { width: 100%; padding: 10px; border: none; border-radius: 8px; font-weight: bold; cursor: pointer; margin-top: 5px; transition: 0.2s;}\n" +
                "        .btn-full.danger { background: #e74c3c; color: #fff; }\n" +
                "        .btn-full.danger:hover { background: #c0392b; }\n" +
                "        .btn-full.primary { background: var(--primary); color: #fff; }\n" +
                "        .btn-full.primary:hover { background: var(--primary-hover); }\n" +
                "\n" +
                "        /* Элемент проема */\n" +
                "        .opening-item { background: rgba(0,0,0,0.2); padding: 10px; border-radius: 6px; margin-bottom: 8px; border-left: 3px solid var(--primary); }\n" +
                "        .opening-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 8px; }\n" +
                "        .opening-header span { font-size: 13px; font-weight: bold; }\n" +
                "        .btn-sm-danger { background: transparent; color: #e74c3c; border: 1px solid #e74c3c; padding: 2px 6px; border-radius: 4px; cursor: pointer; font-size: 11px; }\n" +
                "\n" +
                "        /* --- ИНСТРУМЕНТЫ ВНИЗУ --- */\n" +
                "        #tools-panel { position: absolute; bottom: 20px; left: 50%; transform: translateX(-50%); display: flex; gap: 10px; z-index: 50; background: var(--bg-dark); padding: 10px; border-radius: 12px; backdrop-filter: blur(12px); border: 1px solid var(--border); box-shadow: 0 10px 20px rgba(0,0,0,0.5); }\n" +
                "        button.tool-btn { padding: 10px 15px; background: rgba(255,255,255,0.1); color: #fff; border: none; border-radius: 8px; font-weight: 600; cursor: pointer; white-space: nowrap; transition: 0.2s; }\n" +
                "        button.tool-btn:hover { background: var(--primary); transform: translateY(-2px); }\n" +
                "\n" +
                "        /* --- УПРАВЛЕНИЕ ХОДЬБОЙ --- */\n" +
                "        #d-pad { position: absolute; bottom: 80px; right: 20px; z-index: 60; display: none; grid-template-columns: 50px 50px 50px; grid-template-rows: 50px 50px 50px; gap: 5px; }\n" +
                "        .d-btn { background: rgba(255,255,255,0.15); border-radius: 12px; border: 1px solid rgba(255,255,255,0.2); display: flex; justify-content: center; align-items: center; font-size: 20px; cursor: pointer; backdrop-filter: blur(4px); }\n" +
                "        .d-btn:active { background: rgba(255,255,255,0.4); }\n" +
                "        .d-up { grid-column: 2; grid-row: 1; } .d-down { grid-column: 2; grid-row: 3; } .d-left { grid-column: 1; grid-row: 2; } .d-right { grid-column: 3; grid-row: 2; }\n" +
                "\n" +
                "        /* --- МЕТКИ РАССТОЯНИЙ В 3D --- */\n" +
                "        #label-container { position: absolute; top: 0; left: 0; width: 100%; height: 100%; pointer-events: none; z-index: 40; overflow: hidden; }\n" +
                "        .dist-label { position: absolute; transform: translate(-50%, -50%); background: var(--primary); padding: 3px 6px; border-radius: 4px; font-size: 11px; font-weight: bold; color: #fff; box-shadow: 0 2px 5px rgba(0,0,0,0.5); }\n" +
                "        .dim-wall-label { position: absolute; transform: translate(-50%, -50%); background: rgba(20,20,20,0.75); color: #fff; padding: 3px 9px; border-radius: 6px; font-size: 12px; font-weight: bold; border: 1px solid rgba(255,255,255,0.25); backdrop-filter: blur(4px); pointer-events: auto; cursor: pointer; white-space: nowrap; transition: background 0.15s; }\n" +
                "        .dim-wall-label:hover { background: var(--primary); border-color: var(--primary); }\n" +
                "    </style>\n" +
                "\n" +
                "    <script src=\"https://cdnjs.cloudflare.com/ajax/libs/three.js/r128/three.min.js\"></script>\n" +
                "    <script src=\"https://cdn.jsdelivr.net/npm/three@0.128.0/examples/js/controls/OrbitControls.js\"></script>\n" +
                "    <script src=\"https://cdn.jsdelivr.net/npm/three@0.128.0/examples/js/loaders/GLTFLoader.js\"></script>\n" +
                "    __EXT_JS_BLOCK__\n" +
                "</head>\n" +
                "<body>\n" +
                "\n" +
                "    <div id=\"loading\">Загрузка ядра...</div>\n" +
                "\n" +
                "    <div id=\"top-bar\">\n" +
                "        <button class=\"action-btn\" onclick=\"resetCamera()\">⌂ Центр</button>\n" +
                "        <button class=\"action-btn\" id=\"btn-walk\" onclick=\"toggleWalkMode()\">\uD83D\uDEB6 Ходьба</button>\n" +
                "    </div>\n" +
                "\n" +
                "    <!-- Панель Редактора \"Edit Everything\" -->\n" +
                "    <div id=\"edit-panel\">\n" +
                "        <div class=\"panel-header\">\n" +
                "            <h3 class=\"panel-title\" id=\"edit-title\">Объект</h3>\n" +
                "            <button class=\"close-btn\" onclick=\"deselect()\">✕</button>\n" +
                "        </div>\n" +
                "        \n" +
                "        <div class=\"tabs\">\n" +
                "            <button class=\"tab-btn active\" onclick=\"switchTab('colors')\">Цвета</button>\n" +
                "            <button class=\"tab-btn\" onclick=\"switchTab('textures')\">Текстуры</button>\n" +
                "            <button class=\"tab-btn\" onclick=\"switchTab('params')\">Параметры</button>\n" +
                "        </div>\n" +
                "\n" +
                "        <div id=\"tab-colors\" class=\"tab-content grid-layout active\">\n" +
                "            <div class=\"color-swatch add-btn\" onclick=\"document.getElementById('custom-color').click()\">+</div>\n" +
                "            <input type=\"color\" id=\"custom-color\" style=\"display:none;\" onchange=\"applyCustomColor(this.value)\">\n" +
                "        </div>\n" +
                "\n" +
                "        <div id=\"tab-textures\" class=\"tab-content grid-layout\">\n" +
                "            <div class=\"tex-swatch add-btn\" onclick=\"openAndroidTexturePicker()\">+</div>\n" +
                "            <input type=\"file\" id=\"custom-tex\" accept=\"image/*\" style=\"display:none;\" onchange=\"applyCustomTexture(event)\">\n" +
                "        </div>\n" +
                "\n" +
                "        <div id=\"tab-params\" class=\"tab-content flex-layout\"></div>\n" +
                "    </div>\n" +
                "\n" +
                "    <div id=\"tools-panel\">\n" +
                "        <button class=\"tool-btn\" onclick=\"addFurniture('table')\">+ Стол</button>\n" +
                "        <button class=\"tool-btn\" onclick=\"addFurniture('cabinet')\">+ Шкаф</button>\n" +
                "        <button class=\"tool-btn\" onclick=\"addFurniture('generic')\">+ Куб</button>\n" +
                "    </div>\n" +
                "\n" +
                "    <div id=\"d-pad\">\n" +
                "        <div class=\"d-btn d-up\" onpointerdown=\"keys.w=true\" onpointerup=\"keys.w=false\" onpointerleave=\"keys.w=false\" ontouchstart=\"keys.w=true\" ontouchend=\"keys.w=false\">▲</div>\n" +
                "        <div class=\"d-btn d-left\" onpointerdown=\"keys.a=true\" onpointerup=\"keys.a=false\" onpointerleave=\"keys.a=false\" ontouchstart=\"keys.a=true\" ontouchend=\"keys.a=false\">◀</div>\n" +
                "        <div class=\"d-btn d-right\" onpointerdown=\"keys.d=true\" onpointerup=\"keys.d=false\" onpointerleave=\"keys.d=false\" ontouchstart=\"keys.d=true\" ontouchend=\"keys.d=false\">▶</div>\n" +
                "        <div class=\"d-btn d-down\" onpointerdown=\"keys.s=true\" onpointerup=\"keys.s=false\" onpointerleave=\"keys.s=false\" ontouchstart=\"keys.s=true\" ontouchend=\"keys.s=false\">▼</div>\n" +
                "    </div>\n" +
                "\n" +
                "    <div id=\"label-container\"></div>\n" +
                "\n" +
                "<script>\n" +
                "/**\n" +
                " * 3D Редактор Интерьера PRO (Full Editing Version)\n" +
                " */\n" +
                "\n" +
                "let scene, camera, renderer, controls;\n" +
                "const raycaster = new THREE.Raycaster();\n" +
                "const mouse = new THREE.Vector2();\n" +
                "const clock = new THREE.Clock();\n" +
                "\n" +
                "const group = new THREE.Group(); \n" +
                "const wallMeshes = []; \n" +
                "const furnMeshes = []; \n" +
                "const wallColliders = []; // OBB всей стены — для мебели\n" +
                "const walkColliders = []; // OBB с учётом дверей — для ходьбы\n" +
                "let floorMesh;\n" +
                "\n" +
                "const SCALE = 10;\n" +
                "const defaultColors = ['#f5f5f5', '#e0e0e0', '#2a2a2a', '#4ABAED', '#e74c3c', '#2ecc71', '#f1c40f', '#9b59b6', '#8e44ad', '#34495e'];\n" +
                "\n" +
                "// Injected from Android — base64-encoded to avoid any JS/HTML escaping issues\n" +
                "const isDarkMode = __DARK_MODE__;\n" +
                "const _plan = JSON.parse(atob('__PLAN_B64__'));\n" +
                "const _furn = JSON.parse(atob('__FURN_B64__'));\n" +
                "let pixelsPerMeter = _plan.pixelsPerMeter || 100;\n" +
                "\n" +
                "// Глобальное состояние сцены\n" +
                "let appData = {\n" +
                "    floor: { color: '#888888', texture: null },\n" +
                "    walls: (_plan.walls || []).map(function(w) {\n" +
                "        return {\n" +
                "            x1: w.x1, y1: w.y1, x2: w.x2, y2: w.y2,\n" +
                "            thicknessMeters: w.thicknessMeters || (w.thicknessPx || 20) / pixelsPerMeter,\n" +
                "            color: w.wallColorHex || w.color || '__WALL_COLOR__',\n" +
                "            openings: (w.openings || []).map(function(o) {\n" +
                "                return { type: o.type,\n" +
                "                         pos: o.positionFactor != null ? o.positionFactor : (o.pos != null ? o.pos : 0.5),\n" +
                "                         widthMeters: o.widthMeters || (o.widthPx || 80) / pixelsPerMeter };\n" +
                "            })\n" +
                "        };\n" +
                "    }),\n" +
                "    furniture: (Array.isArray(_furn) ? _furn : []).map(function(f) {\n" +
                "        return {\n" +
                "            type: f.type || f.name || 'furniture',\n" +
                "            x: f.x, y: f.y,\n" +
                "            width: f.width  || 1.0,\n" +
                "            depth: f.depth  || 0.5,\n" +
                "            height: f.height || 0.8,\n" +
                "            rotation: f.rotation || 0,\n" +
                "            color: f.color || '#4ABAED',\n" +
                "            elevation: f.elevation || 0\n" +
                "        };\n" +
                "    }),\n" +
                "    height: __WALL_HEIGHT__\n" +
                "};\n" +
                "\n" +
                "let selectedTarget = null; \n" +
                "let selectionBox = null;\n" +
                "let distanceVisuals = { lines: [], labels: [], data: {} };\n" +
                "const dimLabels = []; // постоянные метки размеров стен\n" +
                "let textureCache = {};\n" +
                "\n" +
                "// Перетаскивание\n" +
                "let isDragging = false;\n" +
                "let dragTarget = null;\n" +
                "const dragPlane = new THREE.Plane(new THREE.Vector3(0, 1, 0), 0);\n" +
                "let dragOffset = new THREE.Vector3();\n" +
                "\n" +
                "// Режим ходьбы\n" +
                "let isWalkMode = false;\n" +
                "let isDraggingHead = false, prevTouch = { x: 0, y: 0 };\n" +
                "let yaw = 0, pitch = 0;\n" +
                "const keys = { w: false, a: false, s: false, d: false };\n" +
                "const playerRadius = 0.4 * SCALE;\n" +
                "\n" +
                "// --- ИНИЦИАЛИЗАЦИЯ ---\n" +
                "window.onload = function() {\n" +
                "    initEngine();\n" +
                "    buildScene();\n" +
                "    initUI();\n" +
                "    document.getElementById('loading').style.opacity = 0;\n" +
                "    setTimeout(() => document.getElementById('loading').style.display = 'none', 500);\n" +
                "};\n" +
                "\n" +
                "function initEngine() {\n" +
                "    scene = new THREE.Scene();\n" +
                "    scene.background = new THREE.Color(isDarkMode ? 0x111111 : 0xffffff);\n" +
                "    camera = new THREE.PerspectiveCamera(60, window.innerWidth / window.innerHeight, 0.1, 5000);\n" +
                "    \n" +
                "    renderer = new THREE.WebGLRenderer({ antialias: true, preserveDrawingBuffer: true });\n" +
                "    renderer.setPixelRatio(window.devicePixelRatio);\n" +
                "    renderer.setSize(window.innerWidth, window.innerHeight);\n" +
                "    renderer.shadowMap.enabled = true;\n" +
                "    renderer.shadowMap.type = THREE.PCFSoftShadowMap;\n" +
                "    document.body.appendChild(renderer.domElement);\n" +
                "\n" +
                "    if (window.Room3D) Room3D.setupLighting(scene, renderer);\n" +
                "    else {\n" +
                "        scene.add(new THREE.AmbientLight(0xffffff, 0.42));\n" +
                "        const dirLight = new THREE.DirectionalLight(0xfff5e8, 0.72);\n" +
                "        dirLight.position.set(50, 100, 50); dirLight.castShadow = true;\n" +
                "        dirLight.shadow.mapSize.width = 2048; dirLight.shadow.mapSize.height = 2048;\n" +
                "        scene.add(dirLight);\n" +
                "    }\n" +
                "\n" +
                "    controls = new THREE.OrbitControls(camera, renderer.domElement);\n" +
                "    controls.enableDamping = true;\n" +
                "    controls.dampingFactor = 0.05;\n" +
                "    controls.maxPolarAngle = Math.PI / 2 - 0.05;\n" +
                "\n" +
                "    scene.add(group);\n" +
                "\n" +
                "    selectionBox = new THREE.BoxHelper(new THREE.Mesh(new THREE.BoxGeometry(1,1,1)), 0xffaa00);\n" +
                "    selectionBox.visible = false;\n" +
                "    scene.add(selectionBox);\n" +
                "\n" +
                "    renderer.domElement.addEventListener('pointerdown', onPointerDown);\n" +
                "    renderer.domElement.addEventListener('pointermove', onPointerMove);\n" +
                "    renderer.domElement.addEventListener('pointerup', onPointerUp);\n" +
                "    window.addEventListener('resize', onWindowResize);\n" +
                "\n" +
                "    setupFPS();\n" +
                "    animate();\n" +
                "}\n" +
                "\n" +
                "function clearScene() {\n" +
                "    group.traverse((child) => {\n" +
                "        if (child.isMesh) {\n" +
                "            if (child.geometry) child.geometry.dispose();\n" +
                "            if (child.material) {\n" +
                "                if (Array.isArray(child.material)) child.material.forEach(m => m.dispose());\n" +
                "                else child.material.dispose();\n" +
                "            }\n" +
                "        }\n" +
                "    });\n" +
                "    while(group.children.length > 0) group.remove(group.children[0]);\n" +
                "    wallMeshes.length = 0; wallColliders.length = 0; walkColliders.length = 0; furnMeshes.length = 0;\n" +
                "    if (window.Room3D) Room3D.clearWalkFurnitureAABBs();\n" +
                "    dimLabels.forEach(d => d.element.remove()); dimLabels.length = 0;\n" +
                "    clearDistanceVisuals();\n" +
                "    if(selectionBox) selectionBox.visible = false;\n" +
                "}\n" +
                "\n" +
                "function cleanupUnusedTextures() {\n" +
                "    const usedUrls = new Set();\n" +
                "    if (appData.floor.texture) usedUrls.add(appData.floor.texture);\n" +
                "    appData.walls.forEach(w => { if (w.texture) usedUrls.add(w.texture); });\n" +
                "    appData.furniture.forEach(f => { if (f.texture) usedUrls.add(f.texture); });\n" +
                "\n" +
                "    for (const url in textureCache) {\n" +
                "        if (url.startsWith('blob:') && !usedUrls.has(url)) {\n" +
                "            textureCache[url].dispose();\n" +
                "            URL.revokeObjectURL(url);\n" +
                "            delete textureCache[url];\n" +
                "            document.querySelectorAll('.tex-swatch').forEach(sw => {\n" +
                "                if (sw.style.backgroundImage.includes(url)) sw.remove();\n" +
                "            });\n" +
                "        }\n" +
                "    }\n" +
                "}\n" +
                "\n" +
                "// --- ПОСТРОЕНИЕ ГЕОМЕТРИИ ---\n" +
                "function buildScene() {\n" +
                "    clearScene();\n" +
                "\n" +
                "    // 1. ВНЕШНИЙ ФОН (земля за пределами комнаты, совпадает с фоном)\n" +
                "    const groundGeo = new THREE.PlaneGeometry(1000, 1000);\n" +
                "    const groundMat = new THREE.MeshStandardMaterial({ color: isDarkMode ? 0x111111 : 0xffffff });\n" +
                "    const groundMesh = new THREE.Mesh(groundGeo, groundMat);\n" +
                "    groundMesh.rotation.x = -Math.PI / 2;\n" +
                "    groundMesh.position.y = -0.05; \n" +
                "    group.add(groundMesh);\n" +
                "\n" +
                "    // 2. ВНУТРЕННИЙ ПОЛ (вычисляем его границы строго по стенам)\n" +
                "    let minX = Infinity, maxX = -Infinity, minZ = Infinity, maxZ = -Infinity;\n" +
                "    appData.walls.forEach(w => {\n" +
                "        minX = Math.min(minX, w.x1, w.x2);\n" +
                "        maxX = Math.max(maxX, w.x1, w.x2);\n" +
                "        minZ = Math.min(minZ, w.y1, w.y2);\n" +
                "        maxZ = Math.max(maxZ, w.y1, w.y2);\n" +
                "    });\n" +
                "\n" +
                "    const roomWidth = (maxX - minX) * SCALE;\n" +
                "    const roomDepth = (maxZ - minZ) * SCALE;\n" +
                "    const centerX = ((maxX + minX) / 2) * SCALE;\n" +
                "    const centerZ = ((maxZ + minZ) / 2) * SCALE;\n" +
                "\n" +
                "    const floorGeo = new THREE.PlaneGeometry(roomWidth, roomDepth);\n" +
                "    const floorMat = new THREE.MeshStandardMaterial({ color: appData.floor.color });\n" +
                "    \n" +
                "    // Накатываем текстуру, если она есть\n" +
                "    if(appData.floor.texture && textureCache[appData.floor.texture]) {\n" +
                "        floorMat.map = textureCache[appData.floor.texture];\n" +
                "        floorMat.color.set(0xffffff);\n" +
                "    }\n" +
                "    \n" +
                "    // Создаем сам кликабельный пол\n" +
                "    floorMesh = new THREE.Mesh(floorGeo, floorMat);\n" +
                "    floorMesh.rotation.x = -Math.PI / 2;\n" +
                "    floorMesh.position.set(centerX, 0, centerZ); // Ставим строго по центру стен\n" +
                "    floorMesh.receiveShadow = true;\n" +
                "    floorMesh.userData = { type: 'floor', name: 'Пол комнаты' };\n" +
                "    group.add(floorMesh);\n" +
                "    if (window.Room3D) Room3D.buildCeilingMesh({ width: roomWidth, depth: roomDepth, centerX, centerZ, height: appData.height });\n" +
                "    if (window.Room3D) Room3D.clearWalkFurnitureAABBs();\n" +
                "\n" +
                "    // 3. Стены и мебель\n" +
                "    appData.walls.forEach((w, i) => buildWall(w, i));\n" +
                "    appData.furniture.forEach((f, i) => buildFurnitureItem(f, i));\n" +
                "\n" +
                "    // Восстанавливаем выделение, если оно было\n" +
                "    if (selectedTarget) {\n" +
                "        const type = selectedTarget.userData.type;\n" +
                "        const idx = selectedTarget.userData.index;\n" +
                "        let newTarget = null;\n" +
                "        if(type === 'furniture') newTarget = furnMeshes.find(m => m.userData.index === idx);\n" +
                "        if(type === 'wall') newTarget = wallMeshes.find(m => m.userData.index === idx);\n" +
                "        if(newTarget) selectObject(newTarget);\n" +
                "    } else {\n" +
                "        resetCamera();\n" +
                "    }\n" +
                "    buildDimLabels();\n" +
                "}\n" +
                "// Прокачанный билдер стен с вырезами (окна, двери)\n" +
                "function buildWall(w, idx) {\n" +
                "    const dx = (w.x2 - w.x1) * SCALE, dz = (w.y2 - w.y1) * SCALE;\n" +
                "    const wallLen = Math.sqrt(dx*dx + dz*dz);\n" +
                "    if(wallLen < 0.1) return;\n" +
                "    \n" +
                "    const angle = -Math.atan2(dz, dx);\n" +
                "    const thick = Math.max(0.05, w.thicknessMeters || 0.2) * SCALE;\n" +
                "    \n" +
                "    const wallGrp = new THREE.Group();\n" +
                "    wallGrp.userData = { type: 'wall', index: idx, data: w, name: `Стена ${idx+1}` };\n" +
                "    \n" +
                "    const wallMat = new THREE.MeshStandardMaterial({ color: w.color || 0xe0e0e0, roughness: 0.9 });\n" +
                "    if(w.texture && textureCache[w.texture]) {\n" +
                "        wallMat.map = textureCache[w.texture];\n" +
                "        wallMat.color.set(0xffffff);\n" +
                "    }\n" +
                "\n" +
                "    // OBB всей стены для мебели (без дыр — мебель не скользит сквозь проёмы)\n" +
                "    wallColliders.push(createOBB(w.x1 * SCALE + dx / 2, w.y1 * SCALE + dz / 2, wallLen, thick, angle));\n" +
                "    // Посегментные OBB для ходьбы — пропускаем дверные проёмы\n" +
                "    (function() {\n" +
                "        var wcur = 0;\n" +
                "        var wsorted = (w.openings || []).slice().sort(function(a,b){ return a.pos - b.pos; });\n" +
                "        function addWalk(s, e) {\n" +
                "            var l = (e - s) * wallLen; if (l < 0.01) return;\n" +
                "            var m = (s + e) / 2;\n" +
                "            walkColliders.push(createOBB(w.x1*SCALE + dx*m, w.y1*SCALE + dz*m, l, thick, angle));\n" +
                "        }\n" +
                "        wsorted.forEach(function(op) {\n" +
                "            var hw = (op.widthMeters * SCALE) / wallLen / 2;\n" +
                "            var s = Math.max(0, op.pos - hw), e = Math.min(1, op.pos + hw);\n" +
                "            if (s > wcur) addWalk(wcur, s);\n" +
                "            if (op.type !== 'DOOR') addWalk(s, e); // окна непроходимы\n" +
                "            wcur = e;\n" +
                "        });\n" +
                "        if (wcur < 1) addWalk(wcur, 1);\n" +
                "    })();\n" +
                "\n" +
                "    // Вспомогательная функция для создания сегмента стены\n" +
                "    function addBlock(startM, endM, yBottomM, yTopM) {\n" +
                "        const len = (endM - startM) * wallLen;\n" +
                "        if(len < 0.01) return;\n" +
                "        \n" +
                "        // Смещение от начала стены\n" +
                "        const centerPosM = startM + (endM - startM) / 2;\n" +
                "        const cx = w.x1 * SCALE + dx * centerPosM;\n" +
                "        const cz = w.y1 * SCALE + dz * centerPosM;\n" +
                "        \n" +
                "        const cy = (yTopM + yBottomM) * SCALE / 2;\n" +
                "        const h = (yTopM - yBottomM) * SCALE;\n" +
                "        \n" +
                "        const mesh = new THREE.Mesh(new THREE.BoxGeometry(len, h, thick), wallMat);\n" +
                "        mesh.position.set(cx, cy, cz);\n" +
                "        mesh.rotation.y = angle;\n" +
                "        mesh.castShadow = true; mesh.receiveShadow = true;\n" +
                "        mesh.userData = wallGrp.userData; \n" +
                "        \n" +
                "        wallGrp.add(mesh);\n" +
                "    }\n" +
                "\n" +
                "    const H = appData.height;\n" +
                "    const SILL_H = 0.9; // Подоконник\n" +
                "    const LINTEL_H = H * 0.78; // Верх дверного проема\n" +
                "    \n" +
                "    let cursor = 0;\n" +
                "    // Сортируем проемы слева направо\n" +
                "    const ops = (w.openings || []).slice().sort((a,b) => a.pos - b.pos);\n" +
                "    \n" +
                "    ops.forEach((op) => {\n" +
                "        const opWidthNorm = (op.widthMeters * SCALE) / wallLen;\n" +
                "        const start = Math.max(0, op.pos - opWidthNorm / 2);\n" +
                "        const end = Math.min(1, op.pos + opWidthNorm / 2);\n" +
                "        \n" +
                "        // Сплошная стена до проема\n" +
                "        if(start > cursor) addBlock(cursor, start, 0, H);\n" +
                "        \n" +
                "        // Создаем сам проем\n" +
                "        if(op.type === 'WINDOW') {\n" +
                "            addBlock(start, end, 0, Math.min(SILL_H, H)); // Под окном\n" +
                "            if(LINTEL_H < H) addBlock(start, end, LINTEL_H, H); // Над окном\n" +
                "        } else if(op.type === 'DOOR') {\n" +
                "            if(LINTEL_H < H) addBlock(start, end, LINTEL_H, H); // Над дверью\n" +
                "        }\n" +
                "        \n" +
                "        cursor = end;\n" +
                "    });\n" +
                "    \n" +
                "    // Остаток стены после последнего проема\n" +
                "    if(cursor < 1) addBlock(cursor, 1, 0, H);\n" +
                "    \n" +
                "    group.add(wallGrp);\n" +
                "    wallMeshes.push(wallGrp);\n" +
                "}\n" +
                "\n" +
                "function buildFurnitureItem(f, idx) {\n" +
                "    const w = f.width * SCALE, h = f.height * SCALE, d = f.depth * SCALE;\n" +
                "    const x = f.x * SCALE, z = f.y * SCALE;\n" +
                "    const rot = f.rotation * Math.PI / 180;\n" +
                "\n" +
                "    const furnGrp = new THREE.Group();\n" +
                "    furnGrp.position.set(x, 0, z);\n" +
                "    furnGrp.rotation.y = rot;\n" +
                "    \n" +
                "    let name = f.type === 'table' ? 'Стол' : (f.type === 'cabinet' ? 'Шкаф' : 'Кубик');\n" +
                "    furnGrp.userData = { type: 'furniture', index: idx, data: f, name: name };\n" +
                "\n" +
                "    const mat = new THREE.MeshStandardMaterial({ color: f.color || 0x4ABAED, roughness: 0.4 });\n" +
                "    if(f.texture && textureCache[f.texture]) {\n" +
                "        mat.map = textureCache[f.texture];\n" +
                "        mat.color.set(0xffffff);\n" +
                "    }\n" +
                "    \n" +
                "    if(f.type === 'table') {\n" +
                "        const top = new THREE.Mesh(new THREE.BoxGeometry(w, 0.5, d), mat);\n" +
                "        top.position.y = h - 0.25; top.castShadow = true; top.receiveShadow = true; furnGrp.add(top);\n" +
                "        [[-1,-1],[1,-1],[-1,1],[1,1]].forEach(pos => {\n" +
                "            const leg = new THREE.Mesh(new THREE.CylinderGeometry(0.2, 0.2, h-0.5), mat);\n" +
                "            leg.position.set(pos[0]*(w/2 - 0.4), (h-0.5)/2, pos[1]*(d/2 - 0.4));\n" +
                "            leg.castShadow = true; leg.receiveShadow = true; furnGrp.add(leg);\n" +
                "        });\n" +
                "        const hitBox = new THREE.Mesh(new THREE.BoxGeometry(w, h, d), new THREE.MeshBasicMaterial({visible:false}));\n" +
                "        hitBox.position.y = h/2; furnGrp.add(hitBox);\n" +
                "    } else {\n" +
                "        const mesh = new THREE.Mesh(new THREE.BoxGeometry(w, h, d), mat);\n" +
                "        mesh.position.y = h/2; mesh.castShadow = true; mesh.receiveShadow = true;\n" +
                "        furnGrp.add(mesh);\n" +
                "    }\n" +
                "\n" +
                "    furnGrp.children.forEach(c => c.userData = furnGrp.userData);\n" +
                "    group.add(furnGrp);\n" +
                "    furnMeshes.push(furnGrp);\n" +
                "    if (window.Room3D) {\n" +
                "        Room3D.MaterialManager.registerFurniture(furnGrp, 'furn_' + idx);\n" +
                "        Room3D.registerWalkFurnitureAABB(furnGrp, f);\n" +
                "    }\n" +
                "}\n" +
                "\n" +
                "// --- МЕТКИ РАЗМЕРОВ СТЕН (всегда видны) ---\n" +
                "function buildDimLabels() {\n" +
                "    dimLabels.forEach(d => d.element.remove());\n" +
                "    dimLabels.length = 0;\n" +
                "    const container = document.getElementById('label-container');\n" +
                "    appData.walls.forEach(function(w, i) {\n" +
                "        const dx = (w.x2 - w.x1) * SCALE, dz = (w.y2 - w.y1) * SCALE;\n" +
                "        const wallLen = Math.sqrt(dx*dx + dz*dz);\n" +
                "        if (wallLen < 0.1) return;\n" +
                "        const lenM = wallLen / SCALE;\n" +
                "        // Перпендикуляр наружу для отступа метки\n" +
                "        const nx = -dz / wallLen * 1.2 * SCALE, nz = dx / wallLen * 1.2 * SCALE;\n" +
                "        const mx = ((w.x1 + w.x2) / 2) * SCALE + nx;\n" +
                "        const my = appData.height * SCALE * 0.55;\n" +
                "        const mz = ((w.y1 + w.y2) / 2) * SCALE + nz;\n" +
                "        const el = document.createElement('div');\n" +
                "        el.className = 'dim-wall-label';\n" +
                "        el.textContent = lenM.toFixed(2) + ' м';\n" +
                "        el.addEventListener('click', function(e) { e.stopPropagation(); editWallLength(i); });\n" +
                "        container.appendChild(el);\n" +
                "        dimLabels.push({ element: el, pos: new THREE.Vector3(mx, my, mz) });\n" +
                "    });\n" +
                "}\n" +
                "\n" +
                "function editWallLength(wallIndex) {\n" +
                "    const w = appData.walls[wallIndex];\n" +
                "    const dx = w.x2 - w.x1, dz = w.y2 - w.y1;\n" +
                "    const curLen = Math.sqrt(dx*dx + dz*dz);\n" +
                "    const input = prompt('Длина стены ' + (wallIndex+1) + ' (м):', curLen.toFixed(2));\n" +
                "    if (input === null) return;\n" +
                "    const newLen = parseFloat(input);\n" +
                "    if (isNaN(newLen) || newLen < 0.1) return;\n" +
                "    const nx = dx / curLen, nz = dz / curLen;\n" +
                "    w.x2 = w.x1 + nx * newLen;\n" +
                "    w.y2 = w.y1 + nz * newLen;\n" +
                "    buildScene();\n" +
                "}\n" +
                "\n" +
                "// --- УПРАВЛЕНИЕ МЫШЬЮ / ТАЧАМИ ---\n" +
                "function onPointerDown(event) {\n" +
                "    if(event.button !== 0 || event.target.closest('#edit-panel') || event.target.closest('#tools-panel') || event.target.closest('#top-bar') || event.target.closest('#d-pad')) return;\n" +
                "\n" +
                "    mouse.x = (event.clientX / window.innerWidth) * 2 - 1;\n" +
                "    mouse.y = -(event.clientY / window.innerHeight) * 2 + 1;\n" +
                "\n" +
                "    raycaster.setFromCamera(mouse, camera);\n" +
                "    const allObjects = [floorMesh, ...getMeshesFromGroup(wallMeshes), ...getMeshesFromGroup(furnMeshes)];\n" +
                "    const intersects = raycaster.intersectObjects(allObjects, false);\n" +
                "\n" +
                "    if (intersects.length > 0) {\n" +
                "        let hitObj = intersects[0].object;\n" +
                "        let rootObj = (hitObj.parent && hitObj.parent.userData && hitObj.parent.userData.type) ? hitObj.parent : hitObj;\n" +
                "\n" +
                "        // Если кликнули на новый объект\n" +
                "        if(selectedTarget !== rootObj) selectObject(rootObj);\n" +
                "        if (rootObj.userData.type === 'furniture' && window.Room3D) Room3D.onFurnitureRaycastHit(rootObj);\n" +
                "\n" +
                "        // Перетаскивание (только мебель, только в режиме обзора)\n" +
                "        if (rootObj.userData.type === 'furniture' && !isWalkMode) {\n" +
                "            isDragging = true;\n" +
                "            dragTarget = rootObj;\n" +
                "            if(controls) controls.enabled = false;\n" +
                "            \n" +
                "            dragPlane.setFromNormalAndCoplanarPoint(new THREE.Vector3(0, 1, 0), dragTarget.position);\n" +
                "            raycaster.ray.intersectPlane(dragPlane, dragOffset);\n" +
                "            if(dragOffset) dragOffset.sub(dragTarget.position);\n" +
                "        }\n" +
                "    } else {\n" +
                "        deselect();\n" +
                "    }\n" +
                "}\n" +
                "\n" +
                "function onPointerMove(event) {\n" +
                "    if (!isDragging || !dragTarget || isWalkMode) return;\n" +
                "\n" +
                "    mouse.x = (event.clientX / window.innerWidth) * 2 - 1;\n" +
                "    mouse.y = -(event.clientY / window.innerHeight) * 2 + 1;\n" +
                "\n" +
                "    raycaster.setFromCamera(mouse, camera);\n" +
                "    const intersectPoint = new THREE.Vector3();\n" +
                "    \n" +
                "    if (raycaster.ray.intersectPlane(dragPlane, intersectPoint)) {\n" +
                "        const newPos = intersectPoint.sub(dragOffset);\n" +
                "        const oldPos = dragTarget.position.clone();\n" +
                "        \n" +
                "        dragTarget.position.x = newPos.x;\n" +
                "        dragTarget.position.z = newPos.z;\n" +
                "        dragTarget.updateMatrixWorld();\n" +
                "\n" +
                "        const fData = dragTarget.userData.data;\n" +
                "        const fOBB = createOBB(dragTarget.position.x, dragTarget.position.z, fData.width * SCALE, fData.depth * SCALE, fData.rotation * Math.PI / 180);\n" +
                "\n" +
                "        let collision = false;\n" +
                "        for(let wOBB of wallColliders) {\n" +
                "            if (checkOBBIntersection(fOBB, wOBB)) { collision = true; break; }\n" +
                "        }\n" +
                "\n" +
                "        if(collision) {\n" +
                "            dragTarget.position.copy(oldPos); \n" +
                "        } else {\n" +
                "            // Сохраняем в Data\n" +
                "            dragTarget.userData.data.x = dragTarget.position.x / SCALE;\n" +
                "            dragTarget.userData.data.y = dragTarget.position.z / SCALE;\n" +
                "            \n" +
                "            // Динамическое обновление UI без потери фокуса\n" +
                "            updateDistanceVisuals();\n" +
                "            updateFurnPosInputsUI();\n" +
                "            \n" +
                "            if(selectionBox.visible) selectionBox.update();\n" +
                "        }\n" +
                "    }\n" +
                "}\n" +
                "\n" +
                "function onPointerUp() {\n" +
                "    isDragging = false; dragTarget = null;\n" +
                "    if(!isWalkMode && controls) controls.enabled = true;\n" +
                "}\n" +
                "\n" +
                "function getMeshesFromGroup(groupArray) {\n" +
                "    let meshes = [];\n" +
                "    groupArray.forEach(g => g.children.forEach(c => meshes.push(c)));\n" +
                "    return meshes;\n" +
                "}\n" +
                "\n" +
                "// --- ВЫДЕЛЕНИЕ И UI ---\n" +
                "function selectObject(obj) {\n" +
                "    selectedTarget = obj;\n" +
                "    if (obj.userData.type !== 'floor') {\n" +
                "        selectionBox.setFromObject(obj); selectionBox.visible = true;\n" +
                "    } else {\n" +
                "        selectionBox.visible = false;\n" +
                "    }\n" +
                "    document.getElementById('edit-title').innerText = obj.userData.name;\n" +
                "    document.getElementById('edit-panel').classList.add('active');\n" +
                "    \n" +
                "    updateParamsTab(obj);\n" +
                "    if(obj.userData.type === 'furniture') updateDistanceVisuals();\n" +
                "}\n" +
                "\n" +
                "function deselect() {\n" +
                "    if (window.Room3D && Room3D.MaterialManager) Room3D.MaterialManager.clearHighlight();\n" +
                "    selectedTarget = null;\n" +
                "    if(selectionBox) selectionBox.visible = false;\n" +
                "    document.getElementById('edit-panel').classList.remove('active');\n" +
                "    clearDistanceVisuals();\n" +
                "}\n" +
                "\n" +
                "function initUI() {\n" +
                "    const colorsContainer = document.getElementById('tab-colors');\n" +
                "    defaultColors.forEach(color => {\n" +
                "        const swatch = document.createElement('div');\n" +
                "        swatch.className = 'color-swatch'; swatch.style.backgroundColor = color;\n" +
                "        swatch.onclick = () => applyColor(color);\n" +
                "        colorsContainer.insertBefore(swatch, colorsContainer.lastElementChild);\n" +
                "    });\n" +
                "}\n" +
                "\n" +
                "function switchTab(tabName) {\n" +
                "    document.querySelectorAll('.tab-btn').forEach(btn => btn.classList.remove('active'));\n" +
                "    document.querySelectorAll('.tab-content').forEach(content => content.classList.remove('active'));\n" +
                "    \n" +
                "    event.target.classList.add('active');\n" +
                "    document.getElementById(`tab-${tabName}`).classList.add('active');\n" +
                "}\n" +
                "\n" +
                "// --- ЦВЕТА И ТЕКСТУРЫ ---\n" +
                "function applyColor(colorHex) {\n" +
                "    if (!selectedTarget) return;\n" +
                "    \n" +
                "    if(selectedTarget.userData.type === 'floor') {\n" +
                "        appData.floor.color = colorHex; appData.floor.texture = null;\n" +
                "    } else {\n" +
                "        selectedTarget.userData.data.color = colorHex; selectedTarget.userData.data.texture = null;\n" +
                "    }\n" +
                "\n" +
                "    const applyToMesh = (mesh) => {\n" +
                "        if (!mesh.material) return;\n" +
                "        mesh.material = mesh.material.clone(); \n" +
                "        mesh.material.color.set(colorHex);\n" +
                "        mesh.material.map = null; \n" +
                "        mesh.material.needsUpdate = true;\n" +
                "    };\n" +
                "\n" +
                "    if (selectedTarget.isMesh) applyToMesh(selectedTarget);\n" +
                "    else selectedTarget.children.forEach(applyToMesh);\n" +
                "    \n" +
                "    cleanupUnusedTextures();\n" +
                "}\n" +
                "function applyCustomColor(colorHex) { applyColor(colorHex); }\n" +
                "\n" +
                "function getSelectedMeshId() {\n" +
                "    if (!selectedTarget) return 'floor';\n" +
                "    const t = selectedTarget.userData.type;\n" +
                "    if (t === 'floor') return 'floor';\n" +
                "    if (t === 'wall') return 'wall_' + selectedTarget.userData.index;\n" +
                "    if (t === 'furniture') return 'furniture_' + selectedTarget.userData.index;\n" +
                "    return 'floor';\n" +
                "}\n" +
                "function openAndroidTexturePicker() {\n" +
                "    if (typeof Android3D !== 'undefined' && Android3D.openTexturePicker)\n" +
                "        Android3D.openTexturePicker(getSelectedMeshId());\n" +
                "    else document.getElementById('custom-tex').click();\n" +
                "}\n" +
                "\n" +
                "function applyCustomTexture(event) {\n" +
                "    if (!selectedTarget || !event.target.files.length) return;\n" +
                "    const file = event.target.files[0];\n" +
                "    const url = URL.createObjectURL(file);\n" +
                "    \n" +
                "    const loader = new THREE.TextureLoader();\n" +
                "    loader.load(url, (texture) => {\n" +
                "        texture.wrapS = THREE.RepeatWrapping; texture.wrapT = THREE.RepeatWrapping;\n" +
                "        if (selectedTarget.userData.type === 'floor') texture.repeat.set(10, 10);\n" +
                "        else texture.repeat.set(2, 2);\n" +
                "\n" +
                "        textureCache[url] = texture;\n" +
                "        if(selectedTarget.userData.type === 'floor') appData.floor.texture = url;\n" +
                "        else selectedTarget.userData.data.texture = url;\n" +
                "\n" +
                "        const applyTexToMesh = (mesh) => {\n" +
                "            if (!mesh.material) return;\n" +
                "            mesh.material = mesh.material.clone();\n" +
                "            mesh.material.color.set(0xffffff);\n" +
                "            mesh.material.map = texture;\n" +
                "            mesh.material.needsUpdate = true;\n" +
                "        };\n" +
                "\n" +
                "        if (selectedTarget.isMesh) applyTexToMesh(selectedTarget);\n" +
                "        else selectedTarget.children.forEach(applyTexToMesh);\n" +
                "\n" +
                "        const texContainer = document.getElementById('tab-textures');\n" +
                "        const swatch = document.createElement('div');\n" +
                "        swatch.className = 'tex-swatch'; swatch.style.backgroundImage = `url(${url})`;\n" +
                "        swatch.style.backgroundSize = 'cover';\n" +
                "        swatch.onclick = () => {\n" +
                "            if(selectedTarget.userData.type === 'floor') appData.floor.texture = url;\n" +
                "            else selectedTarget.userData.data.texture = url;\n" +
                "            if (selectedTarget.isMesh) applyTexToMesh(selectedTarget);\n" +
                "            else selectedTarget.children.forEach(applyTexToMesh);\n" +
                "            cleanupUnusedTextures();\n" +
                "        };\n" +
                "        texContainer.insertBefore(swatch, texContainer.lastElementChild);\n" +
                "        cleanupUnusedTextures();\n" +
                "    });\n" +
                "    event.target.value = '';\n" +
                "}\n" +
                "\n" +
                "// --- ГЕНЕРАЦИЯ ПАНЕЛИ ПАРАМЕТРОВ (\"EDIT EVERYTHING\") ---\n" +
                "function updateParamsTab(obj) {\n" +
                "    const content = document.getElementById('tab-params');\n" +
                "    const type = obj.userData.type; \n" +
                "    const data = obj.userData.data;\n" +
                "\n" +
                "    content.innerHTML = ''; // Очистка\n" +
                "\n" +
                "    if(type === 'floor') { \n" +
                "        content.innerHTML = `<div style=\"text-align:center; padding: 20px; color:#888;\">Для пола нет пространственных габаритов. Воспользуйтесь вкладками \"Цвета\" и \"Текстуры\".</div>`; \n" +
                "        return; \n" +
                "    }\n" +
                "\n" +
                "    if(type === 'furniture') {\n" +
                "        let html = `\n" +
                "            <div class=\"prop-group\">\n" +
                "                <span class=\"prop-title\">Позиция на плане (м)</span>\n" +
                "                <div class=\"prop-row\"><label>Ось X</label> <input type=\"number\" id=\"prop-pos-x\" value=\"${data.x.toFixed(2)}\" step=\"0.1\" class=\"param-input\"></div>\n" +
                "                <div class=\"prop-row\"><label>Ось Y (Глубина)</label> <input type=\"number\" id=\"prop-pos-y\" value=\"${data.y.toFixed(2)}\" step=\"0.1\" class=\"param-input\"></div>\n" +
                "            </div>\n" +
                "            <div class=\"prop-group\">\n" +
                "                <span class=\"prop-title\">Габариты и вращение</span>\n" +
                "                <div class=\"prop-row\"><label>Ширина (м)</label> <input type=\"number\" id=\"prop-w\" value=\"${data.width.toFixed(2)}\" step=\"0.1\" class=\"param-input\"></div>\n" +
                "                <div class=\"prop-row\"><label>Глубина (м)</label> <input type=\"number\" id=\"prop-d\" value=\"${data.depth.toFixed(2)}\" step=\"0.1\" class=\"param-input\"></div>\n" +
                "                <div class=\"prop-row\"><label>Высота (м)</label> <input type=\"number\" id=\"prop-h\" value=\"${data.height.toFixed(2)}\" step=\"0.1\" class=\"param-input\"></div>\n" +
                "                <div class=\"prop-row\"><label>Поворот (°)</label> <input type=\"number\" id=\"prop-rot\" value=\"${data.rotation}\" step=\"15\" class=\"param-input\"></div>\n" +
                "            </div>\n" +
                "            <div class=\"prop-group\" id=\"dist-group-container\">\n" +
                "                <span class=\"prop-title\">Расстояния до стен (м)</span>\n" +
                "                <div id=\"dist-group\">Загрузка...</div>\n" +
                "            </div>\n" +
                "            <button class=\"btn-full danger\" onclick=\"deleteSelected()\">\uD83D\uDDD1 Удалить объект</button>\n" +
                "        `;\n" +
                "        content.innerHTML = html;\n" +
                "\n" +
                "        // Привязка событий размеров и поворота\n" +
                "        ['w','d','h','rot'].forEach(k => {\n" +
                "            document.getElementById(`prop-${k}`).addEventListener('change', (e) => {\n" +
                "                const val = parseFloat(e.target.value);\n" +
                "                if(!isNaN(val)) {\n" +
                "                    if(k==='w') data.width = val; if(k==='d') data.depth = val;\n" +
                "                    if(k==='h') data.height = val; if(k==='rot') data.rotation = val;\n" +
                "                    buildScene(); // Перестраиваем сцену\n" +
                "                }\n" +
                "            });\n" +
                "        });\n" +
                "\n" +
                "        // Привязка событий точной позиции\n" +
                "        ['pos-x', 'pos-y'].forEach(k => {\n" +
                "            document.getElementById(`prop-${k}`).addEventListener('change', (e) => {\n" +
                "                const val = parseFloat(e.target.value);\n" +
                "                if(!isNaN(val)) {\n" +
                "                    const oldPos = {x: data.x, y: data.y};\n" +
                "                    if(k==='pos-x') data.x = val;\n" +
                "                    if(k==='pos-y') data.y = val;\n" +
                "                    \n" +
                "                    // Временная проверка коллизии перед перестройкой\n" +
                "                    const fOBB = createOBB(data.x * SCALE, data.y * SCALE, data.width * SCALE, data.depth * SCALE, data.rotation * Math.PI / 180);\n" +
                "                    let col = false;\n" +
                "                    for(let wOBB of wallColliders) if(checkOBBIntersection(fOBB, wOBB)) col = true;\n" +
                "\n" +
                "                    if(col) {\n" +
                "                        data.x = oldPos.x; data.y = oldPos.y; // Откат\n" +
                "                        e.target.value = oldPos[k==='pos-x'?'x':'y'].toFixed(2);\n" +
                "                        alert(\"Нельзя разместить объект внутри стены.\");\n" +
                "                    } else {\n" +
                "                        buildScene();\n" +
                "                    }\n" +
                "                }\n" +
                "            });\n" +
                "        });\n" +
                "    }\n" +
                "\n" +
                "    if(type === 'wall') {\n" +
                "        const dx = data.x2 - data.x1, dz = data.y2 - data.y1;\n" +
                "        const len = Math.sqrt(dx*dx + dz*dz);\n" +
                "\n" +
                "        let html = `\n" +
                "            <div class=\"prop-group\">\n" +
                "                <span class=\"prop-title\">Конструкция стены</span>\n" +
                "                <div class=\"prop-row\"><label>Длина (м)</label> <input type=\"number\" value=\"${len.toFixed(2)}\" disabled class=\"param-input\" style=\"color:#888;\"></div>\n" +
                "                <div class=\"prop-row\"><label>Толщина (м)</label> <input type=\"number\" id=\"wall-thick\" value=\"${(data.thicknessMeters||0.2).toFixed(2)}\" step=\"0.05\" class=\"param-input\"></div>\n" +
                "            </div>\n" +
                "            \n" +
                "            <div class=\"prop-group\">\n" +
                "                <span class=\"prop-title\">Проемы (Окна/Двери)</span>\n" +
                "                <div id=\"openings-list\"></div>\n" +
                "                <button class=\"btn-full primary\" onclick=\"addOpeningToWall()\">+ Добавить проем</button>\n" +
                "            </div>\n" +
                "        `;\n" +
                "        content.innerHTML = html;\n" +
                "\n" +
                "        document.getElementById('wall-thick').addEventListener('change', (e) => {\n" +
                "            const val = parseFloat(e.target.value);\n" +
                "            if(!isNaN(val) && val >= 0.05) { data.thicknessMeters = val; buildScene(); }\n" +
                "        });\n" +
                "\n" +
                "        renderOpeningsList(data.openings || [], obj.userData.index);\n" +
                "    }\n" +
                "}\n" +
                "\n" +
                "// Вспомогательная функция для обновления UI позиций при перетаскивании (без перерендера панели)\n" +
                "function updateFurnPosInputsUI() {\n" +
                "    if(!selectedTarget || selectedTarget.userData.type !== 'furniture') return;\n" +
                "    const px = document.getElementById('prop-pos-x');\n" +
                "    const py = document.getElementById('prop-pos-y');\n" +
                "    if(px) px.value = selectedTarget.userData.data.x.toFixed(2);\n" +
                "    if(py) py.value = selectedTarget.userData.data.y.toFixed(2);\n" +
                "}\n" +
                "\n" +
                "// --- РЕДАКТИРОВАНИЕ СТЕН (ПРОЕМЫ) ---\n" +
                "function renderOpeningsList(openings, wallIndex) {\n" +
                "    const list = document.getElementById('openings-list');\n" +
                "    if(!list) return;\n" +
                "    list.innerHTML = '';\n" +
                "\n" +
                "    if(openings.length === 0) {\n" +
                "        list.innerHTML = '<div style=\"color:#888; font-size:12px; margin-bottom:10px;\">Проемов нет.</div>';\n" +
                "        return;\n" +
                "    }\n" +
                "\n" +
                "    openings.forEach((op, i) => {\n" +
                "        const item = document.createElement('div');\n" +
                "        item.className = 'opening-item';\n" +
                "        item.innerHTML = `\n" +
                "            <div class=\"opening-header\">\n" +
                "                <span>Проем ${i+1}</span>\n" +
                "                <button class=\"btn-sm-danger\" onclick=\"deleteOpening(${wallIndex}, ${i})\">Удалить</button>\n" +
                "            </div>\n" +
                "            <div class=\"prop-row\">\n" +
                "                <label>Тип</label>\n" +
                "                <select class=\"param-select\" id=\"op-type-${i}\">\n" +
                "                    <option value=\"WINDOW\" ${op.type==='WINDOW'?'selected':''}>Окно</option>\n" +
                "                    <option value=\"DOOR\" ${op.type==='DOOR'?'selected':''}>Дверь</option>\n" +
                "                </select>\n" +
                "            </div>\n" +
                "            <div class=\"prop-row\"><label>Позиция (0-1)</label> <input type=\"number\" id=\"op-pos-${i}\" value=\"${op.pos}\" step=\"0.05\" min=\"0.05\" max=\"0.95\" class=\"param-input\"></div>\n" +
                "            <div class=\"prop-row\"><label>Ширина (м)</label> <input type=\"number\" id=\"op-w-${i}\" value=\"${(op.widthMeters||1.0).toFixed(2)}\" step=\"0.1\" class=\"param-input\"></div>\n" +
                "        `;\n" +
                "        list.appendChild(item);\n" +
                "\n" +
                "        // Биндинг\n" +
                "        document.getElementById(`op-type-${i}`).addEventListener('change', e => { op.type = e.target.value; buildScene(); });\n" +
                "        document.getElementById(`op-pos-${i}`).addEventListener('change', e => { op.pos = Math.max(0.01, Math.min(0.99, parseFloat(e.target.value))); buildScene(); });\n" +
                "        document.getElementById(`op-w-${i}`).addEventListener('change', e => { op.widthMeters = Math.max(0.3, parseFloat(e.target.value)); buildScene(); });\n" +
                "    });\n" +
                "}\n" +
                "\n" +
                "function addOpeningToWall() {\n" +
                "    if(!selectedTarget || selectedTarget.userData.type !== 'wall') return;\n" +
                "    const data = selectedTarget.userData.data;\n" +
                "    if(!data.openings) data.openings = [];\n" +
                "    data.openings.push({ type: 'WINDOW', pos: 0.5, widthMeters: 1.0 });\n" +
                "    buildScene();\n" +
                "}\n" +
                "\n" +
                "function deleteOpening(wallIndex, opIndex) {\n" +
                "    const wall = appData.walls[wallIndex];\n" +
                "    if(wall && wall.openings) {\n" +
                "        wall.openings.splice(opIndex, 1);\n" +
                "        buildScene();\n" +
                "    }\n" +
                "}\n" +
                "\n" +
                "// --- ЛИНИИ РАССТОЯНИЙ И УМНЫЕ ИНПУТЫ ---\n" +
                "function updateDistanceVisuals() {\n" +
                "    clearDistanceVisuals();\n" +
                "    if(!selectedTarget || selectedTarget.userData.type !== 'furniture') return;\n" +
                "\n" +
                "    if(selectionBox) selectionBox.box.setFromObject(selectedTarget);\n" +
                "    const box = new THREE.Box3().setFromObject(selectedTarget);\n" +
                "    const center = new THREE.Vector3();\n" +
                "    box.getCenter(center); \n" +
                "    \n" +
                "    center.y = 1.0 * SCALE; // Стреляем лучами на уровне метра от пола\n" +
                "\n" +
                "    const dirs = [\n" +
                "        { dir: new THREE.Vector3(-1, 0, 0), name: 'Запад (-X)', axis: 'x', sign: -1 },\n" +
                "        { dir: new THREE.Vector3(1, 0, 0), name: 'Восток (+X)', axis: 'x', sign: 1 },\n" +
                "        { dir: new THREE.Vector3(0, 0, -1), name: 'Север (-Z)', axis: 'z', sign: -1 },\n" +
                "        { dir: new THREE.Vector3(0, 0, 1), name: 'Юг (+Z)', axis: 'z', sign: 1 }\n" +
                "    ];\n" +
                "\n" +
                "    const wMeshes = getMeshesFromGroup(wallMeshes);\n" +
                "    const ray = new THREE.Raycaster();\n" +
                "    const mat = new THREE.LineDashedMaterial({ color: 0xffaa00, dashSize: 0.5, gapSize: 0.2, linewidth: 2 });\n" +
                "\n" +
                "    const distGroup = document.getElementById('dist-group');\n" +
                "    let hasDists = false;\n" +
                "\n" +
                "    dirs.forEach(d => {\n" +
                "        ray.set(center, d.dir);\n" +
                "        const hits = ray.intersectObjects(wMeshes, false);\n" +
                "        if(hits.length > 0) {\n" +
                "            hasDists = true;\n" +
                "            const hitPoint = hits[0].point;\n" +
                "            let edgePoint = center.clone();\n" +
                "            \n" +
                "            // Находим край AABB мебели\n" +
                "            if(d.axis === 'x') edgePoint.x = d.sign < 0 ? box.min.x : box.max.x;\n" +
                "            if(d.axis === 'z') edgePoint.z = d.sign < 0 ? box.min.z : box.max.z;\n" +
                "            \n" +
                "            const dist = edgePoint.distanceTo(hitPoint) / SCALE;\n" +
                "            distanceVisuals.data[d.name] = { dist: dist, axis: d.axis, sign: d.sign };\n" +
                "            \n" +
                "            // Визуализация в 3D\n" +
                "            edgePoint.y = 0.5; hitPoint.y = 0.5; // Чуть выше пола\n" +
                "            const geo = new THREE.BufferGeometry().setFromPoints([edgePoint, hitPoint]);\n" +
                "            const line = new THREE.Line(geo, mat);\n" +
                "            line.computeLineDistances();\n" +
                "            scene.add(line);\n" +
                "            distanceVisuals.lines.push(line);\n" +
                "\n" +
                "            const midPoint = new THREE.Vector3().addVectors(edgePoint, hitPoint).multiplyScalar(0.5);\n" +
                "            midPoint.y = 1.5; // Текст над линией\n" +
                "            const el = document.createElement('div');\n" +
                "            el.className = 'dist-label'; el.textContent = `${dist.toFixed(2)} м`;\n" +
                "            document.getElementById('label-container').appendChild(el);\n" +
                "            distanceVisuals.labels.push({ element: el, pos: midPoint });\n" +
                "        }\n" +
                "    });\n" +
                "\n" +
                "    // Умное обновление UI панели (не теряя фокус)\n" +
                "    if(distGroup) {\n" +
                "        if(!hasDists) {\n" +
                "            distGroup.innerHTML = '<div style=\"color:#888; font-size:12px;\">Стены вне зоны видимости.</div>';\n" +
                "            return;\n" +
                "        }\n" +
                "\n" +
                "        // Если инпутов еще нет, создаем их\n" +
                "        if(distGroup.children.length === 0 || distGroup.innerHTML.includes('Загрузка')) {\n" +
                "            let html = '';\n" +
                "            for(let key in distanceVisuals.data) {\n" +
                "                const item = distanceVisuals.data[key];\n" +
                "                html += `<div class=\"prop-row\"><label>${key}</label> <input type=\"number\" id=\"dist-inp-${item.axis}-${item.sign}\" data-axis=\"${item.axis}\" data-sign=\"${item.sign}\" value=\"${item.dist.toFixed(2)}\" step=\"0.05\" class=\"param-input dist-manual-inp\"></div>`;\n" +
                "            }\n" +
                "            distGroup.innerHTML = html;\n" +
                "\n" +
                "            // Привязываем логику ручного ввода отступов\n" +
                "            document.querySelectorAll('.dist-manual-inp').forEach(inp => {\n" +
                "                inp.addEventListener('change', (e) => {\n" +
                "                    const newVal = parseFloat(e.target.value);\n" +
                "                    const axis = e.target.dataset.axis;\n" +
                "                    const sign = parseFloat(e.target.dataset.sign);\n" +
                "                    \n" +
                "                    if(!isNaN(newVal)) {\n" +
                "                        // Рассчитываем, насколько нужно подвинуть объект\n" +
                "                        const currentData = distanceVisuals.data[dirs.find(d => d.axis === axis && d.sign === sign).name];\n" +
                "                        const diff = (newVal - currentData.dist) * sign;\n" +
                "                        \n" +
                "                        const targetData = selectedTarget.userData.data;\n" +
                "                        const oldPos = {x: targetData.x, y: targetData.y};\n" +
                "                        \n" +
                "                        if(axis === 'x') targetData.x -= diff;\n" +
                "                        if(axis === 'z') targetData.y -= diff;\n" +
                "\n" +
                "                        // Проверка коллизии\n" +
                "                        const fOBB = createOBB(targetData.x * SCALE, targetData.y * SCALE, targetData.width * SCALE, targetData.depth * SCALE, targetData.rotation * Math.PI / 180);\n" +
                "                        let col = false;\n" +
                "                        for(let wOBB of wallColliders) if(checkOBBIntersection(fOBB, wOBB)) col = true;\n" +
                "\n" +
                "                        if(col) {\n" +
                "                            targetData.x = oldPos.x; targetData.y = oldPos.y; // Откат\n" +
                "                            alert(\"Нельзя подвинуть в стену.\");\n" +
                "                            updateDistanceVisuals(); // Возвращаем инпут к старому значению\n" +
                "                        } else {\n" +
                "                            buildScene(); // Успех\n" +
                "                        }\n" +
                "                    }\n" +
                "                });\n" +
                "            });\n" +
                "        } else {\n" +
                "            // Если инпуты уже есть, просто обновляем их значения (полезно при Drag & Drop)\n" +
                "            for(let key in distanceVisuals.data) {\n" +
                "                const item = distanceVisuals.data[key];\n" +
                "                const inp = document.getElementById(`dist-inp-${item.axis}-${item.sign}`);\n" +
                "                if(inp && document.activeElement !== inp) {\n" +
                "                    inp.value = item.dist.toFixed(2);\n" +
                "                }\n" +
                "            }\n" +
                "        }\n" +
                "    }\n" +
                "}\n" +
                "\n" +
                "function clearDistanceVisuals() {\n" +
                "    distanceVisuals.lines.forEach(l => { if(l.geometry) l.geometry.dispose(); if(l.material) l.material.dispose(); scene.remove(l); });\n" +
                "    distanceVisuals.lines = [];\n" +
                "    distanceVisuals.labels.forEach(l => l.element.remove()); distanceVisuals.labels = [];\n" +
                "    distanceVisuals.data = {};\n" +
                "}\n" +
                "\n" +
                "// --- УПРАВЛЕНИЕ СЦЕНОЙ ---\n" +
                "function deleteSelected() {\n" +
                "    if(!selectedTarget) return;\n" +
                "    const type = selectedTarget.userData.type;\n" +
                "    const idx = selectedTarget.userData.index;\n" +
                "    \n" +
                "    if(type === 'furniture') appData.furniture.splice(idx, 1);\n" +
                "    if(type === 'wall') appData.walls.splice(idx, 1);\n" +
                "    \n" +
                "    deselect(); \n" +
                "    buildScene();\n" +
                "    cleanupUnusedTextures();\n" +
                "}\n" +
                "\n" +
                "function addFurniture(type) {\n" +
                "    appData.furniture.push({ type: type, x: 5, y: 5, color: '#4ABAED', width: 1.2, depth: 1.2, height: type==='table'?0.8:2.0, rotation: 0 });\n" +
                "    buildScene(); \n" +
                "    // Автоматически выделяем новый объект\n" +
                "    const newTarget = furnMeshes[furnMeshes.length-1];\n" +
                "    selectObject(newTarget);\n" +
                "}\n" +
                "\n" +
                "// --- ХОДЬБА ---\n" +
                "function toggleWalkMode() {\n" +
                "    isWalkMode = !isWalkMode;\n" +
                "    const btn = document.getElementById('btn-walk');\n" +
                "    const dpad = document.getElementById('d-pad');\n" +
                "    \n" +
                "    deselect();\n" +
                "\n" +
                "    if(isWalkMode) {\n" +
                "        btn.textContent = \"\uD83D\uDC41 Обзор\"; btn.classList.add('active');\n" +
                "        dpad.style.display = 'grid'; controls.enabled = false;\n" +
                "        document.getElementById('tools-panel').style.display = 'none';\n" +
                "        \n" +
                "        var wmnX=Infinity,wmxX=-Infinity,wmnZ=Infinity,wmxZ=-Infinity;\n" +
                "        appData.walls.forEach(function(w){\n" +
                "            wmnX=Math.min(wmnX,w.x1,w.x2); wmxX=Math.max(wmxX,w.x1,w.x2);\n" +
                "            wmnZ=Math.min(wmnZ,w.y1,w.y2); wmxZ=Math.max(wmxZ,w.y1,w.y2);\n" +
                "        });\n" +
                "        var wcx=((wmnX+wmxX)/2)*SCALE, wcz=((wmnZ+wmxZ)/2)*SCALE;\n" +
                "        camera.position.set(wcx, 1.7 * SCALE, wcz); // Рост 1.7м\n" +
                "        camera.rotation.order = 'YXZ'; yaw = 0; pitch = 0; camera.rotation.set(0,0,0);\n" +
                "        if (window.Room3D) Room3D.setCeilingVisible(true);\n" +
                "    } else {\n" +
                "        btn.textContent = \"\uD83D\uDEB6 Ходьба\"; btn.classList.remove('active');\n" +
                "        dpad.style.display = 'none'; controls.enabled = true;\n" +
                "        document.getElementById('tools-panel').style.display = 'flex';\n" +
                "        if (window.Room3D) Room3D.setCeilingVisible(false);\n" +
                "        resetCamera();\n" +
                "    }\n" +
                "}\n" +
                "\n" +
                "function setupFPS() {\n" +
                "    renderer.domElement.addEventListener('pointerdown', e => {\n" +
                "        if(!isWalkMode || e.target.closest('#edit-panel') || e.target.closest('#top-bar')) return;\n" +
                "        \n" +
                "        // Предотвращаем вращение камеры, если кликнули по мебели (чтобы выделить её)\n" +
                "        raycaster.setFromCamera(mouse, camera);\n" +
                "        if(raycaster.intersectObjects([...getMeshesFromGroup(furnMeshes), ...getMeshesFromGroup(wallMeshes)], false).length > 0) return;\n" +
                "\n" +
                "        isDraggingHead = true; prevTouch = { x: e.clientX, y: e.clientY };\n" +
                "    });\n" +
                "    renderer.domElement.addEventListener('pointermove', e => {\n" +
                "        if(!isWalkMode || !isDraggingHead) return;\n" +
                "        yaw += (e.clientX - prevTouch.x) * 0.005; pitch += (e.clientY - prevTouch.y) * 0.005;\n" +
                "        pitch = Math.max(-Math.PI/2, Math.min(Math.PI/2, pitch));\n" +
                "        camera.rotation.set(pitch, yaw, 0); prevTouch = { x: e.clientX, y: e.clientY };\n" +
                "    });\n" +
                "    renderer.domElement.addEventListener('pointerup', () => isDraggingHead = false);\n" +
                "    \n" +
                "    document.addEventListener('keydown', e => { if(e.key==='w') keys.w=true; if(e.key==='a') keys.a=true; if(e.key==='s') keys.s=true; if(e.key==='d') keys.d=true; });\n" +
                "    document.addEventListener('keyup', e => { if(e.key==='w') keys.w=false; if(e.key==='a') keys.a=false; if(e.key==='s') keys.s=false; if(e.key==='d') keys.d=false; });\n" +
                "}\n" +
                "\n" +
                "function checkWalkCollision(nextPos) {\n" +
                "    const playerPos = new THREE.Vector2(nextPos.x, nextPos.z);\n" +
                "    for(let wOBB of walkColliders) {\n" +
                "        const t = new THREE.Vector2().subVectors(playerPos, wOBB.center);\n" +
                "        const localX = t.dot(wOBB.axes[0]); const localY = t.dot(wOBB.axes[1]);\n" +
                "        const closestX = Math.max(-wOBB.extents.x, Math.min(wOBB.extents.x, localX));\n" +
                "        const closestY = Math.max(-wOBB.extents.y, Math.min(wOBB.extents.y, localY));\n" +
                "        const dx = localX - closestX; const dy = localY - closestY;\n" +
                "        if((dx * dx + dy * dy) < (playerRadius * playerRadius)) return true;\n" +
                "    }\n" +
                "    return false;\n" +
                "}\n" +
                "window.checkWalkCollision = checkWalkCollision;\n" +
                "\n" +
                "// --- МАТЕМАТИКА ОВВ (Oriented Bounding Box) ---\n" +
                "function createOBB(x, z, w, d, angle) {\n" +
                "    const cos = Math.cos(angle); const sin = Math.sin(angle);\n" +
                "    return { center: new THREE.Vector2(x, z), extents: new THREE.Vector2(w / 2, d / 2), axes: [new THREE.Vector2(cos, -sin).normalize(), new THREE.Vector2(sin, cos).normalize()] };\n" +
                "}\n" +
                "\n" +
                "function checkOBBIntersection(a, b) {\n" +
                "    const axes = [a.axes[0], a.axes[1], b.axes[0], b.axes[1]];\n" +
                "    const t = new THREE.Vector2().subVectors(b.center, a.center);\n" +
                "    for (let i = 0; i < 4; i++) {\n" +
                "        const axis = axes[i];\n" +
                "        const projT = Math.abs(t.dot(axis));\n" +
                "        const projA = Math.abs(a.axes[0].dot(axis)) * a.extents.x + Math.abs(a.axes[1].dot(axis)) * a.extents.y;\n" +
                "        const projB = Math.abs(b.axes[0].dot(axis)) * b.extents.x + Math.abs(b.axes[1].dot(axis)) * b.extents.y;\n" +
                "        if (projT > projA + projB) return false;\n" +
                "    }\n" +
                "    return true; \n" +
                "}\n" +
                "\n" +
                "// --- УТИЛИТЫ И АНИМАЦИЯ ---\n" +
                "function resetCamera() {\n" +
                "    if(isWalkMode || !appData.walls.length) return;\n" +
                "    var mnX=Infinity,mxX=-Infinity,mnZ=Infinity,mxZ=-Infinity;\n" +
                "    appData.walls.forEach(function(w){\n" +
                "        mnX=Math.min(mnX,w.x1,w.x2); mxX=Math.max(mxX,w.x1,w.x2);\n" +
                "        mnZ=Math.min(mnZ,w.y1,w.y2); mxZ=Math.max(mxZ,w.y1,w.y2);\n" +
                "    });\n" +
                "    var cx=((mnX+mxX)/2)*SCALE, cz=((mnZ+mxZ)/2)*SCALE;\n" +
                "    var sz=Math.max((mxX-mnX),(mxZ-mnZ))*SCALE;\n" +
                "    camera.position.set(cx, sz*0.9+appData.height*SCALE, cz+sz*0.7);\n" +
                "    controls.target.set(cx, 0, cz); controls.update();\n" +
                "}\n" +
                "\n" +
                "function onWindowResize() { camera.aspect = window.innerWidth / window.innerHeight; camera.updateProjectionMatrix(); renderer.setSize(window.innerWidth, window.innerHeight); }\n" +
                "\n" +
                "function animate() {\n" +
                "    requestAnimationFrame(animate);\n" +
                "    const delta = clock.getDelta();\n" +
                "\n" +
                "    if(!isWalkMode && controls) controls.update();\n" +
                "    \n" +
                "    if(isWalkMode) {\n" +
                "        const spd = 48 * delta; \n" +
                "        const nextPos = camera.position.clone();\n" +
                "        let moved = false;\n" +
                "        \n" +
                "        if(keys.w) { nextPos.x -= Math.sin(yaw)*spd; nextPos.z -= Math.cos(yaw)*spd; moved = true; }\n" +
                "        if(keys.s) { nextPos.x += Math.sin(yaw)*spd; nextPos.z += Math.cos(yaw)*spd; moved = true; }\n" +
                "        if(keys.a) { nextPos.x -= Math.sin(yaw + Math.PI/2)*spd; nextPos.z -= Math.cos(yaw + Math.PI/2)*spd; moved = true; }\n" +
                "        if(keys.d) { nextPos.x += Math.sin(yaw + Math.PI/2)*spd; nextPos.z += Math.cos(yaw + Math.PI/2)*spd; moved = true; }\n" +
                "        \n" +
                "        const walkHit = (p) => window.Room3D ? Room3D.checkWalkCollisionExtended(p) : checkWalkCollision(p);\n" +
                "        if(moved) {\n" +
                "            if(!walkHit(nextPos)) camera.position.copy(nextPos);\n" +
                "            else {\n" +
                "                const tryX = camera.position.clone(); tryX.x = nextPos.x;\n" +
                "                if(!walkHit(tryX)) camera.position.x = tryX.x;\n" +
                "                const tryZ = camera.position.clone(); tryZ.z = nextPos.z;\n" +
                "                if(!walkHit(tryZ)) camera.position.z = tryZ.z;\n" +
                "            }\n" +
                "        }\n" +
                "    } else {\n" +
                "        // Обновление HTML-ярлыков расстояний (чтобы следовали за камерой)\n" +
                "        distanceVisuals.labels.forEach(lbl => {\n" +
                "            const p = lbl.pos.clone(); p.project(camera);\n" +
                "            if(p.z > 1) { lbl.element.style.display = 'none'; return; }\n" +
                "            lbl.element.style.display = 'block';\n" +
                "            lbl.element.style.left = ((p.x * 0.5 + 0.5) * window.innerWidth) + 'px';\n" +
                "            lbl.element.style.top = ((-p.y * 0.5 + 0.5) * window.innerHeight) + 'px';\n" +
                "        });\n" +
                "        // Обновляем метки размеров стен\n" +
                "        dimLabels.forEach(lbl => {\n" +
                "            const p = lbl.pos.clone(); p.project(camera);\n" +
                "            if(p.z > 1) { lbl.element.style.display = 'none'; return; }\n" +
                "            lbl.element.style.display = 'block';\n" +
                "            lbl.element.style.left = ((p.x * 0.5 + 0.5) * window.innerWidth) + 'px';\n" +
                "            lbl.element.style.top = ((-p.y * 0.5 + 0.5) * window.innerHeight) + 'px';\n" +
                "        });\n" +
                "    }\n" +
                "    renderer.render(scene, camera);\n" +
                "}\n" +
                "\n" +
                "// --- ANDROID TEXTURE BRIDGE ---\n" +
                "window.ThreeJsBridge = {\n" +
                "    updateMeshTexture: function(meshId, dataUrl, repeatU, repeatV) {\n" +
                "        if (!dataUrl || !dataUrl.length) return;\n" +
                "        // Ensure proper data URI prefix\n" +
                "        if (!dataUrl.startsWith('data:')) {\n" +
                "            dataUrl = 'data:image/webp;base64,' + dataUrl;\n" +
                "        }\n" +
                "        // Resolve target mesh/group\n" +
                "        var target = null;\n" +
                "        if (meshId === 'floor') {\n" +
                "            target = floorMesh;\n" +
                "        } else if (meshId.startsWith('furniture_')) {\n" +
                "            var fi = parseInt(meshId.slice(10));\n" +
                "            target = furnMeshes.find(function(m){ return m.userData.index === fi; }) || null;\n" +
                "        } else if (meshId.startsWith('wall_')) {\n" +
                "            var wi = parseInt(meshId.slice(5));\n" +
                "            target = wallMeshes.find(function(m){ return m.userData.index === wi; }) || null;\n" +
                "        }\n" +
                "        if (!target) return;\n" +
                "        // Cache key: reuse per meshId so we can dispose cleanly on next call\n" +
                "        var cacheKey = '__android_' + meshId;\n" +
                "        var prevTex = textureCache[cacheKey];\n" +
                "        var loader = new THREE.TextureLoader();\n" +
                "        loader.load(dataUrl, function(texture) {\n" +
                "            texture.wrapS = THREE.RepeatWrapping;\n" +
                "            texture.wrapT = THREE.RepeatWrapping;\n" +
                "            texture.repeat.set(repeatU, repeatV);\n" +
                "            texture.needsUpdate = true;\n" +
                "            // Persist through buildScene() rebuilds\n" +
                "            textureCache[cacheKey] = texture;\n" +
                "            if (meshId === 'floor') {\n" +
                "                appData.floor.texture = cacheKey;\n" +
                "            } else if (target.userData && target.userData.data) {\n" +
                "                target.userData.data.texture = cacheKey;\n" +
                "            }\n" +
                "            // Apply to every mesh in the group (wall, furniture) or the mesh itself (floor)\n" +
                "            var applyTex = function(mesh) {\n" +
                "                if (!mesh.material) return;\n" +
                "                var mat = mesh.material;\n" +
                "                if (mat.map && mat.map !== texture) mat.map.dispose();\n" +
                "                mat.color.set(0xffffff);\n" +
                "                mat.map = texture;\n" +
                "                mat.needsUpdate = true;\n" +
                "            };\n" +
                "            if (target.isMesh) {\n" +
                "                applyTex(target);\n" +
                "            } else {\n" +
                "                target.traverse(function(child) { if (child.isMesh) applyTex(child); });\n" +
                "            }\n" +
                "            // Dispose previous texture for this slot AFTER applying the new one\n" +
                "            if (prevTex && prevTex !== texture) prevTex.dispose();\n" +
                "        });\n" +
                "    }\n" +
                "};\n" +
                "</script>\n" +
                "</body>\n" +
                "</html>";
    }
}
