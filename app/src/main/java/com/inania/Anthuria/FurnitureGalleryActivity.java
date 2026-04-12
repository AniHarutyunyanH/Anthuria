package com.inania.Anthuria;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import androidx.appcompat.app.AppCompatActivity;
import java.io.File;
import java.util.ArrayList;

public class FurnitureGalleryActivity extends AppCompatActivity {

    private ListView listView;
    private ArrayList<File> modelFiles;
    private ArrayList<String> modelNames;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_furniture_gallery);

        listView = findViewById(R.id.lv_furniture);
        loadSavedModels();

        ArrayAdapter<String> adapter = new ArrayAdapter<>(this,
                android.R.layout.simple_list_item_1, modelNames);
        listView.setAdapter(adapter);

        listView.setOnItemClickListener((parent, view, position, id) -> {
            File selectedFile = modelFiles.get(position);

            Intent intent = new Intent(this, ModelViewerActivity.class);
            // IMPORTANT: Use Uri.fromFile to get the correct file:// prefix
            intent.putExtra("MODEL_URL", Uri.fromFile(selectedFile).toString());
            startActivity(intent);
        });
    }

    private void loadSavedModels() {
        modelFiles = new ArrayList<>();
        modelNames = new ArrayList<>();

        // This must match the path in your AiFurnitureActivity save method
        File folder = new File(getExternalFilesDir(null), "Models");

        if (folder.exists() && folder.isDirectory()) {
            File[] files = folder.listFiles();
            if (files != null) {
                for (File file : files) {
                    if (file.getName().endsWith(".glb")) {
                        modelFiles.add(file);
                        modelNames.add(file.getName());
                    }
                }
            }
        }
    }
}