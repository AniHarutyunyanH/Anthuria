package com.inania.Anthuria;

import android.annotation.SuppressLint;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.WindowManager;
import android.webkit.ConsoleMessage;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

// MongoDB Imports
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import static com.mongodb.client.model.Filters.eq;
import org.bson.Document;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class Room3DActivity extends AppCompatActivity {

    private WebView webView;
    private ExecutorService executorService;

    // REPLACE with your actual MongoDB Connection String
    private static final String MONGO_URI = "mongodb+srv://<username>:<password>@cluster0.mongodb.net/?retryWrites=true&w=majority";
    private static final String DB_NAME = "InteriorDesignApp";
    private static final String COLLECTION_NAME = "Designs";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        getWindow().setFlags(
                WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
                WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED);

        webView = new WebView(this);
        webView.setBackgroundColor(Color.TRANSPARENT);
        setContentView(webView);

        // Get the design ID from the previous activity
        String designId = getIntent().getStringExtra("design_id");
        if (designId == null) {
            designId = "default_design_1"; // Fallback for testing
        }

        // Fetch data from MongoDB in a background thread (Network calls block main thread)
        executorService = Executors.newSingleThreadExecutor();
        fetchDataFromMongoDB(designId);
    }

    private void fetchDataFromMongoDB(String designId) {
        executorService.execute(() -> {
            try (MongoClient mongoClient = MongoClients.create(MONGO_URI)) {
                MongoDatabase database = mongoClient.getDatabase(DB_NAME);
                MongoCollection<Document> collection = database.getCollection(COLLECTION_NAME);

                // Find the design document by its _id
                Document designDoc = collection.find(eq("_id", designId)).first();

                if (designDoc != null) {
                    // Switch back to Main Thread to update UI (WebView)
                    new Handler(Looper.getMainLooper()).post(() -> initWebView(designDoc));
                } else {
                    showErrorOnMainThread("Design not found in MongoDB.");
                }
            } catch (Exception e) {
                Log.e("MongoDB", "Connection error: ", e);
                showErrorOnMainThread("Database connection error.");
            }
        });
    }

    private void showErrorOnMainThread(String errorMsg) {
        new Handler(Looper.getMainLooper()).post(() ->
                Toast.makeText(Room3DActivity.this, errorMsg, Toast.LENGTH_LONG).show()
        );
    }

    @SuppressLint("SetJavaScriptEnabled")
    private void initWebView(Document designDoc) {
        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setAllowFileAccess(true);

        WebView.setWebContentsDebuggingEnabled(true);

        // Extract NoSQL Data directly into JSON strings for WebView
        // MongoDB Document.toJson() method handles the formatting automatically
        String jsonPlan = designDoc.containsKey("plan") ? designDoc.get("plan", Document.class).toJson() : "{\"walls\":[],\"pixelsPerMeter\":100}";
        String jsonFurniture = designDoc.containsKey("furniture") ? designDoc.get("furniture", java.util.List.class).toString() : "[]";

        float wallHeight = designDoc.containsKey("wall_height") ? designDoc.getDouble("wall_height").floatValue() : 2.7f;
        String wallColor = designDoc.getString("wall_color");
        String topColor = designDoc.getString("top_color");
        String floorBase64 = designDoc.getString("floor_base64"); // Assuming you store base64 or URL in Mongo

        final String finalColor = (wallColor != null) ? wallColor : "#FFFFFF";
        final String finalTopColor = (topColor != null) ? topColor : "#333333";
        final String finalFloor = (floorBase64 != null) ? floorBase64 : "";

        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public boolean onConsoleMessage(ConsoleMessage consoleMessage) {
                Log.d("JS_CONSOLE", consoleMessage.message());
                return true;
            }
        });

        // Inject data into HTML
        String html = getHtmlContent()
                .replace("__PLAN_DATA__", jsonPlan)
                .replace("__FURN_DATA__", jsonFurniture)
                .replace("__FLOOR_DATA__", finalFloor)
                .replace("__WALL_HEIGHT__", String.valueOf(wallHeight))
                .replace("__WALL_COLOR__", finalColor)
                .replace("__TOP_COLOR__", finalTopColor);

        webView.loadDataWithBaseURL("http://localhost", html, "text/html", "UTF-8", null);
    }

    // You can call this from Android to dynamically change colors WITHOUT reloading the page
    public void changeWallColorDynamically(String hexColor) {
        webView.evaluateJavascript("javascript:updateWallColor('" + hexColor + "');", null);
    }

    // You can call this from Android to move a piece of furniture dynamically
    public void moveFurnitureDynamically(int index, float x, float z) {
        webView.evaluateJavascript("javascript:updateFurniturePosition(" + index + ", " + x + ", " + z + ");", null);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (executorService != null) {
            executorService.shutdown();
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
                "            top: 40px;\n" +
                "            left: 10px;\n" +
                "            right: 10px;\n" +
                "            z-index: 100;\n" +
                "            display: flex;\n" +
                "            gap: 10px;\n" +
                "        }\n" +
                "        button { padding: 12px 18px; background: #4ABAED; color: white; border: none; border-radius: 8px; font-weight: bold; flex: 1; }\n" +
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
                "        const wallsArray = []; // Store walls to change color later\n" +
                "        const furnitureArray = []; // Store furniture to move later\n" +
                "        const SCALE = 10; \n" +
                "\n" +
                "        function showError(msg) { ... }\n" +
                "\n" +
                "        window.onload = function() {\n" +
                "            const planStr = `__PLAN_DATA__`;\n" +
                "            const furnStr = `__FURN_DATA__`;\n" +
                "            const height = parseFloat('__WALL_HEIGHT__');\n" +
                "            const wallColor = '__WALL_COLOR__';\n" +
                "            \n" +
                "            const plan = JSON.parse(planStr);\n" +
                "            // Replace single quotes with double quotes for valid JSON arrays from MongoDB toString()\n" +
                "            const furniture = JSON.parse(furnStr.replace(/'/g, '\"')); \n" +
                "            \n" +
                "            initRoom(plan, furniture, height, wallColor);\n" +
                "        };\n" +
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
                "            scene.add(new THREE.GridHelper(200, 40, 0xcccccc, 0xdddddd));\n" +
                "            scene.add(group);\n" +
                "\n" +
                "            (plan.walls || []).forEach(w => createWallWithOpenings(w, height, wallColor));\n" +
                "\n" +
                "            if (Array.isArray(furniture)) {\n" +
                "                furniture.forEach((f, index) => {\n" +
                "                    const geo = new THREE.BoxGeometry(f.w * SCALE, f.h * SCALE, f.d * SCALE);\n" +
                "                    const mat = new THREE.MeshStandardMaterial({ color: 0x4ABAED });\n" +
                "                    const mesh = new THREE.Mesh(geo, mat);\n" +
                "                    mesh.position.set(f.x * SCALE, (f.h*SCALE)/2, f.y * SCALE);\n" +
                "                    group.add(mesh);\n" +
                "                    furnitureArray.push(mesh); // save ref for later\n" +
                "                });\n" +
                "            }\n" +
                "\n" +
                "            setTimeout(resetCamera, 100);\n" +
                "            animate();\n" +
                "        }\n" +
                "\n" +
                "        function createWallWithOpenings(w, h, color) {\n" +
                "            const dx = (w.x2 - w.x1) * SCALE, dz = (w.y2 - w.y1) * SCALE;\n" +
                "            const len = Math.sqrt(dx*dx + dz*dz);\n" +
                "            const geo = new THREE.BoxGeometry(len, h * SCALE, 0.15 * SCALE);\n" +
                "            const mat = new THREE.MeshStandardMaterial({ color: color });\n" +
                "            const wall = new THREE.Mesh(geo, mat);\n" +
                "            wall.position.set((w.x1 * SCALE) + dx/2, (h * SCALE)/2, (w.y1 * SCALE) + dz/2);\n" +
                "            wall.rotation.y = -Math.atan2(dz, dx);\n" +
                "            group.add(wall);\n" +
                "            wallsArray.push(mat); // Save material ref to change color later\n" +
                "        }\n" +
                "\n" +
                "        // === NEW DYNAMIC FUNCTIONS EXPOSED TO ANDROID ===\n" +
                "        function updateWallColor(newHexColor) {\n" +
                "            wallsArray.forEach(mat => mat.color.set(newHexColor));\n" +
                "        }\n" +
                "\n" +
                "        function updateFurniturePosition(index, newX, newZ) {\n" +
                "            if(furnitureArray[index]) {\n" +
                "                furnitureArray[index].position.set(newX * SCALE, furnitureArray[index].position.y, newZ * SCALE);\n" +
                "            }\n" +
                "        }\n" +
                "        // ================================================\n" +
                "\n" +
                "        function resetCamera() { ... }\n" +
                "        function animate() { requestAnimationFrame(animate); controls.update(); renderer.render(scene, camera); }\n" +
                "    </script>\n" +
                "</body></html>";
    }
}