package com.vypeensoft.ftpserver;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import com.google.android.material.button.MaterialButton;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class MainActivity extends AppCompatActivity {
    private static final int PERMISSION_REQUEST_CODE = 2002;
    private static final int MANAGE_STORAGE_REQUEST_CODE = 2003;

    private TextView textStatus;
    private TextView textIp;
    private TextView textPort;
    private TextView textFtpUrl;
    private TextView textClients;
    private MaterialButton btnStart;
    private MaterialButton btnStop;
    private MaterialButton btnSettings;

    private final FTPServerManager mServerManager = FTPServerManager.getInstance();
    private final Handler mUpdateHandler = new Handler(Looper.getMainLooper());
    private final Runnable mUpdateRunnable = new Runnable() {
        @Override
        public void run() {
            updateUI();
            mUpdateHandler.postDelayed(this, 1000);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Bind Views
        textStatus = findViewById(R.id.textStatus);
        textIp = findViewById(R.id.textIp);
        textPort = findViewById(R.id.textPort);
        textFtpUrl = findViewById(R.id.textFtpUrl);
        textClients = findViewById(R.id.textClients);
        btnStart = findViewById(R.id.btnStart);
        btnStop = findViewById(R.id.btnStop);
        btnSettings = findViewById(R.id.btnSettings);

        // Setup Listeners
        btnStart.setOnClickListener(v -> startServer());
        btnStop.setOnClickListener(v -> stopServer());
        btnSettings.setOnClickListener(v -> {
            Intent intent = new Intent(MainActivity.this, SettingsActivity.class);
            startActivity(intent);
        });

        // Initialize settings directory and check permissions
        checkAndRequestPermissions();
    }

    @Override
    protected void onResume() {
        super.onResume();
        mUpdateHandler.post(mUpdateRunnable);
    }

    @Override
    protected void onPause() {
        super.onPause();
        mUpdateHandler.removeCallbacks(mUpdateRunnable);
    }

    private void updateUI() {
        boolean isRunning = mServerManager.isRunning();
        SettingsManager.Settings settings = SettingsManager.loadSettings();
        
        if (isRunning) {
            textStatus.setText("Running");
            textStatus.setTextColor(ContextCompat.getColor(this, R.color.statusRunning));
            btnStart.setEnabled(false);
            btnStop.setEnabled(true);
        } else {
            textStatus.setText("Stopped");
            textStatus.setTextColor(ContextCompat.getColor(this, R.color.statusStopped));
            btnStart.setEnabled(true);
            btnStop.setEnabled(false);
        }

        String ip = NetworkUtils.getWifiIpAddress(this);
        textIp.setText(ip);
        textPort.setText(String.valueOf(settings.port));
        textFtpUrl.setText(NetworkUtils.generateFtpUrl(ip, settings.port));
        textClients.setText(String.valueOf(mServerManager.getConnectedClientsCount()));
    }

    private void startServer() {
        if (!hasStoragePermission()) {
            Toast.makeText(this, "Storage permission required to read/write settings & serve files", Toast.LENGTH_LONG).show();
            checkAndRequestPermissions();
            return;
        }

        SettingsManager.Settings settings = SettingsManager.loadSettings();
        if (!NetworkUtils.isPortAvailable(settings.port)) {
            Toast.makeText(this, "Port " + settings.port + " is already in use!", Toast.LENGTH_SHORT).show();
            return;
        }

        // Verify root directory exists
        File rootDir = new File(settings.root_directory);
        if (!rootDir.exists()) {
            boolean created = rootDir.mkdirs();
            if (!created && !rootDir.isDirectory()) {
                Toast.makeText(this, "FTP root directory does not exist and cannot be created!", Toast.LENGTH_LONG).show();
                return;
            }
        }

        Intent serviceIntent = new Intent(this, FTPServerService.class);
        serviceIntent.setAction(FTPServerService.ACTION_START);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent);
        } else {
            startService(serviceIntent);
        }
        
        // Brief delay to let service spin up before updating UI
        mUpdateHandler.postDelayed(this::updateUI, 300);
    }

    private void stopServer() {
        Intent serviceIntent = new Intent(this, FTPServerService.class);
        serviceIntent.setAction(FTPServerService.ACTION_STOP);
        startService(serviceIntent);
        
        // Brief delay to let service spin down before updating UI
        mUpdateHandler.postDelayed(this::updateUI, 300);
    }

    private boolean hasStoragePermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            return Environment.isExternalStorageManager();
        } else {
            return ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED &&
                   ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED;
        }
    }

    private void checkAndRequestPermissions() {
        List<String> permissionsNeeded = new ArrayList<>();
        
        // Notification permission (Android 13+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                permissionsNeeded.add(Manifest.permission.POST_NOTIFICATIONS);
            }
        }

        // Under Android 11, request WRITE_EXTERNAL_STORAGE and READ_EXTERNAL_STORAGE
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                permissionsNeeded.add(Manifest.permission.WRITE_EXTERNAL_STORAGE);
            }
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                permissionsNeeded.add(Manifest.permission.READ_EXTERNAL_STORAGE);
            }
        }

        if (!permissionsNeeded.isEmpty()) {
            ActivityCompat.requestPermissions(this, permissionsNeeded.toArray(new String[0]), PERMISSION_REQUEST_CODE);
        } else {
            // Check MANAGE_EXTERNAL_STORAGE for Android 11+
            checkAllFilesAccess();
        }
    }

    private void checkAllFilesAccess() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (!Environment.isExternalStorageManager()) {
                new AlertDialog.Builder(this)
                        .setTitle("All Files Access Needed")
                        .setMessage("This application requires All Files Access permission to read and write FTP configurations and serve files to connected clients from outside sandbox folders.")
                        .setPositiveButton("Grant", (dialog, which) -> {
                            try {
                                Intent intent = new Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION);
                                intent.addCategory("android.intent.category.DEFAULT");
                                intent.setData(Uri.parse(String.format("package:%s", getApplicationContext().getPackageName())));
                                startActivityForResult(intent, MANAGE_STORAGE_REQUEST_CODE);
                            } catch (Exception e) {
                                Intent intent = new Intent();
                                intent.setAction(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION);
                                startActivityForResult(intent, MANAGE_STORAGE_REQUEST_CODE);
                            }
                        })
                        .setNegativeButton("Cancel", (dialog, which) -> {
                            Toast.makeText(MainActivity.this, "Without storage access, settings cannot be read/written.", Toast.LENGTH_LONG).show();
                        })
                        .show();
            } else {
                // Ensure default settings directory and settings.json is created
                SettingsManager.loadSettings();
            }
        } else {
            // Pre-Android 11, just call loadSettings to initialize directories
            SettingsManager.loadSettings();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == PERMISSION_REQUEST_CODE) {
            boolean allGranted = true;
            for (int result : grantResults) {
                if (result != PackageManager.PERMISSION_GRANTED) {
                    allGranted = false;
                    break;
                }
            }
            if (allGranted) {
                checkAllFilesAccess();
            } else {
                Toast.makeText(this, "Standard permissions denied. App may not behave correctly.", Toast.LENGTH_SHORT).show();
            }
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == MANAGE_STORAGE_REQUEST_CODE) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                if (Environment.isExternalStorageManager()) {
                    Toast.makeText(this, "All Files Access Granted!", Toast.LENGTH_SHORT).show();
                    // Load settings to trigger folder and file creation
                    SettingsManager.loadSettings();
                } else {
                    Toast.makeText(this, "All Files Access Denied.", Toast.LENGTH_SHORT).show();
                }
            }
        }
    }
}
