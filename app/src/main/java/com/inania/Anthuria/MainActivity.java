package com.inania.Anthuria;

import android.content.Intent;
import android.os.Bundle;
import android.view.MenuItem;
import android.widget.EditText;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.cardview.widget.CardView;

import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.firebase.auth.FirebaseAuth;

public class MainActivity extends BaseActivity {

    private FirebaseAuth mAuth;
    private BottomNavigationView bottomNavigation;
    private CardView cardAdd, cardRooms, cardFurniture, cardArchitecture;
    private EditText searchBar;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        mAuth = FirebaseAuth.getInstance();

        bottomNavigation = findViewById(R.id.bottom_navigation);
        searchBar = findViewById(R.id.search_bar);
        cardAdd = findViewById(R.id.card_add);
        cardRooms = findViewById(R.id.card_rooms);
        cardFurniture = findViewById(R.id.card_furniture);
        cardArchitecture = findViewById(R.id.card_architecture);

        if (bottomNavigation == null) {
            finish();
            return;
        }

        cardAdd.setOnClickListener(v -> {
            String[] options = {getString(R.string.scan_blueprint), getString(R.string.draw_manually)};

            new androidx.appcompat.app.AlertDialog.Builder(this)
                    .setTitle(R.string.choose_method)
                    .setItems(options, (dialog, which) -> {
                        if (which == 0) {
                            openImagePicker();
                        } else {
                            Intent intent = new Intent(MainActivity.this, EditorActivity.class);
                            startActivity(intent);
                        }
                    })
                    .show();
        });

        cardRooms.setOnClickListener(v -> {
            startActivity(new Intent(MainActivity.this, ProjectListActivity.class));
        });

        cardFurniture.setOnClickListener(v -> {
            String[] options = {getString(R.string.view_furniture), getString(R.string.add_furniture)};

            new androidx.appcompat.app.AlertDialog.Builder(this)
                    .setTitle(R.string.furniture)
                    .setItems(options, (dialog, which) -> {
                        if (which == 0) {
                            startActivity(new Intent(this, FurnitureGalleryActivity.class));
                        } else {
                            showAddFurnitureDialog();
                        }
                    })
                    .show();
        });

        cardArchitecture.setOnClickListener(v -> {
            Toast.makeText(this, R.string.house_plans, Toast.LENGTH_SHORT).show();
        });

        bottomNavigation.setOnNavigationItemSelectedListener(item -> {
            int id = item.getItemId();

            if (id == R.id.nav_home) {
                return true;
            } else if (id == R.id.nav_favorites) {
                startActivity(new Intent(MainActivity.this, FavoritesActivity.class));
                return true;
            } else if (id == R.id.nav_account) {
                if (mAuth.getCurrentUser() != null) {
                    startActivity(new Intent(MainActivity.this, AccountActivity.class));
                } else {
                    Toast.makeText(MainActivity.this, R.string.error_not_logged_in, Toast.LENGTH_SHORT).show();
                    startActivity(new Intent(MainActivity.this, LoginActivity.class));
                    finish();
                }
                return true;
            } else if (id == R.id.nav_settings) {
                startActivity(new Intent(MainActivity.this, SettingActivity.class));
                return true;
            }
            return false;
        });
    }

    private void openImagePicker() {
        Intent intent = new Intent(Intent.ACTION_PICK);
        intent.setType("image/*");
        startActivityForResult(intent, 101);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == 101 && resultCode == RESULT_OK && data != null) {
            Intent intent = new Intent(this, EditorActivity.class);
            intent.putExtra("imageUri", data.getData().toString());
            startActivity(intent);
        }
    }

    private void showAddFurnitureDialog() {
        String[] addOptions = {getString(R.string.search_in_store), getString(R.string.ai_furniture)};

        new androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle(R.string.add_furniture)
                .setItems(addOptions, (dialog, which) -> {
                    if (which == 0) {
                        Toast.makeText(this, R.string.search_in_store, Toast.LENGTH_SHORT).show();
                    } else {
                        startActivity(new Intent(this, AiFurnitureActivity.class));
                    }
                })
                .show();
    }

    @Override
    protected void onStart() {
        super.onStart();
        if (mAuth.getCurrentUser() == null) {
            startActivity(new Intent(MainActivity.this, LoginActivity.class));
            finish();
        }
    }
}