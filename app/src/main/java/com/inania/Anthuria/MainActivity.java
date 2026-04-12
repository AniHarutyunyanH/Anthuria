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

public class MainActivity extends AppCompatActivity {

    private FirebaseAuth mAuth;
    private BottomNavigationView bottomNavigation;
    private CardView cardAdd, cardRooms, cardFurniture, cardArchitecture;
    private EditText searchBar;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);  // ← изменил здесь

        mAuth = FirebaseAuth.getInstance();

        // Находим views БЕЗОПАСНО
        bottomNavigation = findViewById(R.id.bottom_navigation);
        searchBar = findViewById(R.id.search_bar);
        cardAdd = findViewById(R.id.card_add);
        cardRooms = findViewById(R.id.card_rooms);
        cardFurniture = findViewById(R.id.card_furniture);
        cardArchitecture = findViewById(R.id.card_architecture);


        // Проверяем, что основные элементы найдены
        if (bottomNavigation == null) {
            Toast.makeText(this, "Ошибка: bottom_navigation не найден в activity_main.xml", Toast.LENGTH_LONG).show();
            finish();
            return;
        }
        searchBar = findViewById(R.id.search_bar);
        bottomNavigation = findViewById(R.id.bottom_navigation);

        // 2. Логика кликов по папкам
        cardAdd.setOnClickListener(v -> {
            Toast.makeText(this, "Opening New Project...", Toast.LENGTH_SHORT).show();
            // Здесь будет: startActivity(new Intent(this, AddProjectActivity.class));
        });

        cardRooms.setOnClickListener(v -> {
            String[] options = {"Сканировать чертеж", "Рисовать вручную"};

            new androidx.appcompat.app.AlertDialog.Builder(this)
                    .setTitle("Выберите способ")
                    .setItems(options, (dialog, which) -> {
                        if (which == 0) {
                            // Выбор сканирования (откроем галерею/камеру)
                            openImagePicker();
                        } else {
                            // Просто рисование
                            Intent intent = new Intent(MainActivity.this, EditorActivity.class);
                            startActivity(intent);
                        }
                    })
                    .show();
        });

        cardFurniture.setOnClickListener(v -> {
            // Первый уровень: Посмотреть или Добавить
            String[] options = {"Посмотреть мебель", "Добавить мебель..."};

            new androidx.appcompat.app.AlertDialog.Builder(this)
                    .setTitle("Мебель")
                    .setItems(options, (dialog, which) -> {
                        if (which == 0) {
                            // Переход в каталог (простая активность со списком)
                            startActivity(new Intent(this, FurnitureGalleryActivity.class));
                        } else {
                            // Второй уровень: Поиск или Генерация
                            showAddFurnitureDialog();
                        }
                    })
                    .show();
        });



        cardArchitecture.setOnClickListener(v -> {
            Toast.makeText(this, "House Plans...", Toast.LENGTH_SHORT).show();
        });

        // 3. Логика нижней навигации
        bottomNavigation.setOnNavigationItemSelectedListener(new BottomNavigationView.OnNavigationItemSelectedListener() {
            @Override
            public boolean onNavigationItemSelected(@NonNull MenuItem item) {
                int id = item.getItemId();

                if (id == R.id.nav_home) {
                    // Мы уже на главном экране
                    return true;
                } else if (id == R.id.nav_favorites) {
                    Toast.makeText(MainActivity.this, "Favorites clicked", Toast.LENGTH_SHORT).show();
                    startActivity(new Intent(MainActivity.this, FavoritesActivity.class));
                    return true;
                } // Inside onCreate, find this section:
                else if (id == R.id.nav_account) {
                    // CHANGE THIS: Add a check to see if getCurrentUser() is null
                    if (mAuth.getCurrentUser() != null) {
                        Toast.makeText(MainActivity.this, "User: " + mAuth.getCurrentUser().getEmail(), Toast.LENGTH_LONG).show();
                        startActivity(new Intent(MainActivity.this, AccountActivity.class));

                    } else {
                        // If null, show a safe message instead of crashing
                        Toast.makeText(MainActivity.this, "Error: User not logged in", Toast.LENGTH_SHORT).show();

                        // Optionally, send them back to login
                        startActivity(new Intent(MainActivity.this, LoginActivity.class));
                        finish();
                    }
                    return true;

                } else if (id == R.id.nav_settings) {
                    Toast.makeText(MainActivity.this, "Settings clicked", Toast.LENGTH_SHORT).show();
                    startActivity(new Intent(MainActivity.this, SettingActivity.class));
                    return true;
                }
                return false;
            }
        });
    }

    // Метод для выбора изображения
    private void openImagePicker() {
        Intent intent = new Intent(Intent.ACTION_PICK);
        intent.setType("image/*");
        startActivityForResult(intent, 101);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == 101 && resultCode == RESULT_OK && data != null) {
            // Передаем путь к фото в редактор
            Intent intent = new Intent(this, EditorActivity.class);
            intent.putExtra("imageUri", data.getData().toString());
            startActivity(intent);
        }
    }
    // Метод для второго уровня меню
    private void showAddFurnitureDialog() {
        String[] addOptions = {"Найти в магазине", "Сгенерировать по тексту или фото (AI)"};

        new androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle("Добавить мебель")
                .setItems(addOptions, (dialog, which) -> {
                    if (which == 0) {
                        // Логика поиска в магазине (например, WebView или API)
                        Toast.makeText(this, "Поиск в магазине...", Toast.LENGTH_SHORT).show();
                    } else {
                        // ПЕРЕХОД К ИИ (нашу новую активность)
                        startActivity(new Intent(this, AiFurnitureActivity.class));
                    }
                })
                .show();
    }
    // Проверка: если пользователь не залогинен, отправляем его обратно на LoginActivity
    @Override
    protected void onStart() {
        super.onStart();
        if (mAuth.getCurrentUser() == null) {
            startActivity(new Intent(MainActivity.this, LoginActivity.class));
            finish();
        }
    }
}