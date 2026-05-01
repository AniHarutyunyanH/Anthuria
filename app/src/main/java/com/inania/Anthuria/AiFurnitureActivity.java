package com.inania.Anthuria;
import android.content.Intent;


import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.provider.MediaStore;
import android.util.Log;
import android.view.View;
import android.widget.*;
import androidx.appcompat.app.AppCompatActivity;

import com.google.ar.sceneform.Node;
import com.google.ar.sceneform.SceneView;
import com.google.ar.sceneform.math.Vector3;
import com.google.ar.sceneform.rendering.ModelRenderable;

import org.json.JSONObject;

import java.io.File;

import okhttp3.MediaType;
import okhttp3.RequestBody;
import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

public class AiFurnitureActivity extends BaseActivity {

    // ВАЖНО: Храним только "чистый" ключ
    private final String MY_API_KEY = "tsk_E98jLnAbffSOAp8WSgztiojeskyJ-K96stwavDw8aqS";

    private FrameLayout sceneContainer;
    private SceneView sceneView;
    private Button btnAccept;
    private ImageView imgPreview;
    private EditText etPrompt, etWidth, etHeight, etDepth;
    private Button btnGenerate, btnUpload;
    private ProgressBar progressBar;
    private Uri selectedImageUri;
    private TripoApi tripoApi;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_ai_furniture);

        initUI();

        Retrofit retrofit = new Retrofit.Builder()
                .baseUrl("https://api.tripo3d.ai/")
                .addConverterFactory(GsonConverterFactory.create())
                .build();
        tripoApi = retrofit.create(TripoApi.class);

        btnUpload.setOnClickListener(v -> {
            Intent intent = new Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
            startActivityForResult(intent, 102);
        });

        btnGenerate.setOnClickListener(v -> startProcess());
    }

    private void initUI() {
        sceneContainer = findViewById(R.id.scene_container);
        sceneView = findViewById(R.id.scene_view);
        btnAccept = findViewById(R.id.btn_accept);
        imgPreview = findViewById(R.id.img_preview);
        etPrompt = findViewById(R.id.et_prompt);
        etWidth = findViewById(R.id.et_width);
        etHeight = findViewById(R.id.et_height);
        etDepth = findViewById(R.id.et_depth);
        btnGenerate = findViewById(R.id.btn_generate);
        btnUpload = findViewById(R.id.btn_upload);
        progressBar = findViewById(R.id.progress_bar);
    }

    private void startProcess() {
        String promptText = etPrompt.getText().toString().trim();
        if (promptText.isEmpty()) return;

        progressBar.setVisibility(View.VISIBLE);
        btnGenerate.setEnabled(false);

        // --- NEW CORRECT CODE ---
        java.util.Map<String, Object> mainMap = new java.util.HashMap<>();
        mainMap.put("type", "text_to_model");
        mainMap.put("prompt", promptText); // Put prompt directly in the main map

// Optional: You can also add a negative prompt as shown in the docs
// mainMap.put("negative_prompt", "low quality, blurry");

        // AUTH HEADER
        String cleanKey = MY_API_KEY.replace("Bearer ", "").trim();
        String authHeader = "Bearer " + cleanKey;

        tripoApi.createTask(authHeader, mainMap).enqueue(new Callback<ResponseBody>() {
            @Override
            public void onResponse(Call<ResponseBody> call, Response<ResponseBody> response) {
                if (response.isSuccessful() && response.body() != null) {
                    try {
                        String jsonResponse = response.body().string();
                        JSONObject res = new JSONObject(jsonResponse);
                        String taskId = res.getJSONObject("data").getString("task_id");
                        checkStatusLoop(taskId);
                    } catch (Exception e) {
                        showError("Response Error: " + e.getMessage());
                    }
                } else {
                    try {
                        // IF IT STILL FAILS, READ THE LOGCAT NOW
                        String errorBody = response.errorBody().string();
                        android.util.Log.e("TRIPO_CRITICAL", "CODE: " + response.code() + " BODY: " + errorBody);
                        showError("1004 Error: Check Logcat");
                    } catch (Exception e) { e.printStackTrace(); }
                }
            }

            @Override
            public void onFailure(Call<ResponseBody> call, Throwable t) {
                showError("Network failed: " + t.getMessage());
            }
        });
    }
    private void checkStatusLoop(String taskId) {
        String authHeader = "Bearer " + MY_API_KEY.replace("Bearer ", "").trim();

        tripoApi.getTaskStatus(authHeader, taskId).enqueue(new Callback<ResponseBody>() {
            @Override
            public void onResponse(Call<ResponseBody> call, Response<ResponseBody> response) {
                try {
                    if (response.isSuccessful() && response.body() != null) {
                        JSONObject res = new JSONObject(response.body().string());
                        JSONObject data = res.getJSONObject("data");
                        String status = data.getString("status");

                        if (status.equals("success")) {
                            JSONObject output = data.optJSONObject("output");
                            if (output != null) {
                                String modelUrl = output.getString("model_url");
                                display3DModel(modelUrl);
                            }
                        } else if (status.equals("failed")) {
                            showError("Generation failed on server");
                        } else {
                            // Рекурсивный вызов через 5 секунд
                            new Handler().postDelayed(() -> checkStatusLoop(taskId), 5000);
                        }
                    }
                } catch (Exception e) {
                    showError("Status error: " + e.getMessage());
                }
            }

            @Override
            public void onFailure(Call<ResponseBody> call, Throwable t) {
                showError("Status check failed");
            }
        });
    }

    private void display3DModel(String url) {
        runOnUiThread(() -> {
            progressBar.setVisibility(View.GONE);
            imgPreview.setVisibility(View.GONE);
            btnGenerate.setVisibility(View.VISIBLE);
            btnGenerate.setEnabled(true);
            sceneContainer.setVisibility(View.VISIBLE);

            // Load the 3D Model (.glb)
            ModelRenderable.builder()
                    .setSource(this, Uri.parse(url))
                    .setIsFilamentGltf(true) // Crucial for Tripo's .glb format
                    .build()
                    .thenAccept(renderable -> {
                        // Remove previous models but NOT the camera/lighting
                        for (com.google.ar.sceneform.Node node : new java.util.ArrayList<>(sceneView.getScene().getChildren())) {
                            if (node.getRenderable() != null) {
                                sceneView.getScene().removeChild(node);
                            }
                        }

                        // Create a new node for the model
                        Node modelNode = new Node();
                        modelNode.setRenderable(renderable);

                        // Position the model: x=0, y=0, z=-1.0 (1 meter in front of camera)
                        modelNode.setLocalPosition(new Vector3(0f, 0f, -1.0f));

                        // Add to scene
                        sceneView.getScene().addChild(modelNode);

                        Log.d("TRIPO_DEBUG", "Model rendered successfully from: " + url);
                    })
                    .exceptionally(throwable -> {
                        Log.e("TRIPO_DEBUG", "Render error: " + throwable.getMessage());
                        Toast.makeText(this, "3D Error: " + throwable.getMessage(), Toast.LENGTH_LONG).show();
                        return null;
                    });

            btnAccept.setOnClickListener(v -> {
                saveModelToDevice(url, "furniture_" + System.currentTimeMillis());
                Intent intent = new Intent();
                intent.putExtra("MODEL_URL", url);
                setResult(RESULT_OK, intent);
                finish();
            });
        });
    }
    @Override
    protected void onResume() {
        super.onResume();
        try {
            if (sceneView != null) {
                sceneView.resume(); // This "wakes up" the 3D engine
            }
        } catch (Exception e) {
            Log.e("TRIPO_DEBUG", "SceneView resume error: " + e.getMessage());
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (sceneView != null) {
            sceneView.pause(); // Stop rendering to save battery
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (sceneView != null) {
            sceneView.destroy(); // Release GPU resources
        }
    }
    private void showError(String msg) {
        runOnUiThread(() -> {
            progressBar.setVisibility(View.GONE);
            btnGenerate.setEnabled(true);
            Toast.makeText(this, msg, Toast.LENGTH_LONG).show();
        });
    }
    private void saveModelToDevice(String url, String fileName) {
        okhttp3.OkHttpClient client = new okhttp3.OkHttpClient();
        okhttp3.Request request = new okhttp3.Request.Builder().url(url).build();

        client.newCall(request).enqueue(new okhttp3.Callback() {
            @Override
            public void onFailure(okhttp3.Call call, java.io.IOException e) {
                Log.e("SAVE_MODEL", "Download failed: " + e.getMessage());
            }

            @Override
            public void onResponse(okhttp3.Call call, okhttp3.Response response) throws java.io.IOException {
                if (!response.isSuccessful()) return;

                File folder = new File(getExternalFilesDir(null), "Models");
                if (!folder.exists()) folder.mkdirs();

                File file = new File(folder, fileName + ".glb");

                // Use a BufferedSink for safer writing of large 3D files
                try (java.io.FileOutputStream fos = new java.io.FileOutputStream(file)) {
                    fos.write(response.body().bytes());
                    Log.d("SAVE_MODEL", "Successfully saved to: " + file.getAbsolutePath());

                    // Optional: Notify the user only after it's DONE
                    runOnUiThread(() -> Toast.makeText(AiFurnitureActivity.this,
                            "Furniture saved to Gallery!", Toast.LENGTH_SHORT).show());
                }
            }
        });
    }
}