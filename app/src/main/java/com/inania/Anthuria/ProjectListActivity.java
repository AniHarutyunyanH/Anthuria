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
import androidx.appcompat.app.AlertDialog;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class ProjectListActivity extends BaseActivity {

    private RecyclerView   rvProjects;
    private ProjectAdapter adapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_project_list);

        findViewById(R.id.btn_back).setOnClickListener(v -> finish());

        rvProjects = findViewById(R.id.rvProjects);
        rvProjects.setLayoutManager(new GridLayoutManager(this, 2));

        loadProjects();
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadProjects();
    }

    private void loadProjects() {
        List<FloorPlanStorage.PlanEntry> projects = FloorPlanStorage.listAll(this);
        View emptyView = findViewById(R.id.tv_empty);
        if (emptyView != null) {
            emptyView.setVisibility(projects.isEmpty() ? View.VISIBLE : View.GONE);
        }
        adapter = new ProjectAdapter(projects);
        rvProjects.setAdapter(adapter);
    }

    private void openProject(FloorPlanStorage.PlanEntry entry) {
        Intent intent = new Intent(this, EditorActivity.class);
        intent.putExtra(EditorActivity.EXTRA_PLAN_ID, entry.id);
        startActivity(intent);
    }

    private void confirmDelete(FloorPlanStorage.PlanEntry entry) {
        String date = new SimpleDateFormat("dd MMM yyyy", Locale.getDefault())
                .format(new Date(entry.savedAt));
        new AlertDialog.Builder(this)
                .setTitle(R.string.delete_plan_q)
                .setMessage(entry.roomType + " · " + date)
                .setPositiveButton(R.string.delete, (d, w) -> {
                    FloorPlanStorage.delete(this, entry.id);
                    loadProjects();
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    // -------------------------------------------------------------------------

    private class ProjectAdapter extends RecyclerView.Adapter<ProjectAdapter.ViewHolder> {

        private final List<FloorPlanStorage.PlanEntry> projects;

        ProjectAdapter(List<FloorPlanStorage.PlanEntry> projects) {
            this.projects = projects;
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_project_card, parent, false);
            return new ViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            FloorPlanStorage.PlanEntry entry = projects.get(position);

            Bitmap bmp = FloorPlanStorage.loadPreview(ProjectListActivity.this, entry.id);
            if (bmp != null) {
                holder.imgPreview.setImageBitmap(bmp);
            } else {
                holder.imgPreview.setImageResource(android.R.drawable.ic_menu_report_image);
                holder.imgPreview.setBackgroundColor(0xFFF0F0F0);
            }

            holder.tvRoomType.setText(entry.roomType);
            holder.tvDate.setText(
                    new SimpleDateFormat("dd.MM.yy", Locale.getDefault())
                            .format(new Date(entry.savedAt)));

            holder.itemView.setOnClickListener(v -> openProject(entry));
            holder.itemView.setOnLongClickListener(v -> {
                confirmDelete(entry);
                return true;
            });
        }

        @Override
        public int getItemCount() {
            return projects.size();
        }

        class ViewHolder extends RecyclerView.ViewHolder {
            final ImageView imgPreview;
            final TextView  tvRoomType;
            final TextView  tvDate;

            ViewHolder(View itemView) {
                super(itemView);
                imgPreview = itemView.findViewById(R.id.img_preview);
                tvRoomType = itemView.findViewById(R.id.tv_room_type);
                tvDate     = itemView.findViewById(R.id.tv_date);
            }
        }
    }
}
