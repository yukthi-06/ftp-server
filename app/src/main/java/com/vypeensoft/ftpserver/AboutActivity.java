package com.vypeensoft.ftpserver;

import android.os.Bundle;
import android.view.MenuItem;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;

public class AboutActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        // Apply Theme
        SettingsManager.Settings themeSettings = SettingsManager.loadSettings();
        androidx.appcompat.app.AppCompatDelegate.setDefaultNightMode(
            themeSettings.dark_theme ? androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_YES : androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_NO
        );

        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_about);

        String buildInfo = 
                "Timestamp: " + BuildConfig.BUILD_TIMESTAMP + "\n" +
                "Commit: " + BuildConfig.GIT_SHA + "\n" +
                "Full SHA: " + BuildConfig.GIT_SHA_FULL + "\n" +
                "Tag: " + (BuildConfig.GIT_TAG != null && !BuildConfig.GIT_TAG.isEmpty() ? BuildConfig.GIT_TAG : "N/A") + "\n\n" +
                getString(R.string.about_description);

        TextView tvBuildInfo = findViewById(R.id.tvBuildInfo);
        tvBuildInfo.setText(buildInfo);

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle(R.string.title_about);
        }
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            finish();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }
}
