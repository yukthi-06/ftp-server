package com.vypeensoft.ftpserver;

import android.os.Environment;
import com.vypeensoft.ftpserver.Log;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;

public class SettingsManager {
    private static final String TAG = "SettingsManager";
    
    // Path specified by requirements: /sdcard/Vypeensoft/FTP_Server/settings/settings.json
    private static final String REQUIRED_PATH = "/sdcard/Vypeensoft/FTP_Server/settings/settings.json";

    private static Settings sCachedSettings = null;
    
    public static class Settings {
        public boolean server_enabled = false;
        public int port = 2121;
        public String username = "admin";
        public String password = "admin";
        public boolean anonymous_login = false;
        public boolean read_only = false;
        public boolean passive_mode = true;
        public String root_directory = "/sdcard/";
        public boolean auto_start = false;
        public int max_connections = 10;
        public boolean show_notification = true;
        public boolean dark_theme = false;
    }

    private static File getSettingsFile() {
        // Try the required exact absolute path first
        File file = new File(REQUIRED_PATH);
        File parent = file.getParentFile();
        if (parent != null && !parent.exists()) {
            boolean created = parent.mkdirs();
            Log.d(TAG, "Created directory chain: " + parent.getAbsolutePath() + " -> " + created);
        }
        
        // If we can't write to the direct path, use Environment.getExternalStorageDirectory() as backup
        if (parent != null && !parent.canWrite()) {
            File externalDir = Environment.getExternalStorageDirectory();
            File backupParent = new File(externalDir, "Vypeensoft/FTP_Server/settings");
            if (!backupParent.exists()) {
                backupParent.mkdirs();
            }
            file = new File(backupParent, "settings.json");
        }
        return file;
    }

    public static synchronized Settings loadSettings() {
        if (sCachedSettings != null) {
            return sCachedSettings;
        }

        File file = getSettingsFile();
        Gson gson = new Gson();
        if (!file.exists()) {
            Log.d(TAG, "Settings file does not exist. Creating default settings.");
            Settings defaults = new Settings();
            saveSettings(defaults);
            sCachedSettings = defaults;
            return defaults;
        }

        try (FileReader reader = new FileReader(file)) {
            Settings settings = gson.fromJson(reader, Settings.class);
            if (settings == null) {
                Log.e(TAG, "Parsing settings returned null. Creating defaults.");
                Settings defaults = new Settings();
                saveSettings(defaults);
                sCachedSettings = defaults;
                return defaults;
            }
            // Simple validation
            validateSettings(settings);
            sCachedSettings = settings;
            return settings;
        } catch (Exception e) {
            Log.e(TAG, "Error loading settings, recreating defaults: " + e.getMessage());
            Settings defaults = new Settings();
            saveSettings(defaults);
            sCachedSettings = defaults;
            return defaults;
        }
    }

    public static synchronized void saveSettings(final Settings settings) {
        sCachedSettings = settings;
        new Thread(() -> {
            synchronized (SettingsManager.class) {
                File file = getSettingsFile();
                // Ensure folder structure exists
                File parent = file.getParentFile();
                if (parent != null && !parent.exists()) {
                    parent.mkdirs();
                }

                Gson gson = new GsonBuilder().setPrettyPrinting().create();
                try (FileWriter writer = new FileWriter(file)) {
                    gson.toJson(settings, writer);
                    Log.d(TAG, "Settings saved asynchronously to: " + file.getAbsolutePath());
                } catch (IOException e) {
                    Log.e(TAG, "Failed to save settings: " + e.getMessage());
                }
            }
        }).start();
    }

    private static void validateSettings(Settings settings) {
        if (settings.port <= 1024 || settings.port > 65535) {
            settings.port = 2121;
        }
        if (settings.username == null || settings.username.trim().isEmpty()) {
            settings.username = "admin";
        }
        if (settings.password == null || settings.password.trim().isEmpty()) {
            settings.password = "admin";
        }
        if (settings.root_directory == null || settings.root_directory.trim().isEmpty()) {
            settings.root_directory = "/sdcard/";
        }
        if (settings.max_connections <= 0) {
            settings.max_connections = 10;
        }
    }
}
