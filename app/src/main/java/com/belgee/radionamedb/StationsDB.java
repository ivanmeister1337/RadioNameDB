package com.belgee.radionamedb;

import android.content.Context;
import android.util.Log;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;

/**
 * Менеджер базы радиостанций.
 * Читает/пишет /sdcard/RadioNames/stations.json (тот же файл что использует LSPosed модуль).
 */
public class StationsDB {
    private static final String TAG = "StationsDB";
    public static final String JSON_DIR = "/sdcard/RadioNames";
    public static final String JSON_PATH = JSON_DIR + "/stations.json";

    private List<Station> stations = new ArrayList<>();

    /**
     * Загрузить БД с диска (или вернуть пустой список если файла нет).
     */
    public void load() {
        stations.clear();
        File file = new File(JSON_PATH);
        if (!file.exists()) {
            Log.d(TAG, "File not found, returning empty list: " + JSON_PATH);
            return;
        }
        try {
            StringBuilder sb = new StringBuilder();
            try (BufferedReader r = new BufferedReader(new FileReader(file))) {
                String line;
                while ((line = r.readLine()) != null) sb.append(line).append('\n');
            }
            JSONObject root = new JSONObject(sb.toString());
            parseJson(root);
        } catch (Exception e) {
            Log.e(TAG, "Load error", e);
        }
        sortStations();
    }

    /**
     * Загрузить region preset из assets и слить в текущую базу.
     */
    public void mergeFromAssets(Context ctx, String assetPath) {
        try {
            InputStream is = ctx.getAssets().open(assetPath);
            BufferedReader r = new BufferedReader(new InputStreamReader(is));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = r.readLine()) != null) sb.append(line).append('\n');
            r.close();
            JSONObject root = new JSONObject(sb.toString());
            parseJson(root);
            sortStations();
        } catch (Exception e) {
            Log.e(TAG, "mergeFromAssets error", e);
        }
    }

    /**
     * Заменить базу - очистить и загрузить с assets.
     */
    public void replaceFromAssets(Context ctx, String assetPath) {
        stations.clear();
        mergeFromAssets(ctx, assetPath);
    }

    private void parseJson(JSONObject root) throws JSONException {
        if (root.has("fm")) {
            JSONObject fm = root.getJSONObject("fm");
            Iterator<String> keys = fm.keys();
            while (keys.hasNext()) {
                String key = keys.next();
                String name = fm.getString(key);
                // Слияние: если такая частота уже есть, не перетираем (приоритет у того что было)
                if (!hasStation(key, false)) {
                    stations.add(new Station(key, name, false));
                }
            }
        }
        if (root.has("am")) {
            JSONObject am = root.getJSONObject("am");
            Iterator<String> keys = am.keys();
            while (keys.hasNext()) {
                String key = keys.next();
                String name = am.getString(key);
                if (!hasStation(key, true)) {
                    stations.add(new Station(key, name, true));
                }
            }
        }
    }

    /**
     * Сохранить базу на диск в формат который читает LSPosed-модуль.
     */
    public boolean save() {
        try {
            // Гарантируем что директория существует
            File dir = new File(JSON_DIR);
            if (!dir.exists()) dir.mkdirs();

            JSONObject root = new JSONObject();
            JSONObject fm = new JSONObject();
            JSONObject am = new JSONObject();
            for (Station s : stations) {
                if (s.isAm) am.put(s.freq, s.name);
                else fm.put(s.freq, s.name);
            }
            if (fm.length() > 0) root.put("fm", fm);
            if (am.length() > 0) root.put("am", am);

            File f = new File(JSON_PATH);
            try (FileOutputStream out = new FileOutputStream(f)) {
                out.write(root.toString(2).getBytes("UTF-8"));
            }
            Log.d(TAG, "Saved " + stations.size() + " stations to " + JSON_PATH);
            return true;
        } catch (Exception e) {
            Log.e(TAG, "Save error", e);
            return false;
        }
    }

    public List<Station> getStations() { return stations; }

    public boolean hasStation(String freq, boolean isAm) {
        for (Station s : stations) {
            if (s.isAm == isAm && s.freq.equals(freq)) return true;
        }
        return false;
    }

    public void addStation(String freq, String name, boolean isAm) {
        // Удалить дубликат если был
        removeStation(freq, isAm);
        stations.add(new Station(freq, name, isAm));
        sortStations();
    }

    public void removeStation(String freq, boolean isAm) {
        for (Iterator<Station> it = stations.iterator(); it.hasNext(); ) {
            Station s = it.next();
            if (s.isAm == isAm && s.freq.equals(freq)) {
                it.remove();
                return;
            }
        }
    }

    public void clear() {
        stations.clear();
    }

    public int size() { return stations.size(); }

    private void sortStations() {
        Collections.sort(stations, new Comparator<Station>() {
            @Override
            public int compare(Station a, Station b) {
                if (a.isAm != b.isAm) return a.isAm ? 1 : -1; // FM сначала
                try {
                    double fa = Double.parseDouble(a.freq);
                    double fb = Double.parseDouble(b.freq);
                    return Double.compare(fa, fb);
                } catch (Exception e) {
                    return a.freq.compareTo(b.freq);
                }
            }
        });
    }
}
