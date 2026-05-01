package com.inania.Anthuria;

import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.os.Bundle;
import android.view.Gravity;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.gridlayout.widget.GridLayout;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class AccountActivity extends BaseActivity {

    private FirebaseAuth mAuth;
    private GridLayout   gridProjects;
    private TextView     tvProjectCount;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_account);

        mAuth          = FirebaseAuth.getInstance();
        gridProjects   = findViewById(R.id.grid_projects);
        tvProjectCount = findViewById(R.id.tv_project_count);

        TextView tvEmail   = findViewById(R.id.tv_user_email);
        Button   btnLogout = findViewById(R.id.btn_logout);

        FirebaseUser user = mAuth.getCurrentUser();
        if (user != null) tvEmail.setText(user.getEmail());

        btnLogout.setOnClickListener(v -> confirmLogout());
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadProjects();
    }

    private void loadProjects() {
        gridProjects.removeAllViews();
        gridProjects.setColumnCount(2);

        List<FloorPlanStorage.PlanEntry> entries = FloorPlanStorage.listAll(this);

        if (tvProjectCount != null) {
            tvProjectCount.setText(String.valueOf(entries.size()));
        }

        if (entries.isEmpty()) {
            TextView empty = new TextView(this);
            empty.setText(R.string.no_saved_plans);
            empty.setTextColor(Color.GRAY);
            empty.setGravity(Gravity.CENTER);
            empty.setPadding(dp(16), dp(32), dp(16), dp(32));
            GridLayout.LayoutParams lp = new GridLayout.LayoutParams();
            lp.columnSpec = GridLayout.spec(0, 2, 1f);
            lp.width  = GridLayout.LayoutParams.MATCH_PARENT;
            lp.height = GridLayout.LayoutParams.WRAP_CONTENT;
            empty.setLayoutParams(lp);
            gridProjects.addView(empty);
            return;
        }

        for (FloorPlanStorage.PlanEntry entry : entries) {
            gridProjects.addView(buildProjectCard(entry));
        }
    }

    private android.widget.FrameLayout buildProjectCard(FloorPlanStorage.PlanEntry entry) {
        FrameLayout cell = new FrameLayout(this);
        GridLayout.LayoutParams lp = new GridLayout.LayoutParams(
                GridLayout.spec(GridLayout.UNDEFINED, 1f),
                GridLayout.spec(GridLayout.UNDEFINED, 1f));
        lp.width  = 0;
        lp.height = dp(140);
        lp.setMargins(dp(6), dp(6), dp(6), dp(6));
        cell.setLayoutParams(lp);

        androidx.cardview.widget.CardView card = new androidx.cardview.widget.CardView(this);
        card.setLayoutParams(new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT));
        card.setRadius(dp(12));
        card.setCardElevation(dp(3));

        ImageView imgPreview = new ImageView(this);
        imgPreview.setLayoutParams(new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT));
        imgPreview.setScaleType(ImageView.ScaleType.CENTER_CROP);
        imgPreview.setBackgroundColor(Color.parseColor("#F0F0F0"));

        Bitmap bmp = FloorPlanStorage.loadPreview(this, entry.id);
        if (bmp != null) {
            imgPreview.setImageBitmap(bmp);
        } else {
            imgPreview.setImageResource(android.R.drawable.ic_menu_report_image);
        }
        card.addView(imgPreview);

        TextView label = new TextView(this);
        FrameLayout.LayoutParams labelLp = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.BOTTOM);
        label.setLayoutParams(labelLp);
        label.setPadding(dp(8), dp(4), dp(8), dp(6));
        label.setBackgroundColor(Color.argb(160, 0, 0, 0));
        label.setTextColor(Color.WHITE);
        label.setTextSize(11f);
        String date = new SimpleDateFormat("dd.MM.yy", Locale.getDefault())
                .format(new Date(entry.savedAt));
        label.setText(entry.roomType + "\n" + date);
        card.addView(label);

        cell.addView(card);

        cell.setOnClickListener(v -> {
            Intent intent = new Intent(this, EditorActivity.class);
            intent.putExtra(EditorActivity.EXTRA_PLAN_ID, entry.id);
            startActivity(intent);
        });

        cell.setOnLongClickListener(v -> {
            new AlertDialog.Builder(this)
                    .setTitle(R.string.delete_plan_q)
                    .setMessage(entry.roomType + " · " +
                            new SimpleDateFormat("dd MMM yyyy", Locale.getDefault())
                                    .format(new Date(entry.savedAt)))
                    .setPositiveButton(R.string.delete, (d, w) -> {
                        FloorPlanStorage.delete(this, entry.id);
                        loadProjects();
                    })
                    .setNegativeButton(R.string.cancel, null)
                    .show();
            return true;
        });

        return cell;
    }

    private void confirmLogout() {
        new AlertDialog.Builder(this)
                .setTitle(R.string.logout_confirm_title)
                .setMessage(R.string.logout_confirm_msg)
                .setPositiveButton(R.string.logout, (d, w) -> handleLogout())
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private void handleLogout() {
        mAuth.signOut();

        getSharedPreferences(AnthuriaApp.PREFS_NAME, MODE_PRIVATE)
                .edit()
                .remove("userId")
                .remove("authToken")
                .remove("lastProjectId")
                .apply();

        Toast.makeText(this, R.string.logout_success, Toast.LENGTH_SHORT).show();

        Intent intent = new Intent(this, LoginActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }

    private int dp(int dp) {
        return Math.round(dp * getResources().getDisplayMetrics().density);
    }
}