package com.inania.Anthuria;

import android.content.Intent;
import android.content.SharedPreferences;
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

public class AccountActivity extends AppCompatActivity {

    private FirebaseAuth mAuth;
    private GridLayout   gridProjects;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_account);

        mAuth        = FirebaseAuth.getInstance();
        gridProjects = findViewById(R.id.grid_projects);

        TextView tvEmail  = findViewById(R.id.tv_user_email);
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

    // -------------------------------------------------------------------------
    // Projects grid
    // -------------------------------------------------------------------------

    private void loadProjects() {
        gridProjects.removeAllViews();
        gridProjects.setColumnCount(2);

        List<FloorPlanStorage.PlanEntry> entries = FloorPlanStorage.listAll(this);

        if (entries.isEmpty()) {
            TextView empty = new TextView(this);
            empty.setText("Нет сохранённых чертежей");
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
        // Outer container that fills one grid cell
        FrameLayout cell = new FrameLayout(this);
        GridLayout.LayoutParams lp = new GridLayout.LayoutParams(
                GridLayout.spec(GridLayout.UNDEFINED, 1f),
                GridLayout.spec(GridLayout.UNDEFINED, 1f));
        lp.width  = 0;
        lp.height = dp(140);
        lp.setMargins(dp(6), dp(6), dp(6), dp(6));
        cell.setLayoutParams(lp);

        // Card background
        androidx.cardview.widget.CardView card = new androidx.cardview.widget.CardView(this);
        card.setLayoutParams(new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT));
        card.setRadius(dp(12));
        card.setCardElevation(dp(3));

        // Preview image
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

        // Label overlay (room type + date)
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

        // Click → open in editor
        cell.setOnClickListener(v -> {
            Intent intent = new Intent(this, EditorActivity.class);
            intent.putExtra(EditorActivity.EXTRA_PLAN_ID, entry.id);
            startActivity(intent);
        });

        // Long press → delete confirmation
        cell.setOnLongClickListener(v -> {
            new AlertDialog.Builder(this)
                    .setTitle("Удалить чертёж?")
                    .setMessage(entry.roomType + " · " +
                            new SimpleDateFormat("dd MMM yyyy", Locale.getDefault())
                                    .format(new Date(entry.savedAt)))
                    .setPositiveButton("Удалить", (d, w) -> {
                        FloorPlanStorage.delete(this, entry.id);
                        loadProjects();
                        Toast.makeText(this, "Удалено", Toast.LENGTH_SHORT).show();
                    })
                    .setNegativeButton("Отмена", null)
                    .show();
            return true;
        });

        return cell;
    }

    // -------------------------------------------------------------------------
    // Logout
    // -------------------------------------------------------------------------

    private void confirmLogout() {
        new AlertDialog.Builder(this)
                .setTitle("Выход из аккаунта")
                .setMessage("Вы уверены? Все несохранённые изменения будут потеряны.")
                .setPositiveButton("Выйти", (d, w) -> handleLogout())
                .setNegativeButton("Отмена", null)
                .show();
    }

    /**
     * Signs the user out securely:
     *  1. Invalidates the Firebase session token on the device.
     *  2. Clears any locally-cached session keys from SharedPreferences.
     *  3. Redirects to LoginActivity and clears the back stack so the user
     *     cannot press Back to access protected screens.
     *
     * Saved floor plans are intentionally preserved — they belong to the device,
     * and the next user should not automatically see them; they are only visible
     * from AccountActivity which requires authentication.
     */
    private void handleLogout() {
        mAuth.signOut();

        getSharedPreferences(AnthuriaApp.PREFS_NAME, MODE_PRIVATE)
                .edit()
                .remove("userId")
                .remove("authToken")
                .remove("lastProjectId")
                .apply();

        Toast.makeText(this, "Вы вышли из аккаунта", Toast.LENGTH_SHORT).show();

        Intent intent = new Intent(this, LoginActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }

    // -------------------------------------------------------------------------

    private int dp(int dp) {
        return Math.round(dp * getResources().getDisplayMetrics().density);
    }
}
