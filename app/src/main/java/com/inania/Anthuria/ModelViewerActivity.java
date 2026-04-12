package com.inania.Anthuria;

import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import com.google.ar.sceneform.Node;
import com.google.ar.sceneform.SceneView;
import com.google.ar.sceneform.math.Vector3;
import com.google.ar.sceneform.rendering.ModelRenderable;

import java.io.File;

public class ModelViewerActivity extends AppCompatActivity {
    private SceneView sceneView;
    private ProgressBar progressBar;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_model_viewer); // Create this XML next

        sceneView = findViewById(R.id.scene_view_viewer);
        progressBar = findViewById(R.id.progress_bar_viewer);

        String modelUrl = getIntent().getStringExtra("MODEL_URL");

        if (modelUrl != null) {
            loadModel(modelUrl);
        } else {
            Toast.makeText(this, "Error: No Model URL", Toast.LENGTH_SHORT).show();
            finish();
        }
    }

    private void loadModel(String pathOrUrl) {
        Uri uri;
        if (pathOrUrl.startsWith("http")) {
            uri = Uri.parse(pathOrUrl);
        } else {
            // It's a local file path
            uri = Uri.fromFile(new File(pathOrUrl));
        }

        ModelRenderable.builder()
                .setSource(this, uri)
                .setIsFilamentGltf(true)
                .build()
                .thenAccept(renderable -> {
                    progressBar.setVisibility(View.GONE);
                    Node modelNode = new Node();
                    modelNode.setRenderable(renderable);
                    // Move the model back so the camera can see it
                    modelNode.setLocalPosition(new Vector3(0f, 0f, -1.0f));
                    sceneView.getScene().addChild(modelNode);
                })
                .exceptionally(throwable -> {
                    progressBar.setVisibility(View.GONE);
                    Toast.makeText(this, "Failed to load model: " + throwable.getMessage(), Toast.LENGTH_LONG).show();
                    return null;
                });

        ModelRenderable.builder()
                .setSource(this, uri)
                .setIsFilamentGltf(true)
                .build()
                .thenAccept(renderable -> {
                    // ... same rendering logic as before ...
                });
    }
    // Inside ModelViewerActivity.java


    // CRITICAL: SceneView needs lifecycle management to render
    @Override
    protected void onResume() {
        super.onResume();
        try {
            sceneView.resume();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        sceneView.pause();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        sceneView.destroy();
    }
}