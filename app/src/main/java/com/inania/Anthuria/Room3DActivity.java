package com.inania.Anthuria;

import android.annotation.SuppressLint;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.util.Base64;
import android.util.Log;
import android.view.WindowManager;
import android.webkit.ConsoleMessage;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;

import androidx.appcompat.app.AppCompatActivity;

import java.io.ByteArrayOutputStream;

public class Room3DActivity extends AppCompatActivity {

    private WebView webView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        getWindow().setFlags(
                WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
                WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED);

        webView = new WebView(this);
        webView.setBackgroundColor(Color.TRANSPARENT);
        setContentView(webView);

        initWebView();
    }

    @SuppressLint("SetJavaScriptEnabled")
    private void initWebView() {
        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setAllowFileAccess(true);

        WebView.setWebContentsDebuggingEnabled(true);

        // Получение данных из Intent (включая top_color)
        String jsonPlan = getIntent().getStringExtra("json_plan");
        String jsonFurniture = getIntent().getStringExtra("json_furniture");
        float wallHeight = getIntent().getFloatExtra("wall_height", 2.7f);
        String wallColor = getIntent().getStringExtra("wall_color");
        String topColor = getIntent().getStringExtra("top_color");
        String floorUriStr = getIntent().getStringExtra("floor_image_uri");

        String floorBase64 = "";
        if (floorUriStr != null) {
            floorBase64 = getBase64FromUri(Uri.parse(floorUriStr));
        }

        final String finalPlan = (jsonPlan != null && !jsonPlan.isEmpty()) ? jsonPlan : "{\"walls\":[],\"pixelsPerMeter\":100}";
        final String finalFurn = (jsonFurniture != null && !jsonFurniture.isEmpty()) ? jsonFurniture : "[]";
        final String finalColor = (wallColor != null) ? wallColor : "#FFFFFF";
        final String finalTopColor = (topColor != null) ? topColor : "#333333";

        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public boolean onConsoleMessage(ConsoleMessage consoleMessage) {
                Log.d("JS_CONSOLE", consoleMessage.message());
                return true;
            }
        });

        String escapedPlan = finalPlan.replace("\\", "\\\\").replace("'", "\\'").replace("\n", "");
        String escapedFurn = finalFurn.replace("\\", "\\\\").replace("'", "\\'").replace("\n", "");

        // Подставляем все данные в HTML-шаблон
        String html = getHtmlContent()
                .replace("__PLAN_DATA__", escapedPlan)
                .replace("__FURN_DATA__", escapedFurn)
                .replace("__FLOOR_DATA__", floorBase64)
                .replace("__WALL_HEIGHT__", String.valueOf(wallHeight))
                .replace("__WALL_COLOR__", finalColor)
                .replace("__TOP_COLOR__", finalTopColor);

        webView.loadDataWithBaseURL("http://localhost", html, "text/html", "UTF-8", null);
    }

    private String getBase64FromUri(Uri uri) {
        try {
            Bitmap bitmap = MediaStore.Images.Media.getBitmap(this.getContentResolver(), uri);
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            bitmap.compress(Bitmap.CompressFormat.JPEG, 70, outputStream);
            byte[] byteArray = outputStream.toByteArray();
            return "data:image/jpeg;base64," + Base64.encodeToString(byteArray, Base64.NO_WRAP);
        } catch (Exception e) {
            return "";
        }
    }


    private String getHtmlContent() {
        return "<!DOCTYPE html><html><head>\n" +
                "    <meta charset=\"UTF-8\">\n" +
                "    <meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0, user-scalable=no\">\n" +
                "    <style>\n" +
                "        body { margin: 0; overflow: hidden; background: #f0f0f0; font-family: sans-serif; }\n" +
                "        #ui {\n" +
                "            position: absolute;\n" +
                "            top: 40px; /* Отступ под камеру/челку */\n" +
                "            left: 10px;\n" +
                "            right: 10px;\n" +
                "            z-index: 100;\n" +
                "            display: flex;\n" +
                "            gap: 10px;\n" +
                "        }\n" +
                "        button {\n" +
                "            padding: 12px 18px;\n" +
                "            background: #4ABAED;\n" +
                "            color: white;\n" +
                "            border: none;\n" +
                "            border-radius: 8px;\n" +
                "            font-weight: bold;\n" +
                "            box-shadow: 0 4px 6px rgba(0,0,0,0.1);\n" +
                "            flex: 1;\n" +
                "        }\n" +
                "        #error-msg { position: absolute; top: 100px; left: 10px; color: red; background: white; padding: 10px; display: none; z-index: 200; }\n" +
                "    </style>\n" +
                "    <script src=\"https://cdnjs.cloudflare.com/ajax/libs/three.js/r128/three.min.js\"></script>\n" +
                "    <script src=\"https://cdn.jsdelivr.net/npm/three@0.128.0/examples/js/controls/OrbitControls.js\"></script>\n" +
                "</head><body>\n" +
                "    <div id=\"ui\">\n" +
                "        <button onclick=\"resetCamera()\">Центрировать</button>\n" +
                "    </div>\n" +
                "    <div id=\"error-msg\"></div>\n" +
                "    <script>\n" +
                "        let scene, camera, renderer, controls;\n" +
                "        const group = new THREE.Group();\n" +
                "        const SCALE = 10; \n" +
                "\n" +
                "        function showError(msg) {\n" +
                "            const el = document.getElementById('error-msg');\n" +
                "            el.style.display = 'block';\n" +
                "            el.innerText = msg;\n" +
                "            console.error(msg);\n" +
                "        }\n" +
                "\n" +
                "        window.onerror = function(message, source, lineno, colno, error) {\n" +
                "            showError('JS Error: ' + message);\n" +
                "            return true;\n" +
                "        };\n" +
                "\n" +
                "        // Главная функция запуска\n" +
                "        window.onload = function() {\n" +
                "            try {\n" +
                "                if (typeof THREE === 'undefined') {\n" +
                "                    showError('Ошибка сети: Библиотека Three.js не загрузилась. Проверьте подключение к интернету.');\n" +
                "                    return;\n" +
                "                }\n" +
                "                \n" +
                "                // Данные внедряются Android'ом прямо сюда\n" +
                "                const planStr = '__PLAN_DATA__';\n" +
                "                const furnStr = '__FURN_DATA__';\n" +
                "                const height = parseFloat('__WALL_HEIGHT__');\n" +
                "                const wallColor = '__WALL_COLOR__';\n" +
                "                \n" +
                "                const plan = JSON.parse(planStr);\n" +
                "                const furniture = JSON.parse(furnStr);\n" +
                "                \n" +
                "                initRoom(normalizePlan(plan), furniture, height, wallColor);\n" +
                "            } catch(e) {\n" +
                "                showError('Ошибка инициализации сцены: ' + e.message);\n" +
                "            }\n" +
                "        };\n" +
                "\n" +
                "        function normalizePlan(raw) {\n" +
                "            if (Array.isArray(raw)) return { walls: raw, pixelsPerMeter: 100 };\n" +
                "            if (raw && Array.isArray(raw.walls)) return raw;\n" +
                "            if (raw && raw.rooms) {\n" +
                "                const walls = [];\n" +
                "                raw.rooms.forEach(room => {\n" +
                "                    (room.walls || []).forEach(w => walls.push(w));\n" +
                "                });\n" +
                "                return { walls: walls, pixelsPerMeter: raw.pixelsPerMeter || 100 };\n" +
                "            }\n" +
                "            return { walls: [], pixelsPerMeter: 100 };\n" +
                "        }\n" +
                "\n" +
                "        function initRoom(plan, furniture, height, wallColor) {\n" +
                "            scene = new THREE.Scene();\n" +
                "            scene.background = new THREE.Color(0xeeeeee);\n" +
                "\n" +
                "            camera = new THREE.PerspectiveCamera(60, window.innerWidth / window.innerHeight, 0.1, 5000);\n" +
                "            camera.position.set(60, 60, 60);\n" +
                "\n" +
                "            renderer = new THREE.WebGLRenderer({ antialias: true });\n" +
                "            renderer.setPixelRatio(window.devicePixelRatio);\n" +
                "            renderer.setSize(window.innerWidth, window.innerHeight);\n" +
                "            document.body.appendChild(renderer.domElement);\n" +
                "\n" +
                "            scene.add(new THREE.AmbientLight(0xffffff, 0.7));\n" +
                "            const dirLight = new THREE.DirectionalLight(0xffffff, 0.5);\n" +
                "            dirLight.position.set(50, 100, 50);\n" +
                "            scene.add(dirLight);\n" +
                "\n" +
                "            controls = new THREE.OrbitControls(camera, renderer.domElement);\n" +
                "            controls.enableDamping = true;\n" +
                "\n" +
                "            scene.add(new THREE.GridHelper(200, 40, 0xcccccc, 0xdddddd));\n" +
                "            scene.add(group);\n" +
                "\n" +
                "            const ppm = plan.pixelsPerMeter || 100;\n" +
                "            (plan.walls || []).forEach(w => createWallWithOpenings(w, height, wallColor, ppm));\n" +
                "\n" +
                "            // Отрисовка мебели\n" +
                "            if (Array.isArray(furniture)) {\n" +
                "                furniture.forEach(f => {\n" +
                "                    const fW = (f.width || 0.5) * SCALE;\n" +
                "                    const fH = (f.height || 0.8) * SCALE;\n" +
                "                    const fD = (f.depth || f.height || 0.5) * SCALE;\n" +
                "                    const geo = new THREE.BoxGeometry(fW, fH, fD);\n" +
                "                    const mat = new THREE.MeshStandardMaterial({ color: 0x4ABAED, transparent: true, opacity: 0.85 });\n" +
                "                    const mesh = new THREE.Mesh(geo, mat);\n" +
                "                    mesh.position.set(f.x * SCALE, fH/2, f.y * SCALE);\n" +
                "                    mesh.rotation.y = -(f.rotation * Math.PI / 180);\n" +
                "                    group.add(mesh);\n" +
                "                });\n" +
                "            }\n" +
                "\n" +
                "            setTimeout(resetCamera, 100);\n" +
                "            animate();\n" +
                "        }\n" +
                "\n" +
                "        function createWallWithOpenings(w, h, color, ppm) {\n" +
                "            const x1 = (w.startX !== undefined ? w.startX : w.x1) * SCALE;\n" +
                "            const y1 = (w.startY !== undefined ? w.startY : w.y1) * SCALE;\n" +
                "            const x2 = (w.endX !== undefined ? w.endX : w.x2) * SCALE;\n" +
                "            const y2 = (w.endY !== undefined ? w.endY : w.y2) * SCALE;\n" +
                "\n" +
                "            const dx = x2 - x1, dz = y2 - y1;\n" +
                "            const len = Math.sqrt(dx*dx + dz*dz);\n" +
                "            if (len < 0.05) return;\n" +
                "\n" +
                "            const wallH = h * SCALE;\n" +
                "            let thickM = 0.15;\n" +
                "            if (w.thicknessMeters !== undefined && w.thicknessMeters > 0) thickM = w.thicknessMeters;\n" +
                "            else if (w.thicknessPx !== undefined && w.thicknessPx > 0 && ppm > 0) thickM = w.thicknessPx / ppm;\n" +
                "            const thick = thickM * SCALE;\n" +
                "\n" +
                "            const geo = new THREE.BoxGeometry(len, wallH, thick);\n" +
                "            const mat = new THREE.MeshStandardMaterial({ color: color });\n" +
                "            const wall = new THREE.Mesh(geo, mat);\n" +
                "\n" +
                "            wall.position.set(x1 + dx/2, wallH/2, y1 + dz/2);\n" +
                "            wall.rotation.y = -Math.atan2(dz, dx);\n" +
                "\n" +
                "            const edges = new THREE.EdgesGeometry(geo);\n" +
                "            const line = new THREE.LineSegments(edges, new THREE.LineBasicMaterial({ color: 0x333333 }));\n" +
                "            wall.add(line);\n" +
                "\n" +
                "            if (w.openings && w.openings.length > 0) {\n" +
                "                w.openings.forEach(op => {\n" +
                "                    const opWm = (op.width !== undefined ? op.width : 0.9);\n" +
                "                    const opW = opWm * SCALE;\n" +
                "                    const opH = (op.type === 'DOOR' ? wallH * 0.8 : wallH * 0.4);\n" +
                "                    const opY = (op.type === 'DOOR' ? opH/2 - wallH/2 : 0);\n" +
                "                    const opGeo = new THREE.BoxGeometry(opW, opH, thick + 0.02);\n" +
                "                    const opMat = new THREE.MeshStandardMaterial({ color: 0x333333, roughness: 1 });\n" +
                "                    const opMesh = new THREE.Mesh(opGeo, opMat);\n" +
                "                    let factor = op.factor;\n" +
                "                    if (factor === undefined && op.pos !== undefined) factor = op.pos;\n" +
                "                    if (factor === undefined && op.start !== undefined && op.end !== undefined) {\n" +
                "                        factor = (op.start + op.end) / 2 / (len / SCALE);\n" +
                "                    }\n" +
                "                    opMesh.position.set(((factor || 0.5) - 0.5) * len, opY, 0);\n" +
                "                    wall.add(opMesh);\n" +
                "                });\n" +
                "            }\n" +
                "            group.add(wall);\n" +
                "        }\n" +
                "\n" +
                "        function resetCamera() {\n" +
                "            const box = new THREE.Box3().setFromObject(group);\n" +
                "            if (box.isEmpty()) return;\n" +
                "            const center = box.getCenter(new THREE.Vector3());\n" +
                "            const size = box.getSize(new THREE.Vector3());\n" +
                "            const maxDim = Math.max(size.x, size.z, 20);\n" +
                "            camera.position.set(center.x + maxDim, maxDim * 1.2, center.z + maxDim);\n" +
                "            controls.target.copy(center);\n" +
                "            controls.update();\n" +
                "        }\n" +
                "\n" +
                "        function animate() {\n" +
                "            requestAnimationFrame(animate);\n" +
                "            if (controls) controls.update();\n" +
                "            if (renderer) renderer.render(scene, camera);\n" +
                "        }\n" +
                "\n" +
                "        window.addEventListener('resize', () => {\n" +
                "            if(!camera || !renderer) return;\n" +
                "            camera.aspect = window.innerWidth / window.innerHeight;\n" +
                "            camera.updateProjectionMatrix();\n" +
                "            renderer.setSize(window.innerWidth, window.innerHeight);\n" +
                "        });\n" +
                "    </script>\n" +
                "</body></html>";
    }
}