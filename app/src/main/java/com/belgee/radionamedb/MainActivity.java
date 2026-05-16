package com.belgee.radionamedb;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.Settings;
import android.text.InputType;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.tabs.TabLayout;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;

public class MainActivity extends AppCompatActivity {

    private StationsDB db = new StationsDB();
    private StationsAdapter adapter;
    private RecyclerView recyclerView;
    private TextView statusText;
    private TabLayout tabLayout;
    private boolean showingAm = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        statusText = findViewById(R.id.statusText);
        recyclerView = findViewById(R.id.recyclerView);
        tabLayout = findViewById(R.id.tabLayout);

        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        adapter = new StationsAdapter(new ArrayList<>(), new StationsAdapter.Listener() {
            @Override public void onEdit(Station s) { showEditDialog(s); }
            @Override public void onDelete(Station s) { confirmDelete(s); }
        });
        recyclerView.setAdapter(adapter);

        tabLayout.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
            @Override public void onTabSelected(TabLayout.Tab tab) {
                showingAm = tab.getPosition() == 1;
                refreshList();
            }
            @Override public void onTabUnselected(TabLayout.Tab tab) {}
            @Override public void onTabReselected(TabLayout.Tab tab) {}
        });

        findViewById(R.id.fab).setOnClickListener(v -> showAddDialog());

        findViewById(R.id.btnLoadRegion).setOnClickListener(v -> showRegionsDialog());
        findViewById(R.id.btnSave).setOnClickListener(v -> saveDatabase());
        findViewById(R.id.btnClear).setOnClickListener(v -> confirmClear());

        ensureStoragePermission();
        db.load();
        refreshList();
    }

    /**
     * Автоматически сохранять базу после любого изменения.
     * Это критично — модуль hook читает файл раз в 30 сек,
     * и пользователь не должен вручную нажимать "Сохранить".
     */
    private void autoSave() {
        if (!db.save()) {
            Toast.makeText(this,
                "⚠ Не могу сохранить файл. Проверь разрешение на доступ к файлам.",
                Toast.LENGTH_LONG).show();
        }
    }

    private void ensureStoragePermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (!Environment.isExternalStorageManager()) {
                Toast.makeText(this, "Нужен доступ ко всем файлам — открываю настройки",
                    Toast.LENGTH_LONG).show();
                try {
                    startActivity(new android.content.Intent(
                        Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                        android.net.Uri.parse("package:" + getPackageName())));
                } catch (Exception e) {
                    startActivity(new android.content.Intent(
                        Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION));
                }
            }
        }
    }

    private void refreshList() {
        List<Station> filtered = new ArrayList<>();
        for (Station s : db.getStations()) {
            if (s.isAm == showingAm) filtered.add(s);
        }
        adapter.setStations(filtered);
        updateStatus();
    }

    private void updateStatus() {
        int fm = 0, am = 0;
        for (Station s : db.getStations()) {
            if (s.isAm) am++; else fm++;
        }
        statusText.setText("FM: " + fm + "   AM: " + am
            + "   |   Файл: " + StationsDB.JSON_PATH);
    }

    private void showAddDialog() {
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        int p = (int) (16 * getResources().getDisplayMetrics().density);
        layout.setPadding(p, p, p, 0);

        TextInputEditText freqInput = new TextInputEditText(this);
        freqInput.setHint(showingAm ? "Частота (например 675)" : "Частота (например 103.4)");
        freqInput.setInputType(InputType.TYPE_CLASS_NUMBER
            | (showingAm ? 0 : InputType.TYPE_NUMBER_FLAG_DECIMAL));
        layout.addView(freqInput);

        TextInputEditText nameInput = new TextInputEditText(this);
        nameInput.setHint("Название станции");
        layout.addView(nameInput);

        new AlertDialog.Builder(this)
            .setTitle(showingAm ? "Добавить AM-станцию" : "Добавить FM-станцию")
            .setView(layout)
            .setPositiveButton("Добавить", (d, w) -> {
                String freq = freqInput.getText().toString().trim();
                String name = nameInput.getText().toString().trim();
                if (freq.isEmpty() || name.isEmpty()) {
                    Toast.makeText(this, "Заполни оба поля", Toast.LENGTH_SHORT).show();
                    return;
                }
                db.addStation(freq, name, showingAm);
                autoSave();
                refreshList();
            })
            .setNegativeButton("Отмена", null)
            .show();
    }

    private void showEditDialog(Station station) {
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        int p = (int) (16 * getResources().getDisplayMetrics().density);
        layout.setPadding(p, p, p, 0);

        TextInputEditText freqInput = new TextInputEditText(this);
        freqInput.setHint(station.isAm ? "Частота (например 675)" : "Частота (например 103.4)");
        freqInput.setInputType(InputType.TYPE_CLASS_NUMBER
            | (station.isAm ? 0 : InputType.TYPE_NUMBER_FLAG_DECIMAL));
        freqInput.setText(station.freq);
        layout.addView(freqInput);

        TextInputEditText nameInput = new TextInputEditText(this);
        nameInput.setHint("Название станции");
        nameInput.setText(station.name);
        layout.addView(nameInput);

        new AlertDialog.Builder(this)
            .setTitle("Изменить станцию")
            .setView(layout)
            .setPositiveButton("Сохранить", (d, w) -> {
                String newFreq = freqInput.getText().toString().trim();
                String newName = nameInput.getText().toString().trim();
                if (newFreq.isEmpty() || newName.isEmpty()) {
                    Toast.makeText(this, "Заполни оба поля", Toast.LENGTH_SHORT).show();
                    return;
                }
                // Удалить старую и добавить новую (на случай если частота поменялась)
                db.removeStation(station.freq, station.isAm);
                db.addStation(newFreq, newName, station.isAm);
                autoSave();
                refreshList();
            })
            .setNegativeButton("Отмена", null)
            .show();
    }

    private void confirmDelete(Station station) {
        new AlertDialog.Builder(this)
            .setTitle("Удалить станцию?")
            .setMessage(station.displayFreq() + " — " + station.name)
            .setPositiveButton("Удалить", (d, w) -> {
                db.removeStation(station.freq, station.isAm);
                autoSave();
                refreshList();
            })
            .setNegativeButton("Отмена", null)
            .show();
    }

    private void showRegionsDialog() {
        try {
            InputStream is = getAssets().open("regions/index.json");
            BufferedReader r = new BufferedReader(new InputStreamReader(is));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = r.readLine()) != null) sb.append(line);
            r.close();
            JSONObject root = new JSONObject(sb.toString());
            JSONArray arr = root.getJSONArray("regions");

            String[] names = new String[arr.length()];
            String[] files = new String[arr.length()];
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.getJSONObject(i);
                names[i] = o.getString("name");
                files[i] = o.getString("file");
            }

            new AlertDialog.Builder(this)
                .setTitle("Выбери регион")
                .setItems(names, (d, which) -> {
                    askMergeOrReplace(names[which], files[which]);
                })
                .show();
        } catch (Exception e) {
            Toast.makeText(this, "Ошибка загрузки регионов: " + e.getMessage(),
                Toast.LENGTH_LONG).show();
        }
    }

    private void askMergeOrReplace(String regionName, String assetPath) {
        new AlertDialog.Builder(this)
            .setTitle(regionName)
            .setMessage("Как загрузить станции?")
            .setPositiveButton("Добавить к существующим", (d, w) -> {
                db.mergeFromAssets(this, assetPath);
                autoSave();
                refreshList();
                Toast.makeText(this, "Станции добавлены", Toast.LENGTH_SHORT).show();
            })
            .setNeutralButton("Заменить полностью", (d, w) -> {
                db.replaceFromAssets(this, assetPath);
                autoSave();
                refreshList();
                Toast.makeText(this, "База заменена", Toast.LENGTH_SHORT).show();
            })
            .setNegativeButton("Отмена", null)
            .show();
    }

    private void saveDatabase() {
        if (db.save()) {
            Toast.makeText(this, "Сохранено: " + db.size() + " станций",
                Toast.LENGTH_SHORT).show();
        } else {
            Toast.makeText(this,
                "Ошибка сохранения. Дай приложению доступ к файлам.",
                Toast.LENGTH_LONG).show();
        }
    }

    private void confirmClear() {
        new AlertDialog.Builder(this)
            .setTitle("Очистить базу?")
            .setMessage("Удалить все станции?")
            .setPositiveButton("Очистить", (d, w) -> {
                db.clear();
                autoSave();
                refreshList();
            })
            .setNegativeButton("Отмена", null)
            .show();
    }
}
