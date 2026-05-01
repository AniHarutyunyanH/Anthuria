package com.inania.Anthuria;

import android.content.Intent;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;

public class ProjectListActivity extends AppCompatActivity {

    private RecyclerView rvProjects;
    private ProjectAdapter adapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_project_list);

        rvProjects = findViewById(R.id.rvProjects);
        rvProjects.setLayoutManager(new LinearLayoutManager(this));

        loadProjects();
    }

    private void loadProjects() {
        List<FloorPlanStorage.PlanEntry> projects = FloorPlanStorage.listAll(this);
        adapter = new ProjectAdapter(projects);
        rvProjects.setAdapter(adapter);
    }

    private class ProjectAdapter extends RecyclerView.Adapter<ProjectAdapter.ViewHolder> {
        private final List<FloorPlanStorage.PlanEntry> projects;

        ProjectAdapter(List<FloorPlanStorage.PlanEntry> projects) {
            this.projects = projects;
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext()).inflate(android.R.layout.simple_list_item_2, parent, false);
            return new ViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            FloorPlanStorage.PlanEntry project = projects.get(position);
            holder.text1.setText("Project " + project.id);
            holder.text2.setText("Saved at: " + new java.util.Date(project.savedAt).toString());
            
            holder.itemView.setOnClickListener(v -> {
                Intent intent = new Intent(ProjectListActivity.this, EditorActivity.class);
                intent.putExtra("planId", project.id);
                startActivity(intent);
            });
        }

        @Override
        public int getItemCount() {
            return projects.size();
        }

        class ViewHolder extends RecyclerView.ViewHolder {
            TextView text1, text2;
            ViewHolder(View itemView) {
                super(itemView);
                text1 = itemView.findViewById(android.R.id.text1);
                text2 = itemView.findViewById(android.R.id.text2);
            }
        }
    }
}