package com.vypeensoft.ftpserver;

import android.os.Bundle;
import android.view.MenuItem;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.switchmaterial.SwitchMaterial;
import com.google.android.material.textfield.TextInputEditText;

import java.io.File;

public class SettingsActivity extends AppCompatActivity {

    private TextInputEditText editPort;
    private TextInputEditText editMaxConnections;
    private TextInputEditText editUsername;
    private TextInputEditText editPassword;
    private SwitchMaterial switchAnonymous;
    private TextInputEditText editRootDir;
    private SwitchMaterial switchReadOnly;
    private SwitchMaterial switchPassiveMode;
    private SwitchMaterial switchAutoStart;
    private SwitchMaterial switchShowNotification;

    private MaterialButton btnReset;
    private MaterialButton btnSave;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        // Toolbar setup
        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setDisplayShowHomeEnabled(true);
        }

        // Bind views
        editPort = findViewById(R.id.editPort);
        editMaxConnections = findViewById(R.id.editMaxConnections);
        editUsername = findViewById(R.id.editUsername);
        editPassword = findViewById(R.id.editPassword);
        switchAnonymous = findViewById(R.id.switchAnonymous);
        editRootDir = findViewById(R.id.editRootDir);
        switchReadOnly = findViewById(R.id.switchReadOnly);
        switchPassiveMode = findViewById(R.id.switchPassiveMode);
        switchAutoStart = findViewById(R.id.switchAutoStart);
        switchShowNotification = findViewById(R.id.switchShowNotification);

        btnReset = findViewById(R.id.btnReset);
        btnSave = findViewById(R.id.btnSave);

        // Load Settings
        loadSettingsToUI();

        // Listeners
        btnSave.setOnClickListener(v -> saveSettings());
        btnReset.setOnClickListener(v -> resetSettingsToDefault());
    }

    private void loadSettingsToUI() {
        SettingsManager.Settings settings = SettingsManager.loadSettings();

        editPort.setText(String.valueOf(settings.port));
        editMaxConnections.setText(String.valueOf(settings.max_connections));
        editUsername.setText(settings.username);
        editPassword.setText(settings.password);
        switchAnonymous.setChecked(settings.anonymous_login);
        editRootDir.setText(settings.root_directory);
        switchReadOnly.setChecked(settings.read_only);
        switchPassiveMode.setChecked(settings.passive_mode);
        switchAutoStart.setChecked(settings.auto_start);
        switchShowNotification.setChecked(settings.show_notification);
    }

    private void saveSettings() {
        // Validation
        String portStr = editPort.getText() != null ? editPort.getText().toString().trim() : "";
        String maxConnStr = editMaxConnections.getText() != null ? editMaxConnections.getText().toString().trim() : "";
        String username = editUsername.getText() != null ? editUsername.getText().toString().trim() : "";
        String password = editPassword.getText() != null ? editPassword.getText().toString().trim() : "";
        String rootDir = editRootDir.getText() != null ? editRootDir.getText().toString().trim() : "";

        if (portStr.isEmpty() || maxConnStr.isEmpty() || username.isEmpty() || password.isEmpty() || rootDir.isEmpty()) {
            Toast.makeText(this, "All text fields must be filled out!", Toast.LENGTH_SHORT).show();
            return;
        }

        int port;
        try {
            port = Integer.parseInt(portStr);
            if (port <= 1024 || port > 65535) {
                Toast.makeText(this, "Port must be between 1025 and 65535!", Toast.LENGTH_SHORT).show();
                return;
            }
        } catch (NumberFormatException e) {
            Toast.makeText(this, "Port must be a valid number!", Toast.LENGTH_SHORT).show();
            return;
        }

        int maxConnections;
        try {
            maxConnections = Integer.parseInt(maxConnStr);
            if (maxConnections <= 0) {
                Toast.makeText(this, "Max connections must be at least 1!", Toast.LENGTH_SHORT).show();
                return;
            }
        } catch (NumberFormatException e) {
            Toast.makeText(this, "Max connections must be a valid number!", Toast.LENGTH_SHORT).show();
            return;
        }

        // Validate root directory exists or is buildable
        File dir = new File(rootDir);
        if (!dir.exists()) {
            boolean created = dir.mkdirs();
            if (!created && !dir.isDirectory()) {
                Toast.makeText(this, "Warning: Root directory cannot be accessed or created!", Toast.LENGTH_SHORT).show();
            }
        }

        // Retrieve existing enabled status so we don't overwrite it
        SettingsManager.Settings currentSettings = SettingsManager.loadSettings();

        // Populate new values
        SettingsManager.Settings settings = new SettingsManager.Settings();
        settings.server_enabled = currentSettings.server_enabled; // Keep running state
        settings.port = port;
        settings.max_connections = maxConnections;
        settings.username = username;
        settings.password = password;
        settings.anonymous_login = switchAnonymous.isChecked();
        settings.root_directory = rootDir;
        settings.read_only = switchReadOnly.isChecked();
        settings.passive_mode = switchPassiveMode.isChecked();
        settings.auto_start = switchAutoStart.isChecked();
        settings.show_notification = switchShowNotification.isChecked();

        // Save
        SettingsManager.saveSettings(settings);
        Toast.makeText(this, "Settings saved successfully!", Toast.LENGTH_SHORT).show();

        if (FTPServerManager.getInstance().isRunning() && currentSettings.port != port) {
            Toast.makeText(this, "Restart server to apply port changes.", Toast.LENGTH_LONG).show();
        }

        finish();
    }

    private void resetSettingsToDefault() {
        SettingsManager.Settings defaults = new SettingsManager.Settings();
        SettingsManager.saveSettings(defaults);
        loadSettingsToUI();
        Toast.makeText(this, "Settings reset to defaults!", Toast.LENGTH_SHORT).show();
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            finish();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }
}
